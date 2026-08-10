# Spike — Caption extraction validation (Phase 2, step 1)

**Date:** 2026-08-10 · **Method:** endpoint-level validation from the dev machine
(Git Bash + curl, mobile UA where relevant). **No Android device/emulator was
attached** (`adb devices` empty), so "on-device" claims below are validated by
endpoint behavior, not by a Pixel run. Artifacts: `build/spike/*.html`.

## Findings (verified live)

| Source | Endpoint / mechanism | Result | Consequence for the app |
|---|---|---|---|
| **TikTok** | `https://www.tiktok.com/oembed?url=…` | ✅ **No auth. `title` = full caption incl. hashtags** (`"Scramble up ur name & I'll try to guess it😍❤️ #foryoupage…"`), `author_name` = handle. | Primary fetch: plain HTTP (OkHttp). Fast, zero-setup. |
| **Instagram** | `https://www.instagram.com/p/{code}/embed/captioned/` | ✅ **HTTP 200** (mobile UA, 591 KB) — but caption is **absent from raw HTML** (JS-injected; no `caption`/`text` JSON field, no known caption text). | **WebView rendering is mandatory** for IG/FB. Plain-HTTP fallback is INVALID — drop it. Optional logged-in WebView upgrade path stays. |
| **YouTube** | `https://www.youtube.com/oembed?url=…` | ✅ works, but keys = `author_name, html, thumbnail_*, title, …` — **no `description`**. | Description requires **Data API v3** (`videos.list?part=snippet`, 1 unit/call, API key). oEmbed insufficient. |
| **Cooking blogs** | schema.org Recipe JSON-LD | ✅ BBC Good Food serves a full `Recipe` node (`recipeIngredient` incl. `"3 tbsp olive oil","1 onion finely chopped",…`, `prepTime PT15M`, `recipeCategory`, nutrition) — but **inside an `application/json` hydration block nested under a `schema:` key**, not a standalone `application/ld+json` script. | Parser must scan **both** `application/ld+json` scripts **and** `application/json` blocks, walking nested JSON for `@type: Recipe` (BBC case). |
| Site blocking | allrecipes / seriouseats | ❌ 403 / 0-byte from datacenter IP (allrecipes), 403 (seriouseats). | Datacenter IPs are blocked; **on-device fetch (user ISP IP + WebView TLS) is the resilient path**. Verify blog fetch on a real device in the on-device test pass. |

## Decisions locked by the spike

1. **IG / FB reels → WebView-only caption extraction.** Load `/p/{code}/embed/captioned/`
   (FB: equivalent embed), wait for render, `evaluateJavascript` the caption DOM.
   Error states for blocks; optional "log in inside WebView" upgrade; final fallback =
   user pastes caption text (mockup's OCR flow also exists for screenshots).
2. **TikTok → oEmbed first** (no auth), WebView `__UNIVERSAL_DATA_FOR_REHYDRATION__`
   as fallback for login-walled videos.
3. **YouTube → Data API v3** with `YOUTUBE_API_KEY` (BuildConfig placeholder, set via
   `-PyouTubeApiKey=…` or DataStore). No watch-page scraping.
4. **Blogs → jsoup fetch (on-device context) → JSON-LD parser that scans
   `ld+json` + `application/json` blocks (nested walk)** → microdata fallback →
   readability text + Nano structuring.
5. Non-caption videos (no recipe text): out of scope for v1 (audio transcription
   would need LiteRT Gemma-3n — documented in FEASIBILITY.md §fallback).

## Risks still open (tracked, not blockers)
- IG embed HTML structure may change; WebView extractor isolates that fragility in
  one class (`InstagramWebViewExtractor`) with a regex-free DOM query.
- TikTok oEmbed `title` truncation is undocumented — cap at ~500 chars, keep
  WebView fallback.
- Real-device validation (ISP IP, WebView TLS, logged-in IG) still required — add a
  manual test checklist before Phase 2 sign-off.

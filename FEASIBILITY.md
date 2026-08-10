# Delizioso! — Feasibility Study

**Project:** Delizioso! — a local-first Android app to save, manage, and cook recipes
collected from social media, cooking sites, or entered manually.

**Target device:** Google Pixel 10 (Tensor G5, Android 16).
**Stack (approved):** Native Android — Kotlin + Jetpack Compose (Material 3).
On-device AI: **Gemini Nano via ML Kit GenAI Prompt API** + **ML Kit Text Recognition v2**.
Storage: on-device only (Room + DataStore); API keys embedded in the app.

---

## 1. Feature 1 — Import recipes from a link

User pastes a link (Instagram/Facebook reel, TikTok video, YouTube video, or a
cooking-site URL) and the app extracts the recipe. For social videos the recipe is
usually written in the video **caption/description**.

### Per-platform matrix (verified Feb 2026)

| Source | Feasible | Mechanism (in priority order) | Auth | Reliability / fragility |
|---|---|---|---|---|
| **Cooking blogs/sites** | ✅ Reliable | Parse **schema.org Recipe JSON-LD** (also Microdata/RDFa/OpenGraph) from the page; fallback: readability main-content + LLM structuring | none | Very high; de-facto standard for recipe sites. No maintained Kotlin lib — port logic (jsoup). Python `recipe-scrapers` is the reference implementation. |
| **YouTube** | ✅ Reliable | **Data API v3** `videos.list?part=snippet` → `description` (recipe usually in description). oEmbed returns title only — not enough | Free API key (embedded in app), 1 unit/call, default 10k units/day | High. Official & ToS-compliant. Watch-page scraping is dead (bot-gated) — don't rely on it. |
| **TikTok** | ✅ Best-effort (good) | 1. **oEmbed** `https://www.tiktok.com/oembed?url=…` → `title` = full caption incl. hashtags (verified live, no auth). 2. WebView → watch page → `<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">` → `itemInfo.itemStruct.desc`. 3. plain HTTP GET of watch page + same parse | none | oEmbed has been stable for years; undocumented rate limits; occasionally TikTok requires login for some videos. |
| **Instagram reels** | ✅ Possible (gray zone) | 1. **WebView** → `https://www.instagram.com/p/{code}/embed/captioned/` → wait for render → extract caption from DOM (verified: full caption rendered, no login). 2. plain HTTP GET of same embed URL (mobile UA) if server-rendered. 3. logged-out GraphQL POST (`PolarisLoggedOutDesktopWWWPostRootContentQuery`, `doc_id=27130156389949648`) if TLS impersonation feasible. 4. best-effort: user logged into IG inside WebView → `/api/v1/media/{id}/info/` | none for 1–3; cookies for 4 | Fragile — Meta blocks datacenter IPs (429) and changes endpoints without notice. Mobile-carrier IP + real browser TLS (WebView) resists best. |
| **Facebook reels** | ⚠️ Best-effort | Same embed-page family as IG; WebView embed + fallback chain; lower confidence | none | Fragile; graceful degradation expected. |

**Dead paths (do not build):** Instagram `?__a=1&__d=dis` (killed ~2021), `_sharedData`
(no longer embedded for anonymous visitors), `og:description` (stripped on post pages).
Meta's **official oEmbed/Graph API** deliberately does **not** expose the caption and
its ToS prohibit using oEmbed content beyond embedding — this is why extraction apps
use the unofficial endpoints above (user-initiated, personal use — same gray zone the
shipping apps the user tested operate in).

### How other tools do it (reference)
- `github.com/sleeper/recipe-extractor` — Python, uses **yt-dlp** (YouTube/IG/TikTok
  metadata incl. captions) + optional **audio transcription (OpenAI Whisper)** for
  videos without captions. We replicate the yt-dlp *no-login metadata paths* in Kotlin
  (WebView/oEmbed) — no Python, no Whisper.
- Paid scraper APIs (Apify `apify/instagram-scraper`, RapidAPI hosts) return full
  captions but cost per result — **not needed** for v1; keep as contingency.

### Import pipeline (v1)
```
link ──▶ platform detection (regex) ──▶ per-source fetcher ──▶ raw text/caption
                                                                    │
                                       ┌────────────────────────────┘
                                       ▼
                    Gemini Nano Prompt API: structure into JSON
                    (title, servings, ingredients[{qty,unit,name}], steps)
                                       │
                                       ▼
                    Edit-before-save preview screen ──▶ Room DB
```
- Blogs with JSON-LD skip the LLM for fields already structured (LLM only as fallback).
- `add_recipe` manual screen: form + photo + **"Scan Cookbook Page"** (ML Kit OCR) →
  same Nano structuring path.

---

## 2. Feature 2 — Local AI on the Pixel 10

### Verified facts
- **Pixel 10 ships with Gemini Nano v3 via AICore**; all Pixel 10 models are on the
  nano-v3 device list. Access via **ML Kit GenAI Prompt API**
  (`Generation.getClient(...)`, `checkStatus()` → `UNAVAILABLE/DOWNLOADABLE/AVAILABLE`,
  `download()`), model config with release stage + FAST/FULL preference.
- **Multimodal:** Prompt API accepts text **and image** prompts → can feed a recipe
  photo directly; for text-heavy pages Google recommends OCR-first for accuracy.
- **Structured output:** Prompt API supports JSON structured output (Alpha) — fits
  recipe field extraction.
- **OCR:** ML Kit Text Recognition v2 — free, offline, ~0.3 MB (Play-services) or
  ~4 MB bundled; Latin + CJK/Devanagari scripts; real-time on most devices.

### AI features (all on-device, offline)
1. **Structuring** parsed link text / OCR text into recipe fields (JSON).
2. **Macros per serving** — LLM heuristic estimate from ingredients (kcal, protein,
   fat, carbs) with a "estimates, not nutrition facts" disclaimer.
3. **Ingredient substitutions** — per-ingredient suggestions (e.g. egg → flax egg).

### Constraints (carry into implementation)
- **Quota:** AICore model is a **shared system resource** — per-app inference quota,
  `BUSY` errors under load (backoff + retry), **foreground-only** (background blocked),
  daily battery quota. Fine for occasional recipe parsing.
- **First-run:** model downloaded by AICore over Wi-Fi (takes minutes); first inference
  after load ~1 min. Check/download flow must be surfaced in UI.
- **Consent:** Gemini Nano requires runtime user consent (terms acceptance). Gate
  features behind opt-in; degrade gracefully to manual entry/OCR-only.
- **Macros are estimates** — label as such; no nutrition-db (USDA) call in v1.
- **Fragility:** IG/TikTok endpoints change without notice — per-platform fallback
  chains + clear error states; spike Phase 2 before committing.

### Fallback path (documented, not built in v1)
LiteRT-LM + Gemma 4 E2B (~2.6 GB, Apache-2.0, no quota) or LiteRT Gemma-3n for audio —
if Nano quota proves limiting or non-Nano devices are targeted. MediaPipe LLM
Inference API is maintenance-only; Ollama has no official Android app — both skipped.

---

## 3. Architecture decisions (approved)

| Decision | Choice | Rationale |
|---|---|---|
| Platform | Native Android (Kotlin + Compose M3) | Pixel AI stack (Nano, ML Kit) is Android-native; M3 tokens map 1:1 |
| AI engine | Gemini Nano (ML Kit GenAI) + ML Kit OCR | Ships with Pixel 10, ~0 app size, text+image, structured output |
| Storage | On-device: Room + DataStore | Privacy-friendly, no server, matches "local AI" ethos |
| Keys | YouTube Data API key embedded (BuildConfig) | Fine for personal use; key lives in app |
| Backend | None (v1) | Paid scraper APIs / server only if a future need appears |
| App name | **Delizioso!** (mockups' "ClayCook" was placeholder) | User requirement |

## 4. Top risks & mitigations
1. **IG/FB endpoint blocking** → WebView + device IP (best resistance), logged-in
   WebView upgrade path, user-paste/OCR fallback (mockup already has OCR flow).
2. **TikTok login walls / truncation** → oEmbed primary + watch-page parse fallback.
3. **Nano quota / BUSY** → backoff-retry with progress UI; FAST model preference.
4. **LLM structuring errors** → edit-before-save preview is the safety net.
5. **Macro accuracy** → heuristic estimates labeled as estimates.

## 5. Sources
- IG embed caption render + 429 behavior: `https://www.instagram.com/p/Chunk8-jurw/embed/captioned/`
- yt-dlp extractors: `yt_dlp/extractor/instagram.py`, `yt_dlp/extractor/tiktok.py`
- TikTok oEmbed: `https://www.tiktok.com/oembed?url=…` (verified live);
  docs `https://developers.tiktok.com/doc/embed-videos`
- Meta oEmbed prohibition: `https://developers.facebook.com/docs/instagram-platform/oembed`
- YouTube Data API quota: `https://developers.google.com/youtube/v3/getting-started`
- Python `recipe-scrapers`: `https://github.com/hhursev/recipe-scrapers`
- ML Kit GenAI (Prompt API, device list, quota): `https://developers.google.com/ml-kit/genai`
- ML Kit Text Recognition v2: `https://developers.google.com/ml-kit/vision/text-recognition/v2/android`
- `sleeper/recipe-extractor`: `https://github.com/sleeper/recipe-extractor`
- Apify Instagram Scraper: `https://apify.com/apify/instagram-scraper`

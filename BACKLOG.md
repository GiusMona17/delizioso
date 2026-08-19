# Delizioso! — Backlog

Everything known to be open, so it stops living in chat scrollback. One line of
what, one of why it matters, and — where a decision was already taken — the
reason, so it is not re-litigated later.

**Last reviewed:** 2026-08-20 (after Tier 1 stability fixes)

Specs for work that has been designed live in `docs/superpowers/specs/`.

---

## In progress

Nothing.

---

## Defects

**Machine translation of ingredient names is literal.**
"chicken breasts" becomes "seno di pollo" rather than "petto di pollo". This is
NMT quality, not a bug in our code. A fix means an exception table for
ingredients, the way `UnitNames` handles units — a much longer road, and only
worth it if the wrong wordings turn out to be common.

---

## Unverified

Built and unit-tested, but never exercised on the device.

- **Backup export and restore round trip.** The zip format is covered by tests
  (`LibraryBackupTest`), but the system file picker flow has not been driven
  end to end. The single most important thing to confirm by hand, since it is
  the only defence against losing the library.
- **Library sort menu** and **re-import-JSON dialog** in the edit screen. Both
  compile and are reasoned about; neither has been seen working.
- **Online search by ingredient**, and **the offline failure card**. The phone
  disconnected part-way through the device checks. Search by name was confirmed
  end to end — find, preview, save, and Refresh on the saved recipe — but the
  ingredient picker, the multi-ingredient intersection and the no-network path
  have only been read, never run. The ingredient picker is the one to try first:
  it is fed by the largest response the app requests, which is exactly where the
  main-thread body read above would have bitten.

---

## Improvements

**Map `nutrition` from JSON-LD.**
GialloZafferano and most recipe sites publish `calories`, `fatContent`,
`carbohydrateContent` and more in their JSON-LD. `RecipeJsonLdParser` ignores all
of it and the app falls back to its own lookup table. Where the site states the
figures they are better than our estimate, and the "from N of M ingredients"
caveat could be dropped for those recipes.

**Empty-search card with an action.**
When a library search finds nothing, offer to import from a link or search
online, rather than only saying there are no matches. Pattern borrowed from the
sibling project.

**Small things the online-search reviews left on the table.**
`openResult` captures whatever state it finds, so a second tile tap inside one
frame can restore `Loading` on return from the preview. Retry re-runs with
whatever is currently in the name field rather than the query that failed. The
search screen's input column does not scroll, so around six ingredient chips
start squeezing the results grid. None is worth a commit on its own; fix them
next time that file is open.

**`updatedAt` doubles as "last touched".**
Refreshing a recipe from its source bumps `updatedAt`, so it jumps to the top
under the "Last updated" sort. Correct by the letter, mildly surprising in
practice. Only worth addressing if it becomes annoying.

---

## Decisions taken (do not revisit without new information)

**Extraction stays deterministic; the model only handles language.**
Measured across the session: the heading parser beat Gemini Nano on captions, and
code beats a 1B model at arithmetic. The LLM earns its place in the chat, and
ML Kit — a purpose-built translator — handles translation.

**Macros are calculated, not generated.**
A small model produces confident, unverifiable calorie counts. The lookup table
gives numbers that can be traced and corrected, and the app says how many
ingredients it recognised.

**Chaquopy + `recipe-scrapers` — rejected.**
Proposed as a precision layer for GialloZafferano. Checked directly: that site
publishes complete JSON-LD which the existing parser already reads, so the layer
adds nothing there. It also optimises the case that already works — recipe
websites — while our failures are social captions, which it does not touch. The
dependency tree (`extruct` → `lxml`, `rdflib`) needs C extensions inside
Chaquopy, plus a CPython runtime per ABI in the APK.

**Edamam — rejected.** Returns links, not instructions, so recipes could not be
saved. That is the entire point of the app.

**Spoonacular — open, and yours to decide.**
The only provider with true multi-ingredient search, but a ~150 point daily quota
and terms that limit how long results may be stored — which sits badly with a
local-first library kept forever. Not needed unless TheMealDB's ~300 recipes
prove too thin.

**No automatic translation of imported recipes.**
Translation stays a deliberate button press, as it is everywhere else in the app.

**No rate limiting.** Personal use, a handful of requests a day. Would be
theatre.

---

## Notes

- **TheMealDB test key `1`** is public and free for the core endpoints.
  Multi-ingredient filtering and "latest meals" sit behind a one-off paid tier we
  do not need — the intersection is done client-side instead.
- **The `macros*` columns were dropped** from `recipes` in migration 3→4. Macros
  are derived on display so they can never be stale.
- **Gemma is chat-only.** Import, conversion and translation never touch it.

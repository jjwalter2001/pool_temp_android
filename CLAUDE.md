# pool_temp_android — Claude Instructions

See the global `~/.claude/CLAUDE.md` for machine-wide conventions. This is the
native Android (Kotlin + Jetpack Compose) client for the `pool_temp` Flask
backend (sibling project). specifics below.

## Architecture

- Single-backend client: the app talks **only** to the `pool_temp` Flask API,
  behind **Cloudflare Access** (service-token headers injected globally by the
  OkHttp interceptor in `data/ApiClient.kt`) plus an optional Flask bearer
  token. It never talks to Home Assistant / Tuya / etc. directly — new device
  features are added as backend endpoints and proxied. Keep secrets server-side.
- Layers: `data/` (Retrofit `ApiService`, `@Serializable` `Models` mirroring the
  Flask JSON, `Settings` DataStore) → `ui/dashboard/DashboardViewModel` (a
  parallel phase-A fetch → `PhaseA`, then a background phase-B history fetch) →
  `ui/dashboard/DashboardScreen` (Compose cards). Adding a control = model +
  ApiService endpoint + VM state/action + a card + a confirm `AlertDialog`
  (see the HeaterCard / LightningCard pair for the pattern).
- Models add new backend fields as **optional with defaults** so older builds
  don't break on unknown JSON (`ignoreUnknownKeys = true`).

## Build

- **`JAVA_HOME` must point at Android Studio's bundled JBR** — there's no
  standalone JDK on PATH. In the PowerShell tool:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then
  `.\gradlew.bat :app:compileDebugKotlin`. (Gradle exits 9009 "JAVA_HOME is not
  set" otherwise.)
- Material **icons-extended** is a dependency, so `Icons.Outlined.*` beyond the
  core set (e.g. `Thunderstorm`) resolve fine.
- `keystore.properties` / `firebase.properties` / `local.properties` are
  gitignored; `*.example` templates are committed. Release signing + Firebase
  App Distribution are wired in `app/build.gradle.kts`.

## Shipping a release (Firebase App Distribution)

Firebase project: **`pool-temp-a1973`** (owner: `jjwalter2001@gmail.com`; note
`appId` project number `202773767254`). The app applies the `appdistribution`
plugin but **not** `google-services` — so `google-services.json` is unused by
the build; keep it out of commits (it's not gitignored, so don't `git add` it).

- **Don't try to create/download a service-account JSON key** — the project's
  `iam.disableServiceAccountKeyCreation` org policy is **enforced**, so key
  creation 403s. (Don't disable the policy to work around it.)
- **Autonomous upload uses the gcloud owner token + the App Distribution REST
  API** — no stored secret, no interactive login, respects the policy:
  1. `$env:JAVA_HOME=…\Android Studio\jbr; .\gradlew.bat :app:assembleRelease`
     → signed `app/build/outputs/apk/release/app-release.apk`.
  2. `$tok = gcloud auth print-access-token` (owner, `cloud-platform` scope).
  3. **Every** call needs header `X-Goog-User-Project: pool-temp-a1973` — user
     creds require a quota project or the operations endpoint 403s (the
     `releases:upload` POST doesn't enforce it, but polling the returned
     operation does — this bit me).
  4. Flow: POST `…/upload/v1/projects/202773767254/apps/<appId>/releases:upload`
     (raw APK body, `X-Goog-Upload-Protocol: raw`) → poll `v1/{operation.name}`
     until `done` → PATCH `v1/{release}?updateMask=release_notes.text` → POST
     `v1/{release}:distribute` with `groupAliases`/`testerEmails` from
     `firebase.properties`. App Distribution **dedupes by APK hash**
     (`RELEASE_UNMODIFIED` on a re-upload of the same binary), so re-running is
     safe.
- `firebase.properties` has `appId`/`testers` set, `groups` blank — so releases
  go to the individual `testers` list. Add a `groups` alias once that grows.
- **The project's tester roster and `firebase.properties` drift apart.**
  `GET v1/projects/202773767254/testers` returns everyone ever added (console or
  API); `:distribute` only reaches the emails in `firebase.properties`. As of
  2026-08-17 the roster has `bryan.walter1012@gmail.com` but the file does not —
  so they silently get no new builds. Adding a tester = edit the file *and* ship
  a release; the file edit alone sends nothing.
- Rebuilding an unchanged tree still yields a **new** APK hash (signing isn't
  reproducible here), so a re-ship gets `RELEASE_CREATED`, not
  `RELEASE_UNMODIFIED` — fine, but it means you don't need a `versionCode` bump
  just to re-distribute to a newly added tester.
  `FIREBASE_TOKEN` / `serviceCredentialsFile` (README's older paths) are **not**
  used; the gcloud-token REST method above supersedes them.

## Water chemistry

`ui/chemistry/` (ViewModel + Screen) plus the chemistry models in `data/`.
Talks to `/api/chem/*` on the same backend; no new auth, the existing
interceptor covers it.

- The **entry form lives in the ViewModel**, not in the composable, so a
  rotation or a brief backgrounding at poolside does not discard a
  half-entered test.
- **Censored readings are a checkbox with no value.** The Taylor K-1005 cannot
  read chlorine above 5 ppm, pH below 7, or CYA below 30. Ticking a box clears
  and disables its field; the server rejects a row carrying both, so the two
  must stay mutually exclusive here too.
- **Entry is dropdowns, not typing.** Values come from fixed scales so a
  reading the kit cannot produce cannot be entered at all. Salt is the one
  exception and stays typed: the cell display gives a precise value like 3250
  and a dropdown would round away real precision.
- **The scale options are built from `pool_config`** (`ph_scale_min/max/step`
  and friends) in `ChemistryViewModel.buildScales`, so the app, the web page,
  and the engine's off-scale handling all read the same numbers. Never
  hardcode a bound here.
- Steps are counted by index, not accumulated. Adding 0.1 twelve times lands
  on 7.999999999999999 and the option stops matching what the server stores.
- **Nothing is preselected.** A blank field means "not tested"; defaulting one
  would quietly invent a reading the user never took.
- **The entry form's shape comes from the server.** `/api/chem/config` returns
  field descriptors (label, scale bounds, and which columns a value or an
  off-scale marker writes to). Adding a parameter is a server change, not an
  edit in three places. Never hardcode a bound or a flag name here.
- **`data/ChemEntry.kt` is pure and unit tested** (`src/test/kotlin`, run with
  `gradlew :app:testDebugUnitTest`). It is the code that decides which column a
  selection lands in, where a mistake writes plausible-looking but wrong
  chemistry rather than failing loudly. Keep new mapping logic there, not in
  the ViewModel or the composable.
- **Off-scale readings are entries in the dropdown, not checkboxes.** One
  control per parameter, so "Below 6.8" cannot contradict a value the way a
  checkbox beside a filled field could. `UNDER`/`OVER` are UI-only sentinels
  that map onto the flag columns at save time; a field is one string that is
  blank, a number, or a sentinel.
- **A dose date is sent as `on_date` (YYYY-MM-DD), never as a computed
  timestamp.** The server resolves it to midday in the pool's timezone.
  Computing midday on the device used the *device's* zone, which filed a dose
  five hours out and landed it before the very reading it was recorded
  against. Only "today" sends a real timestamp.
- Amounts render from the action's `display` field ("1 gallon (128 oz)"), which
  the server produces. Do not reformat amounts here.
- **Recording a dose asks when it actually went in.** The timestamp is what
  calibration pairs are matched on, in hours, so "now" is wrong every time the
  pour and the tap happen at different points in the day. Today keeps the real
  clock time; an earlier date resolves to midday, matching the importer's
  convention for a date-only row. Future dates are unselectable in the picker
  and rejected by the server.
- Chemistry is fetched in the dashboard's phase-A block wrapped in
  `runCatching`, like lightning: an older backend 404s and the card just does
  not render.
- Recording a dose gets a confirm dialog because it writes to the calibration
  training set, not because it is dangerous. Only confirm what actually went
  in the water.
- **"Already added" is server state, never local.** An action carries `done`
  and `done_ts` when its product was recorded against that same reading. The
  app previously tracked this in a `doseLogged` set that vanished on refresh,
  so the recommendation to add acid came back after you had added it. Do not
  reintroduce local "recorded" state.
- The dashboard card counts `pendingCount`, not `actions.size`, or it keeps
  saying "1 thing to add" after it has been added.
- **Skipping a recommendation** posts the action's stable `key`, never its
  position: the engine regenerates actions on every fetch and `order` shifts.
  A skipped action stays on screen with an Undo, so the decision is
  reversible.
- **Every endpoint that returns a recommendation returns the reading too.**
  The VM swaps its whole `latest` for the response, so a bare recommendation
  makes the status card read "No tests logged yet". That was a real bug; the
  server now shares one `_full_body()` across all three endpoints.
- **The history screen is read only on purpose.** Correcting a row means
  seeing its neighbours and retyping carefully, which is a desk job; the web
  page owns editing and deleting. It also means no destructive gesture can
  fire from a wet hand at the pool.
- History has **two tabs**, Tests and Additions, sharing one ViewModel and one
  fetch. Switching tabs must not trigger a network call; both lists load in
  parallel up front because they are small.
- Each grid gets **its own `ScrollState`**. Sharing one between the two would
  carry a sideways scroll from a nine-column grid into a four-column one and
  leave it scrolled past its end.
- The Additions grid's REC column compares what went in against what was
  recommended. That difference is the calibration signal, so it is surfaced
  rather than left to arithmetic in the reader's head.
- History is a **grid, not cards**. Its column order deliberately matches the
  Pool Measurements spreadsheet the data was imported from (pH, TC, FC, TA,
  CYA, salt, CH), so scanning it feels like the sheet rather than like a
  different tool showing the same numbers.
- **The date column is frozen and everything else shares one `ScrollState`**
  with the header. That shared state is what keeps the columns aligned while
  the grid scrolls sideways; give the header and every row the same instance
  or the alignment silently drifts.
- Cells use `fontFeatureSettings = "tnum"` and fixed `Dp` column widths.
  Tabular figures are why digits line up down a column; proportional numerals
  would ruin the effect at a glance.
- Values are **neutral except where the reading was censored**. They are
  deliberately not colored in-range or out-of-range: the targets have changed
  over three years of history, so scoring a 2024 reading against today's bands
  would be a fabrication.
- pH always renders to one decimal in history (`String.format("%.1f")`), not
  via a trailing-zero-stripping helper. "pH 8" and "pH 8.0" read as different
  kinds of measurement.
- Zebra striping uses `itemsIndexed`, not `indexOf`. `indexOf` is O(n) per row
  and returns the wrong index whenever two readings compare equal.

## Versioning

Source of truth is `app/build.gradle.kts` (`versionName` + `versionCode`). Bump
both per the global versioned-commits convention (feature → minor, bump
`versionCode` by 1) and tag `v<versionName>`.

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

## Versioning

Source of truth is `app/build.gradle.kts` (`versionName` + `versionCode`). Bump
both per the global versioned-commits convention (feature → minor, bump
`versionCode` by 1) and tag `v<versionName>`.

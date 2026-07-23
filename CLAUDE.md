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

## Versioning

Source of truth is `app/build.gradle.kts` (`versionName` + `versionCode`). Bump
both per the global versioned-commits convention (feature → minor, bump
`versionCode` by 1) and tag `v<versionName>`.

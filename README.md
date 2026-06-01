# pool_temp_android

Native Android companion app for the [pool_temp](https://github.com/jjwalter2001/pool_temp) Flask service. Reads live pool/weather/heater data over the existing `/api/*` endpoints, lets family members turn the pool heater on/off with attribution, and (from Phase 5 onward) keeps a persistent notification on the lock screen showing the current pool temperature.

## Status

- ✅ Phase 1 — backend hardening (shipped in pool_temp v1.4.0)
- ✅ Phase 2 — Android project scaffold
- ✅ Phase 3 — Dashboard + heater control (with confirm dialog)
- ✅ Phase 4 — 24h temp sparkline on each sensor card
- ✅ Phase 5 — Persistent notification via WorkManager (30 min cadence)
- ⏳ Phase 6 — Settings polish + signed APK for family sideloading

## Architecture

- Kotlin 2.1, Jetpack Compose, Material 3
- min SDK 26 / target SDK 35
- Retrofit + OkHttp + Kotlinx Serialization (`ApiService` mirrors Flask's JSON shapes)
- DataStore Preferences for config (`Settings`)
- WorkManager for the periodic notification refresh (Phase 5)
- Auth headers injected by an OkHttp interceptor:
  - `CF-Access-Client-Id` + `CF-Access-Client-Secret` (Cloudflare Access service token, required)
  - `Authorization: Bearer <API_TOKEN>` (Flask defense-in-depth, optional)
  - `X-User: <name>` (per-call on heater toggles, set by the endpoint)

## Build

Open the folder in Android Studio (Ladybug or newer). Android Studio will set up the Gradle wrapper on first sync. To build and install on a device:

```
./gradlew :app:installDebug
```

(`gradlew`/`gradlew.bat` aren't checked in yet — they appear after the first sync. Alternatively, run `gradle wrapper` once.)

## First-launch config

The Onboarding screen captures everything needed to reach your instance:

| Field | Source |
| --- | --- |
| Base URL | Your pool_temp public hostname (e.g. `https://pool.example.com`) |
| CF Access Client ID | Cloudflare Zero Trust → Access → Service Tokens |
| CF Access Client Secret | Same |
| Flask API Token | The `API_TOKEN` env var on the pool_temp container (leave blank if unset) |
| Your name | What appears in the heater event log when you flip it on/off |

Values are stored in app-private DataStore; uninstalling the app wipes them.

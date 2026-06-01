# pool_temp_android

Native Android companion app for the [pool_temp](https://github.com/jjwalter2001/pool_temp) Flask service. Reads live pool/weather/heater data over the existing `/api/*` endpoints, lets family members turn the pool heater on/off with attribution, and (from Phase 5 onward) keeps a persistent notification on the lock screen showing the current pool temperature.

## Status

- ✅ Phase 1 — backend hardening (shipped in pool_temp v1.4.0)
- ✅ Phase 2 — Android project scaffold
- ✅ Phase 3 — Dashboard + heater control (with confirm dialog)
- ✅ Phase 4 — 24h temp sparkline on each sensor card
- ✅ Phase 5 — Persistent notification via WorkManager (30 min cadence)
- ✅ Phase 6 — Editable settings, notification toggle, release-signing config

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

## Release builds for family sideloading

1. Generate a release keystore (once — keep it safe; you can't sign updates without it):

   ```
   keytool -genkeypair -v -keystore pool_temp.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias pooltemp
   ```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in the real values. Both files live at the project root; `keystore.properties` and `*.jks` are gitignored.

3. Build a signed release APK:

   ```
   ./gradlew :app:assembleRelease
   ```

   The signed APK lands in `app/build/outputs/apk/release/`.

4. Sideload to each phone (USB transfer, Bluetooth, Drive, etc.) and tap to install. Recipients need to allow "Install unknown apps" for the source they use.

5. For future updates, bump `versionCode` (and optionally `versionName`) in `app/build.gradle.kts`, rebuild, and reinstall on each phone. The keystore must match every time — Android refuses to upgrade an app whose signature changed.

## Pushing updates to the family via Firebase App Distribution

Sideloading is fine for the first install but tedious for updates. Firebase App Distribution lets you push new builds to family members from a single gradle command; recipients install via Firebase's "App Tester" helper app and get a notification on every release. Free, no Play Console required.

**One-time setup:**

1. Create a Firebase project at https://console.firebase.google.com — pick "Add app → Android", use the package name `com.jjwalter.pooltemp`. You don't need `google-services.json` for App Distribution; the only field that matters is the App ID (looks like `1:1234567890:android:abcdef…`).
2. In the Firebase console, open *App Distribution → Testers & groups* and add the family emails (or create a group like `family`).
3. Install the Firebase CLI on this machine (one-time):

   ```
   npm install -g firebase-tools
   firebase login:ci
   ```

   The second command opens a browser to authenticate, then prints a refresh token. Stash it in your shell profile or set it before each release:

   ```
   $env:FIREBASE_TOKEN = "1//0abc...your-token..."
   ```

4. Copy `firebase.properties.example` to `firebase.properties` (gitignored) and fill in `appId` + `testers`. Optionally use `groups` instead once the email list grows.

**Each release:**

```
./gradlew :app:assembleRelease :app:appDistributionUploadRelease
```

The signed APK is uploaded to Firebase and every tester gets an email + push notification. The first time they tap the invite, they install the Firebase App Tester app once; after that, each new build is a single tap to update.

Release notes default to `Pool Temp <versionName> (build <versionCode>)`. Override per release with:

```
./gradlew :app:appDistributionUploadRelease -PreleaseNotes="Fixes heater button on Pixel 6"
```

**Updating the family without recompiling:** You don't need to. Just bump `versionCode` in `app/build.gradle.kts` and run the gradle command above — the previously installed app updates in place because it's signed with the same keystore.

## Settings (after first launch)

The in-app Settings screen lets you:

- **Edit connection** — re-runs the onboarding form prefilled with your saved values.
- **Toggle the persistent notification** — when off, the periodic worker is cancelled and the existing notification is cleared. When on, the worker re-schedules immediately.
- **Forget configuration** — wipes DataStore and cancels the worker; the app drops back to onboarding.

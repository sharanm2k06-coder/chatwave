# ChatWave Android (Phase 1)

Kotlin + Jetpack Compose (Material 3) client for `chatwave-backend`. App ID `com.chatwave.app`, minSdk 26.

**Not end-to-end encrypted** — TLS in transit, server-readable storage.

## Run
1. Open this folder in Android Studio (Ladybug or newer, JDK 17+). Let it sync; if `gradlew` is missing run `gradle wrapper --gradle-version 8.9` once (the wrapper JAR is not included).
2. Start the backend (see its README). Debug builds talk to `http://10.0.2.2:3000/` (emulator -> host). For a physical phone, change `BASE_URL` in `app/build.gradle.kts` (debug) to your PC's LAN IP and add it to `network_security_config.xml`.
3. Run on two emulators/devices, register two users, search one from the other, chat.
4. Release builds use `CHATWAVE_PROD_URL` (gradle.properties or `-P`), must be HTTPS.

## Render and phone installation
1. Deploy the backend Blueprint from `../chatwave-backend/render.yaml` and copy the resulting HTTPS service URL.
2. In `gradle.properties`, set `CHATWAVE_PROD_URL=https://your-service.onrender.com/` with a trailing slash.
3. In Android Studio, use **Build > Generate App Bundles or APKs > Generate APKs** and choose the `release` variant after configuring signing.
4. For a quick private install, choose the `debug` variant and install `app-debug.apk` on an Android 8.0+ phone. Enable installation from the file manager when Android asks.
5. For a distributable release, create a keystore in Android Studio's signed APK wizard and keep it outside source control. Never share the keystore or passwords.

The backend runs migrations automatically on startup. Confirm `/health` is healthy before opening the Android app.

## Phase 1 scope
Register/login/logout, Keystore-encrypted tokens with automatic refresh, user search, direct chat, realtime send with server ack, retry of failed sends (idempotent via clientMessageId), sent/delivered/read, typing, history pagination, chat list, loading/error/offline/empty states. Room cache, groups, media, FCM, profile/settings are Phase 2.

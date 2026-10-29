# ChatWave

ChatWave is a Kotlin/Jetpack Compose Android client with a Node.js, Express,
PostgreSQL, and Socket.IO backend.

## Project layout

- `chatwave-android/` — Android application
- `chatwave-backend/` — REST and realtime backend

## Deploy the backend

The backend includes a Render Blueprint in
`chatwave-backend/render.yaml`. Push this repository to GitHub, create a
Render Blueprint from the repository, and apply the Blueprint. It provisions
the web service and PostgreSQL database.

After deployment, verify:

```text
https://YOUR-SERVICE.onrender.com/health
```

## Configure and install Android

Set `CHATWAVE_PROD_URL` in `chatwave-android/gradle.properties` to the HTTPS
URL produced by Render, including the trailing slash. Open
`chatwave-android/` in Android Studio with JDK 17 or newer, sync the project,
and build the debug APK for a private phone installation or a signed release
APK for distribution.

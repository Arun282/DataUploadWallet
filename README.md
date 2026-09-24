# Data Upload Wallet

Android + Node.js prototype for uploading files to a server.

## Important
This project does **not** convert unused SIM/mobile-data quota into transferable internet data. It stores uploaded files on a server and lets the app access them.

## Render deployment
The repository includes `render.yaml`. Create a Render Web Service from this GitHub repository. It uses:
- Root directory: `server`
- Build command: `npm install`
- Start command: `npm start`
- Health check: `/api/health`

After deployment, Render provides a public `onrender.com` URL.

**Storage note:** this prototype stores uploads on the server filesystem. For production/durable storage, use object storage or a persistent disk.

## Android
The Android app is in `android/`. For the emulator it currently uses `http://10.0.2.2:3000`.

After Render deployment, replace `BASE_URL` in `android/app/src/main/java/com/example/datawallet/MainActivity.kt` with your Render HTTPS URL, then build the APK.

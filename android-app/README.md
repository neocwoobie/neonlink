# NeonLink Android Share Helper

This is an experimental native Android helper for this NeonLink fork. It is a small Android app that appears in the Android share menu and sends shared links to your self-hosted NeonLink server through the existing API.

## Features

- Registers as an Android `ACTION_SEND` target for text shares.
- Saves a NeonLink server URL on the phone.
- Logs in with `/api/users/login` and stores the returned `SSID` session cookie.
- Loads existing NeonLink groups from `/api/categories`.
- Sends shared links to `/api/share`.
- Supports saving into an existing group, creating a new group, and adding comma-separated tags.
- Supports HTTPS and cleartext HTTP for private LAN or Tailscale deployments.

## Build

Open the `android-app` folder in Android Studio and let Gradle sync.

Recommended flow:

1. Install Android Studio.
2. Open this folder: `android-app`.
3. Let Android Studio download the Android Gradle Plugin and SDK components it asks for.
4. Choose `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. Install the generated debug APK on your Android phone.

GitHub Actions also builds a debug APK artifact when changes are pushed to the `android-pwa-share` branch. Open the `Build Android app` workflow run and download `neonlink-share-debug-apk`.

If you already have Gradle and Android SDK installed, you can also run:

```sh
cd android-app
gradle :app:assembleDebug
```

The APK will be generated under:

```text
android-app/app/build/outputs/apk/debug/
```

## Phone Setup

1. Install the APK on the Android phone.
2. Open the NeonLink app.
3. Enter your NeonLink URL, for example `https://tomato.tail16e0f.ts.net`.
4. Tap `Connect`.
5. Enter your NeonLink username and password, then tap `Login`.
6. Share a link from Chrome or another Android app.
7. Choose `NeonLink Share` in the Android share menu.
8. Confirm the URL, choose an existing group or enter a new group name, then tap `Save to NeonLink`.

## Troubleshooting

If you see an error like:

```text
Unable to resolve host "tomato.tail16e0f.ts.net"
```

the app cannot resolve the Tailscale MagicDNS name. Check these first:

1. Open the Android Tailscale app and make sure it says `Connected`.
2. In the Android Tailscale app, make sure DNS/MagicDNS is enabled for the phone.
3. Try opening the same NeonLink URL in Android Chrome.
4. If MagicDNS still fails in the native app, use the device Tailscale IP directly instead of the MagicDNS name.

For example, in the Android Tailscale app, open your Unraid device and copy its `100.x.x.x` address. Then enter this in NeonLink Share:

```text
http://100.x.x.x:3333
```

The native Android app allows private HTTP URLs, so this can work even when the PWA needs HTTPS.

## Notes

- This app requires the forked NeonLink server that includes `POST /api/share`.
- The password is only sent during login and is not stored by the app.
- The app stores the server URL and session cookie in Android `SharedPreferences`.
- Cleartext HTTP is allowed so private NAS URLs like `http://host:3333` can work, but HTTPS is still preferred when available.
- This first version is intentionally small and native. It does not bundle the web app or require PWA installation.

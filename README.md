# Shorts Blocker (Android)

Blocks YouTube Shorts: the moment the Shorts player opens in the YouTube app, the app presses Back for you. Normal videos, search and subscriptions keep working.

- No account, no internet permission: nothing leaves the phone.
- Listens to the YouTube app only.
- 30-second wait before you can pause (15 min / 1 hour) or turn it off.
- Shows how many Shorts were blocked today.

## Get the APK
Every push to `main` builds the app on GitHub Actions. Open the **Releases** section of this repo and download `ShortsBlocker.apk`.

## Install on the phone
1. Open `ShortsBlocker.apk` on the phone. If asked, allow "Install unknown apps" for your browser/Files app.
2. Open Shorts Blocker → **Turn on Shorts Blocker** → I agree → find Shorts Blocker → turn it ON.
3. Android 13+: if the switch is greyed out ("Restricted setting"), go to Settings → Apps → Shorts Blocker → ⋮ (top corner) → **Allow restricted settings**, then repeat step 2.
4. Xiaomi / Oppo / Vivo / Realme / Samsung: tap **Keep it running** in the app and set battery to "Unrestricted" / "No restrictions" so the phone doesn't stop the blocker.

## If YouTube changes and blocking stops
Edit `app/src/main/res/values/detection.xml` (the list of Shorts screen IDs) and push. A new APK builds automatically.

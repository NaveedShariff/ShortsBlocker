# Shorts Blocker — handoff for Claude Code

## What this is
An Android app that blocks YouTube Shorts. When the YouTube app opens the Shorts player, the app presses Back automatically (and goes to the home screen if Back doesn't work, e.g. for shared Shorts links). Normal YouTube videos, search and subscriptions keep working.

Owner: Shariff. He's not a developer, so use plain language, give simple step-by-step instructions, and give commands for the Mac VS Code terminal.

The full product spec (PRD) is in a Claude Doc: https://claude.ai/code/artifact/e5a1f8b8-818d-4fcc-b515-c2d5f8d8fa29
This folder implements the **MVP (v1.0)** from that PRD.

## What's already built (all code is written)
- `app/src/main/java/com/shariff/shortsblocker/ShortsBlockerService.java`: Accessibility Service. It listens ONLY to `com.google.android.youtube`, detects the Shorts player and presses Back (or Home after 2 failed Backs). It counts blocks.
- `MainActivity.java`: home screen with the status ("Protecting you" / Paused / Off), blocks today, estimated time saved, the setup card, a disclosure dialog before Accessibility, a battery-settings button, a "Test it" button that opens YouTube, and a toggle for the "Shorts blocked" toast.
- Friction to disable: a 30-second wait, then the user picks Pause 15 min / Pause 1 hour / Turn off.
- `Prefs.java`: on-device storage (SharedPreferences). Stores only on/off, the pause time and the daily block count.
- `app/src/main/res/values/detection.xml`: **detection rules** (YouTube view IDs for the Shorts player, plus the "Shorts" bottom-tab check). When YouTube changes its layout, edit this file.
- No INTERNET permission on purpose (privacy promise: nothing leaves the phone).
- Plain Java + Android framework only, with **no external libraries** (no AndroidX, no Compose). Min SDK 26, target/compile SDK 35, AGP 8.7.3, Gradle 8.11.1, JDK 17.
- Signed with a fixed key, `app/shortsblocker.keystore` (password `shortsblocker`), so new builds install over old ones. Fine for side-loading; make a private key before any Play Store release.
- `.github/workflows/build.yml`: GitHub Actions builds the release APK on every push to `main` and publishes it as a GitHub Release (`ShortsBlocker.apk`).

## IMPORTANT: never built or tested yet
The code was written in an environment that couldn't download the Android SDK, so it has **never been compiled**. Expect small build errors on the first run and fix them.

## What to complete (in order)
0. **Put the build workflow in place first.** The cloud build file is at `MOVE-ME-build.yml` in this folder (it couldn't be saved straight into `.github`). Run:
   ```
   mkdir -p .github/workflows && mv MOVE-ME-build.yml .github/workflows/build.yml
   ```
1. **Push to GitHub.** The repo is empty: https://github.com/NaveedShariff/ShortsBlocker (GitHub account: NaveedShariff)
   ```
   git init -b main
   git add -A
   git commit -m "Shorts Blocker v1.0"
   git remote add origin https://github.com/NaveedShariff/ShortsBlocker.git
   git push -u origin main
   ```
   If git asks for a login, use `gh auth login` and sign in as NaveedShariff.
2. **Get the build green.** Watch it with `gh run watch`. If it fails, read the log (`gh run view --log-failed`), fix the code, push again, and repeat. Only fix build errors; don't change features.
3. **Give Shariff the APK link:** https://github.com/NaveedShariff/ShortsBlocker/releases (the newest release has `ShortsBlocker.apk`).
4. **Help him install and test on his Android phone:**
   - Open the APK and allow "Install unknown apps" if asked.
   - In the app: Turn on Shorts Blocker → I agree → find Shorts Blocker in Accessibility → ON.
   - Android 13+ "Restricted setting": Settings → Apps → Shorts Blocker → ⋮ → Allow restricted settings, then try again.
   - Xiaomi/Vivo/Oppo/Realme/Samsung: set the app's battery to Unrestricted.
   - Test: Shorts tab, a Short in the home feed, a Short from search, a shared Shorts link. All should bounce back. A normal video must NOT be blocked.
5. **If Shorts get through:** the YouTube view IDs probably changed. Connect the phone with USB debugging and run `adb shell uiautomator dump` while a Short is open to find the current IDs, then update `detection.xml`.

## Not in v1.0 (later, per the PRD)
Schedules (v1.1), daily Shorts allowance (v1.1), Instagram/Facebook Reels (v1.2), streaks and weekly report (v1.2), PIN lock (v1.3), browser Shorts (v2.0). Don't add these unless Shariff asks.

## Open questions for Shariff
- Daily allowance in v1, or keep it a strict block?
- Which phone brand is he testing on? This affects the battery-setup steps.
- Final app name and icon (the current icon is an original red shield with a slash; don't use YouTube's logo).

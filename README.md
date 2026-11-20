# ليميت كلود · Claude Limit

Android app and home-screen widgets that show how much of your Claude Pro/Max limit is left.

- **5 hours**: what is left and when it resets.
- **Weekly**: what is left and when it resets.
- **Weekly plan**: how much you should have used by today if you spread the week evenly, against how much you really used, and how much you can use per day until the reset.

Every number has its own widget, so you add only what you want. There is also an "All in one" widget whose rows follow the switches in Settings. Arabic first, English in Settings.

## Install

Download the newest APK (always the same link):

https://github.com/architectahmedgaber-sys/claude-usage-widget/releases/latest/download/ClaudeLimit.apk

Allow installs from your browser when Android asks. Later versions update from inside the app (Settings, Check for updates).

## How it works

Anthropic publishes no API for plan usage, so the app signs in on the real claude.ai page once and then reads the same usage numbers the Settings → Usage page shows, through an invisible WebView (Cloudflare rejects plain HTTP clients). Nothing is sent anywhere except to claude.ai. Your session stays in the app's private storage, and Android backup is off.

- Unofficial. Not affiliated with Anthropic. The interface it reads is undocumented and can change, which may stop the app until it is updated.
- The app never uses Claude Code tokens or OAuth.

## Build

GitHub Actions builds the APK on every push to `main` (`.github/workflows/build.yml`). Raise `version.txt` to publish a new release. `./gradlew :core:test` runs the parser and pace tests.

The release key is kept in the repo (`signing/release.jks`) so every build installs over the previous one. This is a personal sideloaded app: if you fork it, make your own key.

Credits: the WebView approach and the sign-in flow follow [Limits for Claude](https://github.com/Tattanu/limits-for-claude) (MIT). The look follows Pocketta.

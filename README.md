# Filament

Charging animations for Android. Plug in and a full-screen animation appears (even on the lock screen); it eases to a stop at 100%.

**Animations:** Tide, Fireflies, Aurora, Clockwork, Constellation, Bloom, or "Surprise me" for a different one each time.

## Build
Push to GitHub. The **Build APK** workflow (Actions tab) produces `Filament-apk`. Download it, unzip, and install the APK on your phone.
Every build is signed with the same key in `app/filament-debug.jks`, so new versions install over old ones.

## First run on the phone
1. Open Filament and pick an animation (long-press a tile to preview it).
2. Under **Setup**, allow **Display over other apps** and **Unrestricted battery**.
3. Plug in.

Double-tap the animation to close it. Unplugging closes it too. After a minute at 100% the screen is allowed to sleep normally.
If Samsung's "Protect battery" stops charging at 80–85%, the animation stops there and says so.

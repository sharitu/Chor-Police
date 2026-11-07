# Chor Police – Android TV app

A small full-screen app that shows the game board on your TV. The game itself still runs on your laptop
(`start.command` / `start.bat` in the game folder); the TV app finds it on your Wi-Fi and opens the board.
The TV remote can press **Start game**, **Play again** and the replay buttons.

## Get the app (pick one)

### A. Build it in the cloud (no Android tools needed)
1. Make a free GitHub account, create a new private repository, and upload everything inside this `tv-app` folder
   (including the hidden `.github` folder).
2. Open the repository's **Actions** tab, choose **Build TV app**, press **Run workflow**.
3. After about 3 minutes, open the finished run and download **ChorPoliceTV-apk** (it contains `app-debug.apk`).

### B. Build it with Android Studio
Open this folder in Android Studio, wait for it to sync, then **Build > Build APK(s)**.

## Install on the TV (if a Claude session already has wireless debugging to your TV)
Ask it to run `adb install -r app-debug.apk`, then open Chor Police from the TV's app row.

## Install on the TV by hand
1. TV: Settings > Device Preferences > About, press **Build** 7 times to turn on Developer options.
2. Developer options: turn on **USB/ADB debugging** (and **Network debugging** if it is there).
3. Easiest way to install: put `app-debug.apk` on a USB stick and open it with the TV's file manager,
   or use the free **Send Files to TV** app on the TV and your phone/laptop. Allow "Install unknown apps" when asked.
   With a laptop on the same Wi-Fi you can also run `adb connect <TV IP>:5555` then `adb install app-debug.apk`.
4. Open **Chor Police** from the TV's app row.

## First launch
Start the game on the laptop, press **Find my game** on the TV. It saves the laptop's address and opens the board
straight away next time. If the laptop's address changes, press **Back** on the remote to change it.

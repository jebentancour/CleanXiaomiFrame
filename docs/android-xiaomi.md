# Xiaomi Google TV setup

How CleanXiaomiFrame was installed on the reference TV, a Xiaomi MiTV-MOOR4 (Google TV, Android 11), and how to undo each step. Other Xiaomi Google TVs are likely close; other brands will differ in the package names.

Every command runs on a computer on the same network as the TV. Replace `TV` with the TV's IP address (Settings → Network & Internet → your network).

## 1. Connect over ADB

On the TV: Settings → System → About → press OK on **Android TV OS build** seven times. Then Settings → System → Developer options → **USB debugging** on.

```sh
adb connect TV:5555
```

Accept **Allow USB debugging?** on the TV, ticking **Always allow from this computer**.

## 2. Debloat (optional)

[`android/debloat-xiaomi.txt`](../android/debloat-xiaomi.txt) lists the packages disabled on the reference TV, with the reasons. Read it first and comment out anything you use.

```sh
grep -vE '^\s*(#|$)' android/debloat-xiaomi.txt | while read p; do adb -s TV:5555 shell pm disable-user --user 0 "$p" </dev/null; done
```

A few packages may answer `SecurityException`: the firmware already hides them, and they stay off. To undo, run the same loop with `pm enable "$p"`.

## 3. Build and install

You need a JDK (17 or newer) and the Android SDK with platform 36.

```sh
cd android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # your SDK path
./gradlew assembleRelease
adb -s TV:5555 install -r app/build/outputs/apk/release/app-release.apk
```

The APK carries the web app from `app/`: change the interface there, rebuild and install again.

## 4. Permissions

**Recently used apps** (the Abiertas row in All apps) need usage access, which a TV has no screen to grant:

```sh
adb -s TV:5555 shell appops set app.cleanframe.tv GET_USAGE_STATS allow
```

**Remote buttons** (Settings, YouTube, Apps, and the blocked Xiaomi TV+, Netflix and Prime Video) go through an accessibility service:

```sh
adb -s TV:5555 shell settings put secure enabled_accessibility_services app.cleanframe.tv/app.cleanframe.tv.KeyService
adb -s TV:5555 shell settings put secure accessibility_enabled 1
```

**Android switches the accessibility service off on every reinstall: run those two lines after each `adb install`.** While it is off, pressing Settings with the stock launcher disabled crashes Xiaomi's `partnercustomizer`; Android then refuses to restart it and the Input button stops working too, until the TV is restarted.

## 5. Make it the Home screen

Google TV keeps Home while two of its system apps can answer it: its launcher and the setup wizard. Open CleanXiaomiFrame first, then disable both, then set the preference:

```sh
adb -s TV:5555 shell am start -n app.cleanframe.tv/.HomeActivity
adb -s TV:5555 shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb -s TV:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s TV:5555 shell cmd package set-home-activity app.cleanframe.tv/.HomeActivity
adb -s TV:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

The last line must print `app.cleanframe.tv/.HomeActivity`. If it does not, enable the launcher again (below) and look at what answered Home.

**Before a factory reset, enable the setup wizard again**: it is what runs the first-time setup.

To give Home back to Google TV:

```sh
adb -s TV:5555 shell pm enable com.google.android.apps.tv.launcherx
adb -s TV:5555 shell pm enable com.google.android.tungsten.setupwraith
```

## 6. Screensaver

```sh
adb -s TV:5555 shell settings put secure screensaver_components app.cleanframe.tv/.PhotoDream
adb -s TV:5555 shell settings put secure screensaver_enabled 1
```

It fades in from black and shows the wallpapers with the clock and date. It starts after the TV's sleep timeout (Settings → System → Power & energy). To go back to the Google one: `com.google.android.apps.tv.dreamx/.service.Backdrop`.

## 7. Updates

Firmware updates come from Google's system update. To stop them installing on their own and check by hand (Settings → System → About → System update):

```sh
adb -s TV:5555 shell settings put global ota_disable_automatic_update 1
```

Set it back to `0` to restore automatic updates.

## Using it

| Remote | On Home | Anywhere |
| --- | --- | --- |
| Arrows, OK | Move along the dock, open | |
| Back | Close the open panel; with none open, start the screensaver | |
| Home | | Back to CleanXiaomiFrame |
| Home held | | Quick menu (time and date); held again to close |
| Apps | | All apps |
| OK held, in All apps | Close the app, or its page in TV settings | |
| Settings | | TV settings |
| YouTube | | YouTube |
| Input, volume | | As before (Live TV's input list; the soundbar over HDMI-CEC) |
| Xiaomi TV+, Netflix, Prime Video | | Nothing |

The **Apariencia** tile, always last in the dock, opens the launcher's own settings: background, layout, fonts, focus, and **Apps** to add, remove and reorder the apps in the dock. HDMI devices come first in the dock and Apariencia last; neither can be moved.

### Quick menu

Home held past 200 ms shows, without waiting for release, a panel at the top right with the time and date as Home shows them (*Lunes 26 de Oct*, in the Home font, aligned left), over whatever is open, which keeps playing. A short Home press still goes to CleanXiaomiFrame, and closes the menu. Home held again, Back, OK or an arrow closes it; it also closes by itself after 10 seconds. Over the screensaver Home only wakes it. Holding Home no longer reaches the system's own long-press action. The accessibility service of step 4 draws it (`QuickMenu.java`, showing `src/main/dream/quick.html`), so while that service is off Home simply goes Home and holding it does what the system decides.

## Troubleshooting

- **The Input button does nothing**: Xiaomi's `partnercustomizer` crashed (usually Settings pressed while the accessibility service was off). Restart the TV, then switch the service on again (step 4).
- **The Abiertas row is empty**: grant usage access again (step 4).
- **An app misbehaves after closing it**: hold OK on it in All apps → Información de la app → Forzar detención or Borrar datos.
- **TikTok for TV** ("Error al generar el código", "Algo salió mal") did not work on the reference TV with or without this launcher: its servers rejected every request from that device.

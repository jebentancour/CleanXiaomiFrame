# Reference TV and verification status

Recorded through 20 October 2026. These results describe the maintainer's TV. Other models, firmware and operating systems need their own checks. Repo-only work must not contact the TV.

## Hardware and OS

| Item | Reference setup |
| --- | --- |
| TV | Samsung 55-inch The Frame, QA55LS03DAWXXY / LS03D |
| Firmware | 2130.0; displayed firmware string T-PTMDUABC-0090-2130.0 |
| OS and architecture | Tizen 9.0.0, ARMv7 / 32-bit ARM, confirmed by developer capability and native probe |
| Native helper environment | SDK user UID 901, .NET 6 runtime and required native libraries confirmed on this TV |
| Web engine | Exact Chromium/WebKit version has not been recorded; keep runtime ES5-compatible |

Developer Mode is enabled. The developer connection needed a full mains power cycle after enabling it. Network address, device identifier, signing material and diagnostic dumps are private local configuration.

## Current app and identity

CleanFrame 0.6.1 was signed, installed in place and launched on 20 October. The SDK reported installation complete, retained the app in its installed list and returned a successful launch. The author certificate and application identity stayed stable. The original Home/startup recovery snapshot and previous signed 0.6.0 package remain local.

- Package: `nClrkHome1`
- Application: `nClrkHome1.FrameHome`
- Display name: `CleanFrame` (earlier builds used `Frame Home Test`)

The current interface has List and Dock, independent appearance choices, an app editor, grouped Settings and fifteen wallpapers. Public builds contain Manrope, Geist and Newsreader. The public-source cleanup changed neither the installed widget nor the native configuration.

## Physically confirmed

The maintainer confirmed that the custom interface appears, opens Netflix and YouTube using the normal remote, and opens Other apps. The physical Home button from Netflix returns to CleanFrame. Netflix handles repeated Back inside its own app; an exit destination was not observed.

Netflix uses `org.tizen.netflix-app`. YouTube uses `9Ur5IzDKqV.TizenYouTube`, with `com.samsung.tv.cobalt-yt` as a fallback. These IDs came from this TV's installed-app list. Other TVs must discover their own IDs.

## Pending physical checks

The 0.6.1 installation and launch responses are command evidence. Current rendering, fonts, expanded catalogue, Settings and remote feel still need physical observation. Also pending are native Art Mode's destination, TV settings, corrected Samsung Home escape through `com.samsung.tv.csfs`, and normal power-on after streaming or Art Mode. An earlier report of startup after Netflix was provisional.

Browser and package checks establish source behaviour and archive contents. CPU-throttled Chromium timings are development comparisons; they are not TV benchmarks. Current browser checks are described in [testing](testing.md).

## Native Home and recovery

Native Home/startup was configured separately on 19 October. A guarded one-shot helper ran as the SDK user. Original typed values were saved privately before apply; exact-value restore/readback and reapply passed. The latest recorded readback assigns CleanFrame to Home, startup and boot-backup, with Home autostart enabled. The first-screen type still names Samsung Home.

Preserve this configuration and `.local/native-home/original.json`. Restore the original Home/startup values before uninstalling CleanFrame or changing its identity. The maintained command is `python3 scripts/native-home/run.py restore`; use it only in an explicitly authorised TV task. The earlier last-app-resume menu experiment does not describe the current native assignment.

The helper uses undocumented native operations, guarded for the reference identity and firmware. Its source is in `scripts/native-home/`; compiled tools, original values and diagnostics stay private. See [development](development.md), the [setup guide](agent-tv-setup-guide.md) and the [native helper notes](../scripts/native-home/README.md).

Implementation reference: [S90D firmware 2130 native Home work](https://github.com/splitcipher/couchroot/blob/83906b468836869933012da03a4018fb339462b3/docs/devices/s90d/README.md). That is a different model; it does not establish compatibility with another TV.

## Xiaomi Google TV (Android build)

Reference: Xiaomi MiTV-MOOR4, Google TV on Android 11, set up as in [Xiaomi Google TV setup](android-xiaomi.md).

On 26 October 2026, `app.cleanframe.tv` 0.6.1-android was reinstalled from scratch, because the debug key that signed the previous build was lost. Launcher preferences reset to defaults. Usage access, the accessibility service, the Home assignment and the screensaver were reapplied and read back equal to the values recorded just before. The signing key is now kept in the ignored `.local/android/debug.keystore`.

The owner confirmed on screen that Home pressed inside another app shows the quick menu over it, Home pressed again goes to CleanFrame, and Back closes the menu. This firmware delivers `KEYCODE_HOME` to the accessibility service. Home was then changed to press = Home, hold = quick menu, and the owner confirmed it on screen. With the overlay page preloaded, it now opens as soon as Home has been held 200 ms, also confirmed on screen. Back on Home starting the screensaver (System UI's `Somnambulator` shortcut) and the screensaver's clock and date were also confirmed on screen.

On 27 October 2026 the TV Manager, Google screensaver (`dreamx`) and USB media packages were disabled, and the system animations set to 0.5. Home, the screensaver component and the accessibility service read back unchanged afterwards, and the owner confirmed on screen that Input, Settings and the screensaver still work. The previous values are in `.local/android/before-2026-10-27.txt`.

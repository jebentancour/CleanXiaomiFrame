# Home Assistant Assist on the remote's microphone

On the reference TV, the remote's microphone button asks [Home Assistant](https://www.home-assistant.io/)'s Assist instead of Google Assistant. It is optional: without the setup below the button only shows "Assist no está configurado".

A press records the remote's microphone and streams it to HA's preferred Assist pipeline over the WebSocket API (`assist_pipeline/run`, speech-to-text through to text-to-speech). HA decides when you have stopped talking, with a 10-second limit. A panel like the quick menu's shows what HA heard and its answer, and the answer is spoken with the pipeline's voice while the app underneath pauses or ducks. A question within five minutes continues the same conversation. Pressing the button again, or Back, stops it. The code is `Assist.java` and `HaSocket.java` in `android/app/src/main/java/app/cleanframe/tv/`, and the panel is `android/app/src/main/dream/assist.html`.

**This was tested on one TV with one Home Assistant installation.** Read `docs/tv-status.md` before assuming it works on yours.

## What you need

- CleanXiaomiFrame installed and its accessibility service on ([setup guide](android-xiaomi.md), steps 3 and 4).
- A Home Assistant installation on the same network, with an Assist pipeline that has speech-to-text and text-to-speech set (Settings → Voice assistants). The preferred pipeline is used.
- Your own long-lived access token for that installation.

## Values your agent must supply

These are placeholders. Keep the real values in your own ignored `.local/` folder, never in the repository.

| Placeholder | Meaning | Where it comes from |
| --- | --- | --- |
| `<TV_IP>` | The TV's local address | Settings → Network & Internet on the TV, or your router's device list |
| `<HA_URL>` | HA's address as the TV reaches it, for example `http://<HA_IP>:8123` | The address you open HA with, resolved to an IP (see below) |
| `<HA_TOKEN>` | A long-lived access token | Created by you in HA; the agent never reads or prints it |

**Use an IP address for HA, not `homeassistant.local`.** The reference TV (Android 11) does not resolve `.local` names, even when the computer does. Find the IP from the computer (`dscacheutil -q host -a name homeassistant.local` on a Mac, `getent hosts homeassistant.local` on Linux, or the router), then check that the TV reaches it with `adb -s <TV_IP>:5555 shell ping -c 1 <HA_IP>`. Keep the port HA actually answers on: 8123 by default, none when it sits behind a proxy on port 80. A fixed IP for HA in the router stops the address from changing later. An `https://` address with a valid certificate also works.

## Prompt for your agent

```text
Read AGENTS.md, docs/android-xiaomi.md and docs/home-assistant-assist.md.
Help me point my remote's microphone button at Home Assistant's Assist.
CleanXiaomiFrame is already installed on my TV.

Check my Assist pipeline if you can reach Home Assistant; otherwise tell me
where to look. Find the address the TV can use for Home Assistant and check
the TV reaches it. Confirm which key my remote's microphone sends before
assuming the reference key. I will create the access token and write it
into .local/assist.properties myself; never ask me to paste it into the chat
and never print it. Check the file without showing the token.

Ask before connecting to the TV, and announce installs, permission grants
and restarts. Confirm what I see and hear on the TV.
```

## Steps

### 1. Check the Assist pipeline

In HA: Settings → Voice assistants. The preferred pipeline needs a speech-to-text and a text-to-speech engine, and a language. If your agent has a Home Assistant connection (for example an MCP server), it can list the pipelines read-only. Try a sentence by typing it in HA's own Assist dialog first: if HA does not understand typed text, it will not understand speech either.

### 2. Confirm the microphone key

On the reference remote (Xiaomi, Bluetooth), the microphone button sends `KEY_SEARCH`, which Xiaomi's key layout turns into Android key code **186** (`KEYCODE_PROG_BLUE`). `KeyService.java` listens for that code (`KEYCODE_MIC`). Another remote may send something else. To check, run both captures, press the button a few times within a minute, and look for the key:

```sh
adb -s <TV_IP>:5555 shell timeout 60 getevent -lt
adb -s <TV_IP>:5555 shell timeout 60 logcat -v time | grep -E "interceptKeyTq keycode="
```

If the code differs, change `KEYCODE_MIC` in `KeyService.java` and rebuild. Holding the button sends the same instant press as a tap, so the TV cannot use it as push-to-talk.

Google's own assistant (`com.google.android.katniss`) is disabled by the [debloat list](../android/debloat-xiaomi.txt), so nothing else answers the button. Apps with their own voice search, such as YouTube, still hear the remote.

### 3. Create the token and the local file

Create the token yourself in HA: your profile (bottom left) → Security → Long-lived access tokens → Create token. HA shows it once. Copy it, and with nothing else copied in between, write the file from the clipboard so the token never appears in the terminal or the chat:

```sh
# macOS; on Linux use "$(wl-paste)" or "$(xclip -o -selection clipboard)"
printf 'url=<HA_URL>\ntoken=%s\n' "$(pbpaste)" > .local/assist.properties
```

Or create `.local/assist.properties` in an editor:

```properties
url=<HA_URL>
token=<HA_TOKEN>
```

The agent can check the file without printing the token: the first line, the token's length (around 180 characters), and that HA accepts it (`200` from `/api/`):

```sh
head -1 .local/assist.properties
sed -n 2p .local/assist.properties | wc -c
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $(sed -n 's/^token=//p' .local/assist.properties)" <HA_URL>/api/
git check-ignore .local/assist.properties
```

`.local/` is ignored by Git. Do not commit the file, paste the token into an issue or a chat, or put it in a build.

### 4. Install, grant the microphone and push the file

After an APK install, switch the accessibility service on again (step 4 of the setup guide). Then:

```sh
adb -s <TV_IP>:5555 shell pm grant app.cleanframe.tv android.permission.RECORD_AUDIO
adb -s <TV_IP>:5555 shell mkdir -p /sdcard/Android/data/app.cleanframe.tv/files
adb -s <TV_IP>:5555 push .local/assist.properties /sdcard/Android/data/app.cleanframe.tv/files/assist.properties
```

The permission and the file survive later reinstalls. Other apps cannot read the app's own folder.

### 5. Test it

Press the microphone button once, wait for **Escuchando…** and ask something simple, such as the time. While testing, a capture shows each stage:

```sh
adb -s <TV_IP>:5555 shell timeout 60 logcat -v time | grep -E "keycode=186|cleanframe|AudioRecord|MediaPlayer|MediaFocusControl"
```

A working question shows the key, `requestAudioFocus` from `app.cleanframe.tv`, an `AudioRecord` line, then a `MediaPlayer` playing the answer and `abandonAudioFocus`. A `dead IAudioRecord, creating a new one` warning when recording starts is normal on the reference TV: the input switches to the remote's microphone and recording carries on.

## Troubleshooting

| Panel or symptom | Likely cause |
| --- | --- |
| Assist no está configurado | The file is missing, in the wrong folder, or has no `url` or `token` line |
| Sin conexión con Home Assistant | Wrong address or port, a `.local` name, or HA unreachable from the TV |
| Home Assistant rechazó el token | The token was revoked, mistyped or belongs to another installation |
| Sin permiso para el micrófono | `RECORD_AUDIO` not granted (step 4) |
| No te entendí | HA heard nothing it could transcribe: speak closer to the remote, check the pipeline's language |
| Nothing happens at all | The accessibility service is off after a reinstall, or the remote sends another key (step 2) |

## Undo

```sh
adb -s <TV_IP>:5555 shell pm revoke app.cleanframe.tv android.permission.RECORD_AUDIO
adb -s <TV_IP>:5555 shell rm /sdcard/Android/data/app.cleanframe.tv/files/assist.properties
```

Delete the token in HA (profile → Security) when you no longer use it.

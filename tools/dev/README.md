# Development helpers

Small scripts used while building and checking the app. They are not part of the product.

| Script | Use |
|---|---|
| `add_messages.py` | `python tools/dev/add_messages.py en.txt fr.txt` appends new English and French texts; `MessageKeysTest` then checks every key exists in both. |
| `check_store_texts.py` | `python tools/dev/check_store_texts.py` checks every field in `docs/store/*.md` against its store limit (characters, items, search terms). |
| `shot.ps1` | `powershell -File tools/dev/shot.ps1 -Out screen.png` waits for the desktop app's window (a `java` process) and saves a picture of it. It also finds the installed app (`RANN's Roost.exe`); either way it captures only the app's own window. |
| `click.ps1` | `powershell -File tools/dev/click.ps1 -X 100 -Y 200 [-Out screen.png]` clicks inside the app's window (coordinates from its top-left corner) and optionally saves a picture; `-Wheel -600` scrolls down there instead (120 per notch, positive scrolls up). |

Typical check of a screen: `./gradlew :app:desktop:runDemo -Plang=en -Psection=DOCUMENTS` in the background, then `shot.ps1`.
Android: start the emulator (`pixel_7_-_api_36_0`), `adb install -r app/android/build/outputs/apk/github/debug/android-github-debug.apk`, and `adb exec-out screencap -p > phone.png` (from Git Bash, not PowerShell, which corrupts binary output).
To pair the emulator with the demo (each demo run is a new household): run the demo with `-Psection=PHONES`, click "Pair a phone", then "Copy as text", and open the copied `hfmpair:` text on the phone with `adb shell am start -a android.intent.action.VIEW -d "'<text>'" ca.ranns.roost.mobile`. The phone's screen is 1080x2400 for `adb shell input tap`.
Update screens: sign a test release with a throwaway key (`tools/release` `keygen` into a temporary folder, then `sign-release`; delete the `release-key.pub` it writes into `core/update` afterwards), put that key's `release-key.pub` beside the files, and run the demo with `-Pupdate=<folder> -Psection=ABOUT`. The demo keeps its own settings, so the first-start question appears until it is answered there.

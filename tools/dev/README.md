# Development helpers

Small scripts used while building and checking the app. They are not part of the product.

| Script | Use |
|---|---|
| `add_messages.py` | `python tools/dev/add_messages.py en.txt fr.txt` appends new English and French texts; `MessageKeysTest` then checks every key exists in both. |
| `shot.ps1` | `powershell -File tools/dev/shot.ps1 -Out screen.png` waits for the desktop app's window (a `java` process) and saves a picture of it. |
| `click.ps1` | `powershell -File tools/dev/click.ps1 -X 100 -Y 200 [-Out screen.png]` clicks inside the app's window (coordinates from its top-left corner) and optionally saves a picture. |

Typical check of a screen: `./gradlew :app:desktop:runDemo -Plang=en -Psection=DOCUMENTS` in the background, then `shot.ps1`.
Android: start the emulator (`pixel_7_-_api_36_0`), `adb install -r app/android/build/outputs/apk/debug/android-debug.apk`, and `adb exec-out screencap -p > phone.png` (from Git Bash, not PowerShell, which corrupts binary output).

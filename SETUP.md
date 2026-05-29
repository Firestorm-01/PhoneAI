# PhoneAI v2 — Setup & Run Guide

## Prerequisites
| Tool | Version | Download |
|------|---------|----------|
| Android Studio | Hedgehog 2023.1.1+ | developer.android.com/studio |
| JDK | 17+ | Bundled with Android Studio |
| Android Phone | API 29+ (Android 10+) | — |
| Groq API Key | Free | console.groq.com |

---

## Step 1 — Open in Android Studio
```
1. Extract PhoneAI-Project.zip
2. Android Studio → File → Open → select /PhoneAI folder
3. Wait for Gradle sync (first time: ~2-3 min)
4. If "SDK not found" → File → Project Structure → set your SDK path
```

---

## Step 2 — Run the Unit Tests (no device needed)

### Option A — Android Studio UI
```
Right-click  app/src/test/kotlin/com/phoneai/assistant/PhoneAITests.kt
→ "Run PhoneAITests"
```

### Option B — Terminal
```bash
cd PhoneAI
./gradlew test --info          # Run all 58 tests
./gradlew test --tests "*.ActionTypeTests"        # One class
./gradlew test --tests "*.ConversationMemoryTests"
./gradlew test --tests "*.FocusModeManagerTests"
```

### Option C — Test report (HTML)
```bash
./gradlew test
open app/build/reports/tests/testDebugUnitTest/index.html
```

Expected output:
```
BUILD SUCCESSFUL
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
```

---

## Step 3 — Run on Device

### Enable Developer Mode on your phone
```
Settings → About Phone → tap "Build Number" 7 times
Settings → Developer Options → USB Debugging ON
```

### Connect and install
```bash
adb devices                    # Confirm phone is listed
./gradlew installDebug         # Build + install APK
```
Or in Android Studio: press the ▶ Run button with your phone selected.

---

## Step 4 — First Launch Setup (in-app)

### 4a. Enter Groq API Key
```
App → tap Settings (top right)
→ "Groq API Key" field → paste your key (starts with gsk_...)
→ SAVE SETTINGS
```

### 4b. Grant Special Permissions (app will prompt)

| Permission | Where to grant |
|-----------|---------------|
| Accessibility Service | Settings → Accessibility → Installed Services → PhoneAI → ON |
| Notification Access | Settings → Notifications → Notification Access → PhoneAI → ON |
| Device Admin | App prompts automatically on first launch |
| Overlay (Draw over other apps) | App links you directly |

### 4c. Set as Default Phone App (for InCallService)
```
Settings → Apps → Default Apps → Phone App → PhoneAI
```
> ⚠️ This is required to answer/decline calls programmatically.

### 4d. Set as Default Call Screening App
```
Settings → Apps → Default Apps → Caller ID & Spam → PhoneAI
```

---

## Step 5 — Test Features

### Voice commands (say after wake word "hey phone"):
```
"hey phone, turn off the screen"
"hey phone, call Priya"
"hey phone, battery level"
"hey phone, sleep mode"
"hey phone, drive mode for 2 hours"
"hey phone, read my notifications"
"hey phone, set alarm for 7am"
"hey phone, volume to 60"
```

### Gesture controls:
```
Shake phone once      → wake assistant
Shake phone twice     → call 112 (SOS)
Hold face-down        → silence incoming call
```

---

## Permissions Summary

| Permission | Used For |
|-----------|---------|
| RECORD_AUDIO | Wake word + voice commands |
| READ_PHONE_STATE | Detect calls |
| CALL_PHONE | Make outgoing calls |
| ANSWER_PHONE_CALLS | Answer incoming calls |
| READ/WRITE_CONTACTS | Resolve names to numbers |
| SEND_SMS / READ_SMS | SMS features |
| BIND_ACCESSIBILITY_SERVICE | Screen lock, power menu, navigation |
| BIND_NOTIFICATION_LISTENER | Read + dismiss notifications |
| BIND_INCALL_SERVICE | Manage active calls |
| BIND_SCREENING_SERVICE | Spam call blocking |
| FOREGROUND_SERVICE | Keep assistant alive |
| RECEIVE_BOOT_COMPLETED | Auto-start after reboot |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "API key not configured" | Add key in Settings |
| "Accessibility service not active" | Grant in Settings → Accessibility |
| Can't answer calls | Set PhoneAI as default Phone app |
| TTS not speaking | Check Settings → TTS enabled → ON |
| Shake not working | Grant BODY_SENSORS if prompted |
| Service keeps stopping | Disable battery optimization for PhoneAI |

### Disable battery optimization:
```
Settings → Battery → Battery Optimization → All Apps → PhoneAI → Don't optimize
```

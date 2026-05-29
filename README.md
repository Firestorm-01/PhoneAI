# PhoneAI Use Cases
⚠️Entire project needs to be tested before usage.This is a prototype.
## 🚗 Driving

- **"Hey Phone, call Mom"** → calls hands-free
- **"Hey Phone, read my messages"** → reads SMS aloud
- **"Hey Phone, drive mode"** → silences notifications, enables auto-reply
- **Face-down on seat** → silences incoming call
- **"Hey Phone, navigate to airport"** → opens Maps

## 😴 Sleep / Night

- **"Hey Phone, sleep mode"** → full silence, screen dim, DND
- **"Hey Phone, set alarm for 7 AM"** → alarm set, confirmed via TTS
- **Double shake** → emergency SOS even from bed
- **Battery at 5%** → spoken alert before it dies

## 🏋️ Gym

- **"Hey Phone, gym mode"** → max volume, calls silenced, auto-reply on
- **"Hey Phone, flashlight on"** → torch without touching screen
- **Shake** → wake assistant mid-workout without wet hands
- **"Hey Phone, set timer for 3 minutes"** → rest timer

## 📅 Meetings / Work

- **"Hey Phone, meeting mode for 2 hours"** → vibrate only, auto-reply SMS
- **Incoming spam call** → auto-declined silently
- **"Hey Phone, mute"** → mic muted mid-call
- **"Hey Phone, take screenshot"** → without touching screen

## ♿ Accessibility

- Full hands-free device control for motor-impaired users
- Voice answer/decline calls without touching phone
- **"Hey Phone, go home / go back"** → navigation without tapping
- TTS reads all responses — no screen needed

## 🔋 Power Management

- **"Hey Phone, what's my battery?"** → instant spoken answer
- Auto-alert at 20%, 10%, 5% thresholds
- Overheat warning if battery > 42°C
- **"Hey Phone, wifi off, bluetooth off"** → extend battery life

## 🤖 Smart Automation

- Morning routine at 7 AM: auto brightness + unmute + volume 60%
- Bedtime routine at 10:30 PM: dim screen + DND + low volume
- After every call: auto-unmute + speaker off
- Custom: "every weekday at 9, focus mode"

## 🆘 Emergency

- **Double shake anywhere** → calls 112 instantly
- Emergency numbers bypass all safety gates and focus modes
- Works on lock screen — no unlock needed
- **"Hey Phone, emergency"** → immediate dial

## 🔒 Privacy & Safety

- Every action logged with timestamp locally
- Dangerous actions (power off, SMS) need voice confirmation
- Rate limiting prevents abuse (20 actions/min)
- Spam calls auto-blocked before they ring

---

# 🚀 How to Run the Android App

## Step 1 — Prerequisites

You need:
- Android Studio Hedgehog or newer ([download at developer.android.com](https://developer.android.com))
- JDK 17+
- Android phone (API 29+ = Android 10+)
- A Groq API key ([free at console.groq.com](https://console.groq.com))

## Step 2 — Open the project

1. Extract `PhoneAI-Project.zip`
2. Open Android Studio → "Open Project" → select the PhoneAI folder
3. Let Gradle sync finish (2-3 min first time)

## Step 3 — Add your Groq key

The app has no hardcoded key. You add it at runtime in Settings.

## How to run — exact steps

### Tests (no phone needed, 30 seconds)

```bash
# Unzip, open terminal inside the PhoneAI folder
./gradlew test

# See HTML report
open app/build/reports/tests/testDebugUnitTest/index.html
```

**Expected:** 58 tests, 0 failures.

Or in Android Studio: right-click `PhoneAITests.kt` → Run.

### The app on your phone

```bash
# 1. Enable USB debugging on phone
#    Settings → About Phone → tap Build Number 7x
#    Settings → Developer Options → USB Debugging ON

# 2. Connect phone, then:
./gradlew installDebug

# 3. Open the app, tap Settings, paste your Groq key
```

Then grant 4 special permissions the app will prompt you for:

| Permission | Path |
|-----------|------|
| **Accessibility** | Settings → Accessibility → PhoneAI → ON |
| **Notification Access** | Settings → Notifications → Notification Access → PhoneAI |
| **Default Phone App** | Settings → Apps → Default [...] |

After that — say **"hey phone"** and you're live!

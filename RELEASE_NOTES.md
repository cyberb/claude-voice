## claude-voice

A native Android voice front-end for [Claude Code](https://www.anthropic.com/claude-code) that talks to a coding agent running entirely on your phone.

Everything runs on-device in Termux — no desktop, no SSH relay:

- **mic → whisper.cpp** for on-device speech-to-text
- **Go bridge** runs `claude -p` agents, one per working directory
- **reply → TTS** (system or Piper neural voice), with optional spoken narration of code

### Downloads

| File | What it is |
|------|------------|
| `claude-voice-0.1-release.apk` | The Android app (arm64 phones) |
| `claude-voice-bridge-arm64` | The Termux-side Go bridge (android/arm64 static binary) |

### Quick start

1. Install the APK on your phone.
2. In Termux, run the bridge (it listens on `127.0.0.1:8765`):
   ```
   VOICE_WORKDIR=$PWD ./claude-voice-bridge-arm64
   ```
3. Open the app, add an agent for a directory, and hold the mic button to talk.

Requires `claude` and `whisper.cpp` available in Termux.

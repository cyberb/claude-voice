# bridge

Termux-side localhost server that the Android app talks to. The Claude agent
runs **here**, where your repos and tools live; the app is only voice-in /
speech-out.

## Run

```bash
cd ~/path/to/repo-you-want-the-agent-to-work-on
python3 ~/storage/projects/claude-voice/bridge/server.py
```

Listens on `http://127.0.0.1:8765` (loopback is reachable cross-app on Android,
so the app on the same phone can connect).

## API

| Method | Path     | Body              | Returns                       |
|--------|----------|-------------------|-------------------------------|
| POST   | `/stt`   | WAV bytes         | transcript (whisper.cpp)      |
| POST   | `/chat`  | `{"text": "..."}` | agent reply (`claude -p`)     |
| GET    | `/health`| —                 | `ok`                          |

## Config (env)

- `VOICE_PORT` (default `8765`), `VOICE_HOST` (default `127.0.0.1`)
- `VOICE_PERM` claude permission mode (default `acceptEdits`; `bypassPermissions` for full hands-free)
- `VOICE_WORKDIR` agent working dir (default: cwd)
- `WHISPER_BIN`, `WHISPER_MODEL` paths to the whisper.cpp cli + ggml model

Requires the whisper.cpp build at `~/storage/projects/whisper.cpp` and a model
at `~/whisper-models/ggml-base.en.bin` (see repo README).

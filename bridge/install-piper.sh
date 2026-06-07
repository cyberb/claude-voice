#!/data/data/com.termux/files/usr/bin/bash
# Install Piper neural TTS for the claude-voice bridge (Termux/arm64).
#
# Prereq: grun (glibc runner) — pkg install glibc-runner  (from the termux glibc repo).
# Lays out ~/piper (engine) and ~/piper-voices (models); the bridge auto-detects them.
set -e

PIPER_REL="2023.11.14-2"
VOICE="en_US-lessac-medium"
VOICE_PATH="en/en_US/lessac/medium"

command -v grun >/dev/null || { echo "grun not found — run: pkg install glibc-runner"; exit 1; }
mkdir -p ~/piper-voices

if [ ! -x ~/piper/piper ]; then
  echo "downloading piper engine ($PIPER_REL, linux_aarch64)..."
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/p.tgz" "https://github.com/rhasspy/piper/releases/download/$PIPER_REL/piper_linux_aarch64.tar.gz"
  tar xzf "$tmp/p.tgz" -C "$tmp"
  rm -rf ~/piper && mv "$tmp/piper" ~/piper
  rm -rf "$tmp"
  chmod +x ~/piper/piper
fi

if [ ! -f ~/piper-voices/"$VOICE".onnx ]; then
  echo "downloading voice $VOICE (~60MB)..."
  base="https://huggingface.co/rhasspy/piper-voices/resolve/main/$VOICE_PATH"
  curl -fsSL -o ~/piper-voices/"$VOICE".onnx "$base/$VOICE.onnx"
  curl -fsSL -o ~/piper-voices/"$VOICE".onnx.json "$base/$VOICE.onnx.json"
fi

echo "ok. quick test:"
echo "hi from piper" | LD_LIBRARY_PATH=~/piper grun ~/piper/piper \
  -m ~/piper-voices/"$VOICE".onnx --espeak_data ~/piper/espeak-ng-data -f "${TMPDIR:-/tmp}/piper-test.wav" \
  && echo "wrote ${TMPDIR:-/tmp}/piper-test.wav — piper works."
echo
echo "Add more voices: drop <name>.onnx + <name>.onnx.json into ~/piper-voices"
echo "(browse https://huggingface.co/rhasspy/piper-voices). The bridge exposes them at GET /voices."

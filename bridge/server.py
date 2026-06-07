#!/data/data/com.termux/files/usr/bin/env python3
"""Termux-side bridge for the Claude Voice Android app.

Exposes a tiny localhost HTTP API the on-device app talks to:
  POST /stt   body: WAV bytes        -> text/plain transcript (whisper.cpp)
  POST /chat  body: {"text": "..."}  -> text/plain agent reply (claude CLI)
  GET  /health                       -> "ok"

The agent runs here, in Termux, where it has your repos and tools. The app is
just voice in / text + speech out. Run this from inside the repo you want the
agent to work on (its cwd becomes the agent's working directory).
"""
import json
import os
import subprocess
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

WHISPER = os.environ.get("WHISPER_BIN",
                         os.path.expanduser("~/storage/projects/whisper.cpp/build/bin/whisper-cli"))
MODEL = os.environ.get("WHISPER_MODEL",
                       os.path.expanduser("~/whisper-models/ggml-base.en.bin"))
PERM = os.environ.get("VOICE_PERM", "acceptEdits")
WORKDIR = os.environ.get("VOICE_WORKDIR", os.getcwd())
HOST = os.environ.get("VOICE_HOST", "127.0.0.1")
PORT = int(os.environ.get("VOICE_PORT", "8765"))

_session = {"started": False}


def transcribe(wav_bytes):
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, "in.wav")
        out = os.path.join(d, "out")
        with open(wav, "wb") as f:
            f.write(wav_bytes)
        subprocess.run(
            [WHISPER, "-m", MODEL, "-f", wav, "-l", "en", "-nt", "-np", "-otxt", "-of", out],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
        )
        try:
            with open(out + ".txt") as f:
                text = f.read()
        except FileNotFoundError:
            return ""
    for junk in ("[BLANK_AUDIO]", "(silence)"):
        text = text.replace(junk, "")
    return " ".join(text.split()).strip()


def ask(text):
    if not text:
        return ""
    cmd = ["claude", "-p"]
    if _session["started"]:
        cmd.append("--continue")
    cmd += ["--permission-mode", PERM, text]
    _session["started"] = True
    r = subprocess.run(cmd, cwd=WORKDIR, capture_output=True, text=True)
    return (r.stdout or "").strip() or "No response."


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body):
        b = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, "ok")
        else:
            self._send(404, "not found")

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        data = self.rfile.read(length)
        if self.path == "/stt":
            self._send(200, transcribe(data))
        elif self.path == "/chat":
            try:
                text = json.loads(data.decode("utf-8")).get("text", "")
            except Exception:
                text = ""
            self._send(200, ask(text))
        else:
            self._send(404, "not found")

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"claude-voice bridge on http://{HOST}:{PORT}  (cwd={WORKDIR}, perm={PERM})")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()

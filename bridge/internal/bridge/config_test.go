package bridge

import (
	"os"
	"path/filepath"
	"testing"
)

func TestEnvInt(t *testing.T) {
	if got := envInt("VOICE_TEST_MISSING_INT", 80); got != 80 {
		t.Errorf("envInt default = %d, want 80", got)
	}
	t.Setenv("VOICE_TEST_INT", "42")
	if got := envInt("VOICE_TEST_INT", 80); got != 42 {
		t.Errorf("envInt set = %d, want 42", got)
	}
	t.Setenv("VOICE_TEST_INT", "notanumber")
	if got := envInt("VOICE_TEST_INT", 80); got != 80 {
		t.Errorf("envInt invalid = %d, want 80 (default)", got)
	}
}

func TestEnv(t *testing.T) {
	if got := env("VOICE_TEST_MISSING_STR", "d"); got != "d" {
		t.Errorf("env default = %q, want d", got)
	}
	t.Setenv("VOICE_TEST_STR", "v")
	if got := env("VOICE_TEST_STR", "d"); got != "v" {
		t.Errorf("env set = %q, want v", got)
	}
}

func TestExpand(t *testing.T) {
	home, err := os.UserHomeDir()
	if err != nil {
		t.Skip("no home dir")
	}
	if got := expand("~/piper"); got != filepath.Join(home, "piper") {
		t.Errorf("expand ~ = %q, want %q", got, filepath.Join(home, "piper"))
	}
	if got := expand("/abs/path"); got != "/abs/path" {
		t.Errorf("expand abs = %q, want /abs/path", got)
	}
}

func TestDefaultConfigCompactAt(t *testing.T) {
	if got := DefaultConfig().CompactAt; got != 80 {
		t.Errorf("default CompactAt = %d, want 80", got)
	}
}

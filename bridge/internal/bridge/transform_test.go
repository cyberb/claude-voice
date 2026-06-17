package bridge

import (
	"testing"

	"github.com/syncloud/claude-voice/bridge/internal/bridge/model"
)

func TestTrunc(t *testing.T) {
	if got := trunc("  a\nb  ", 100); got != "a b" {
		t.Errorf("trunc collapse = %q, want %q", got, "a b")
	}
	if got := trunc("abcdef", 3); got != "abc…" {
		t.Errorf("trunc cut = %q, want %q", got, "abc…")
	}
	if got := trunc("abc", 3); got != "abc" {
		t.Errorf("trunc exact = %q, want %q", got, "abc")
	}
}

func TestToolLabel(t *testing.T) {
	cases := []struct {
		name string
		in   model.ToolInput
		want string
	}{
		{"Read", model.ToolInput{FilePath: "/a/b/main.go"}, "Read main.go"},
		{"Bash", model.ToolInput{Command: "ls -la"}, "Bash: ls -la"},
		{"Grep", model.ToolInput{Pattern: "foo"}, "Grep foo"},
		{"Edit", model.ToolInput{FilePath: "/x/y.go"}, "Edit y.go"},
		{"Unknown", model.ToolInput{}, "Unknown"},
	}
	for _, c := range cases {
		if got := toolLabel(c.name, c.in); got != c.want {
			t.Errorf("toolLabel(%s) = %q, want %q", c.name, got, c.want)
		}
	}
}

func TestDiffPatchEdit(t *testing.T) {
	patch, file, ok := diffPatch("Edit", model.ToolInput{FilePath: "/p/q.txt", OldString: "old", NewString: "new"})
	if !ok {
		t.Fatal("diffPatch(Edit) ok = false, want true")
	}
	if file != "q.txt" {
		t.Errorf("file = %q, want q.txt", file)
	}
	if patch != "- old\n+ new" {
		t.Errorf("patch = %q, want %q", patch, "- old\n+ new")
	}
}

func TestDiffPatchUnsupported(t *testing.T) {
	if _, _, ok := diffPatch("Read", model.ToolInput{FilePath: "/a/b"}); ok {
		t.Error("diffPatch(Read) ok = true, want false")
	}
}

func TestTransformEventResultSplitsNarration(t *testing.T) {
	ev := model.StreamEvent{
		Type:   "result",
		Result: "here is code\n===SPOKEN===\nspoken form",
		Usage:  &model.TokenUsage{InputTokens: 10, OutputTokens: 5},
		ModelUsage: map[string]model.ModelUsageInfo{
			"claude": {ContextWindow: 200000},
		},
	}
	out := transformEvent(ev)
	var usage, reply *model.Event
	for i := range out {
		switch out[i].T {
		case "usage":
			usage = &out[i]
		case "reply":
			reply = &out[i]
		}
	}
	if usage == nil || usage.Max == nil || *usage.Max != 200000 {
		t.Fatalf("usage event missing/wrong max: %+v", usage)
	}
	if usage.In == nil || *usage.In != 10 {
		t.Errorf("usage.In = %v, want 10", usage.In)
	}
	if reply == nil {
		t.Fatal("no reply event")
	}
	if reply.Text != "here is code" {
		t.Errorf("reply.Text = %q, want %q", reply.Text, "here is code")
	}
	if reply.Speech != "spoken form" {
		t.Errorf("reply.Speech = %q, want %q", reply.Speech, "spoken form")
	}
}

func TestTransformEventAssistantToolUse(t *testing.T) {
	ev := model.StreamEvent{
		Type: "assistant",
		Message: &model.Message{
			Content: []byte(`[{"type":"tool_use","name":"Read","input":{"file_path":"/a/b/x.go"}}]`),
		},
	}
	out := transformEvent(ev)
	if len(out) == 0 || out[0].T != "action" || out[0].Label != "Read x.go" {
		t.Fatalf("expected action 'Read x.go', got %+v", out)
	}
}

package model

import "testing"

func TestTokenUsageInOut(t *testing.T) {
	u := &TokenUsage{
		InputTokens:              100,
		OutputTokens:             20,
		CacheReadInputTokens:     300,
		CacheCreationInputTokens: 50,
	}
	in, out := u.InOut()
	if in != 450 {
		t.Errorf("in = %d, want 450 (input + cache read + cache creation)", in)
	}
	if out != 20 {
		t.Errorf("out = %d, want 20", out)
	}
}

func TestTokenUsageInOutNil(t *testing.T) {
	var u *TokenUsage
	in, out := u.InOut()
	if in != 0 || out != 0 {
		t.Errorf("nil InOut = (%d, %d), want (0, 0)", in, out)
	}
}

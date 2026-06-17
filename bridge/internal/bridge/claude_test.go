package bridge

import "testing"

func TestShouldCompact(t *testing.T) {
	cases := []struct {
		name              string
		inTok, maxCtx, at int
		want              bool
	}{
		{"disabled at zero percent", 200000, 200000, 0, false},
		{"under threshold", 100000, 200000, 80, false},
		{"exactly at threshold", 160000, 200000, 80, true},
		{"over threshold", 190000, 200000, 80, true},
		{"unknown context window", 190000, 0, 80, false},
		{"empty turn", 0, 200000, 80, false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := shouldCompact(c.inTok, c.maxCtx, c.at); got != c.want {
				t.Errorf("shouldCompact(%d, %d, %d) = %v, want %v", c.inTok, c.maxCtx, c.at, got, c.want)
			}
		})
	}
}

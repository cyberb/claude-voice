package org.syncloud.claudevoice

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun tokBelowThousand() = assertEquals("999", Format.tok(999))
    @Test fun tokExactThousand() = assertEquals("1k", Format.tok(1000))
    @Test fun tokThousands() = assertEquals("12k", Format.tok(12345))

    @Test fun modelStripsClaudePrefixAndDate() =
        assertEquals("opus-4-8", Format.model("claude-opus-4-8-20260101"))
    @Test fun modelWithoutDateSuffix() =
        assertEquals("sonnet-4-6", Format.model("claude-sonnet-4-6"))
    @Test fun modelLeavesUnknownUnchanged() =
        assertEquals("custom", Format.model("custom"))
}

package org.syncloud.claudevoice

object Format {
    fun tok(n: Int): String = if (n >= 1000) "${n / 1000}k" else "$n"

    fun model(raw: String): String =
        Regex("-\\d{8}$").replace(raw.removePrefix("claude-"), "")
}

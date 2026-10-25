package com.smartexpense.domain

/** Explicit numeric/spacing compatibility only; never turn superscripts or fractions into money. */
internal object CommandInputNormalizer {
    fun normalize(text: String): String = buildString(text.length) {
        var offset = 0
        while (offset < text.length) {
            val point = Character.codePointAt(text, offset)
            offset += Character.charCount(point)
            when {
                Character.getType(point) == Character.DECIMAL_DIGIT_NUMBER.toInt() ->
                    append(('0'.code + Character.digit(point, 10)).toChar())
                Character.isWhitespace(point) || Character.isSpaceChar(point) -> append(' ')
                point == 0xFF0E || point == 0x066B -> append('.')
                point == 0xFF0C || point == 0x066C -> append(',')
                point == 0xFF0D || point == 0xFE63 -> append('-')
                point == 0xFF0B || point == 0xFE62 -> append('+')
                else -> appendCodePoint(point)
            }
        }
    }
}

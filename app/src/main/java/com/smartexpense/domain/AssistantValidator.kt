package com.smartexpense.domain

/** Mirrors ml/tally-assistant/assistant_validate.py exactly; parity is checked by AssistantModelTest. */
internal object AssistantValidator {
    private val periods = mapOf(listOf("today") to "today", listOf("yesterday") to "yesterday", listOf("this", "week") to "this_week",
        listOf("last", "week") to "last_week", listOf("previous", "week") to "last_week", listOf("this", "month") to "this_month",
        listOf("last", "month") to "last_month", listOf("previous", "month") to "last_month", listOf("this", "year") to "this_year",
        listOf("last", "year") to "last_year", listOf("previous", "year") to "last_year")
    private val months = setOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val simple = setOf("today", "yesterday", "this_week", "last_week", "this_month", "last_month", "this_year", "last_year")
    private val filtered = setOf("spend", "income", "count", "average", "top", "largest")
    private val topicCues = mapOf("add" to setOf("add", "record", "log", "enter", "expense", "purchase"), "split" to setOf("split", "share", "shared", "friends"),
        "backup" to setOf("backup", "back", "restore", "move", "phone", "backed"), "budget" to setOf("budget", "budgets"),
        "export" to setOf("export", "csv", "download", "report", "transactions"), "lock" to setOf("lock", "pin", "fingerprint", "protect"),
        "receipt" to setOf("receipt", "bill", "scan", "import", "read"), "privacy" to setOf("private", "privacy", "upload", "internet", "see", "who"))
    private val strongCues = mapOf("split" to setOf("split"), "receipt" to setOf("receipt", "scan"), "budget" to setOf("budget", "budgets"),
        "lock" to setOf("lock"), "backup" to setOf("backup", "restore"), "export" to setOf("export", "csv"), "privacy" to setOf("privacy", "private"))
    private val percentCues = setOf("percent", "percentage", "%", "share", "part")
    private val compareCues = setOf("compare", "compared", "vs", "versus", "than", "or", "higher")

    fun placeholder(token: String, kinds: List<String> = listOf("CAT", "ACC", "NUM", "WORD")) =
        kinds.any { token.startsWith(it) && token.length > it.length && token.substring(it.length).all(Char::isDigit) }

    fun questionPeriods(source: List<String>): Set<List<String>> {
        val found = mutableSetOf<List<String>>()
        for (i in source.indices) {
            val token = source[i]
            if (token in months && !(token == "may" && i + 1 < source.size && source[i + 1] in setOf("i", "we"))) found += listOf("month", token)
            if ((token == "days" && i >= 1 && source[i - 1].startsWith("NUM") && i >= 2 && source[i - 2] == "last") ||
                (token == "days" && i >= 2 && source[i - 2] == "past")) found += listOf("last_days", source[i - 1])
            for ((words, name) in periods) {
                if (source.subList(i, minOf(source.size, i + words.size)) == words &&
                    !(name == "last_month" && i + 2 < source.size && source[i + 2] == "days")) found += listOf(name)
            }
        }
        return found
    }

    private fun parse(program: List<String>): List<List<String>>? {
        if (program.size < 2 || program.last() != "answer" || program[program.size - 2] != ";") return null
        val statements = mutableListOf<List<String>>(); var current = mutableListOf<String>()
        for (token in program.dropLast(1)) {
            if (token == ";") { if (current.isEmpty()) return null; statements += current; current = mutableListOf() } else current += token
        }
        if (current.isNotEmpty() || statements.size !in 1..4) return null
        return statements
    }

    private fun statement(s: List<String>, spendsBefore: Int): Pair<Boolean, List<String>?> {
        val head = s[0]; val args = s.drop(1)
        fun only(ok: Boolean) = ok to null
        return when (head) {
            in filtered -> {
                if (args.isEmpty()) return false to null
                var i: Int; val period: List<String>
                when {
                    args[0] in simple -> { period = listOf(args[0]); i = 1 }
                    args[0] == "month" && args.size > 1 && args[1] in months -> { period = listOf("month", args[1]); i = 2 }
                    args[0] == "last_days" && args.size > 1 && placeholder(args[1], listOf("NUM")) -> { period = listOf("last_days", args[1]); i = 2 }
                    else -> return false to null
                }
                while (i < args.size) {
                    val key = args[i++]
                    val kind = mapOf("cat" to "CAT", "acc" to "ACC", "n" to "NUM")[key]
                    if (kind != null && i < args.size && placeholder(args[i], listOf(kind))) i++
                    else if (key == "text" && i < args.size && placeholder(args[i], listOf("WORD"))) { while (i < args.size && placeholder(args[i], listOf("WORD"))) i++ }
                    else return false to null
                }
                if ("n" in args && head != "top") false to null else true to period
            }
            "compare", "share" -> only(args == listOf("$1", "$2") && spendsBefore == 2)
            "budget" -> only(args.isEmpty() || (args.size == 2 && args[0] == "cat" && placeholder(args[1], listOf("CAT"))))
            "forecast" -> only(args.isEmpty())
            "saveplan" -> only(args.size == 3 && placeholder(args[0], listOf("NUM")) && args[1] == "months" && placeholder(args[2], listOf("NUM")))
            "savetime" -> only(args.size == 3 && placeholder(args[0], listOf("NUM")) && args[1] == "per" && placeholder(args[2], listOf("NUM")))
            "whatif" -> only(args.size == 4 && args[0] == "cat" && placeholder(args[1], listOf("CAT")) && args[2] == "cut" && (args[3] == "all" || placeholder(args[3], listOf("NUM"))))
            "help" -> only(args.size == 1 && args[0] in topicCues.keys)
            "unknown" -> only(args.isEmpty())
            else -> only(false)
        }
    }

    fun valid(source: List<String>, program: List<String>): Boolean {
        val statements = parse(program) ?: return false
        val used = mutableSetOf<List<String>>(); var spends = 0
        statements.forEachIndexed { i, s ->
            if (s[0] in setOf("compare", "share") && i != statements.size - 1) return false
            val (ok, period) = statement(s, spends)
            if (!ok) return false
            if (s[0] == "spend" || s[0] == "income") spends++
            if (period != null) used += period
        }
        val heads = statements.map { it[0] }
        if (statements.size > 1 && !(heads.last() in setOf("compare", "share") && heads.dropLast(1).all { it in setOf("spend", "income") } && heads.size == 3)) return false
        val words = source.toSet()
        if (heads[0] == "help") {
            val scores = topicCues.mapValues { (topic, cues) -> (words intersect cues).size + (words intersect strongCues[topic].orEmpty()).size }
            val best = scores.values.max()
            if (best == 0 || scores.getValue(statements[0][1]) != best || scores.values.count { it == best } > 1) return false
        } else if (heads[0] != "unknown") {
            if ((words intersect percentCues).isNotEmpty() && (setOf("share", "whatif") intersect heads.toSet()).isEmpty()) return false
            if ((words intersect compareCues).isNotEmpty() && "compare" !in heads && "whatif" !in heads && "savetime" !in heads) return false
            if ("compare" in heads && (words intersect (compareCues + "more")).isEmpty()) return false
        }
        val placeholders = program.filter { placeholder(it) }.toSet()
        val mentioned = source.filter { placeholder(it) }.toSet()
        if (heads[0] in setOf("help", "unknown")) return placeholders.isEmpty()
        if (placeholders != mentioned) return false
        if (used.isNotEmpty() && used != questionPeriods(source)) return false
        return true
    }
}

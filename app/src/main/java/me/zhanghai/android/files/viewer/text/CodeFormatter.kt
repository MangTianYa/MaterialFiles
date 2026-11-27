/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

object CodeFormatter {
    sealed class Result {
        class Success(val formatted: String) : Result()
        class Error(val reason: String) : Result()
    }

    fun formatJson(source: String): Result {
        if (source.isBlank()) {
            return Result.Error("JSON 内容为空")
        }
        return try {
            val out = StringBuilder((source.length * 1.2).toInt().coerceAtLeast(64))
            JsonPrettyPrinter(source).format(out)
            Result.Success(out.toString())
        } catch (e: JsonParseException) {
            Result.Error(e.message ?: "JSON 语法错误")
        }
    }

    fun formatYaml(source: String): Result {
        if (source.isBlank()) {
            return Result.Error("YAML 内容为空")
        }
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')

        val outLines = ArrayList<String>(lines.size)
        // Maps observed source indent column -> normalized indent column (2-space steps).
        val indentStack = ArrayList<Pair<Int, Int>>()
        var lastWasBlank = false
        var blockScalarParentIndent = -1
        var blockScalarContentIndent = -1
        var hasTrailingNewline = source.endsWith('\n')

        for (lineIndex in lines.indices) {
            val lineNumber = lineIndex + 1
            val rawLine = lines[lineIndex].trimEnd(' ', '\t')

            if (rawLine.isBlank()) {
                if (outLines.isNotEmpty() && !lastWasBlank) {
                    outLines.add("")
                    lastWasBlank = true
                }
                continue
            }

            // Convert leading tabs to 2 spaces cleanly while measuring indentation.
            var pos = 0
            var rawIndent = 0
            while (pos < rawLine.length) {
                val c = rawLine[pos]
                if (c == ' ') {
                    rawIndent++
                    pos++
                } else if (c == '\t') {
                    rawIndent += 2
                    pos++
                } else {
                    break
                }
            }
            val content = rawLine.substring(pos)

            // Handle multi-line block scalar (`|` / `>`) content verbatim (just normalized indent).
            if (blockScalarParentIndent >= 0) {
                if (rawIndent > blockScalarParentIndent) {
                    if (blockScalarContentIndent < 0) {
                        blockScalarContentIndent = rawIndent
                    }
                    val targetParentNorm = indentStack.findLast { it.first <= blockScalarParentIndent }
                        ?.second ?: 0
                    val extraSpaces = (rawIndent - blockScalarContentIndent).coerceAtLeast(0)
                    val targetIndent = targetParentNorm + 2 + extraSpaces
                    outLines.add(" ".repeat(targetIndent) + content)
                    lastWasBlank = false
                    continue
                } else {
                    blockScalarParentIndent = -1
                    blockScalarContentIndent = -1
                }
            }

            // Document boundaries reset indent stack cleanly.
            if (content == "---" || content == "..." || content.startsWith("--- ")) {
                indentStack.clear()
                outLines.add(content)
                lastWasBlank = false
                continue
            }

            // Validate balanced quotes/flow delimiters on non-comment content lines.
            if (!content.startsWith('#')) {
                val balanceError = validateYamlLineDelimiters(content)
                if (balanceError != null) {
                    return Result.Error("第 $lineNumber 行：$balanceError")
                }
            }

            val normIndent = resolveNormalizedIndent(indentStack, rawIndent)
            val formattedContent = formatYamlLineContent(content)
            outLines.add(" ".repeat(normIndent) + formattedContent)
            lastWasBlank = false

            if (startsBlockScalar(formattedContent)) {
                blockScalarParentIndent = rawIndent
                blockScalarContentIndent = -1
            }
        }

        while (outLines.isNotEmpty() && outLines.last().isEmpty()) {
            outLines.removeAt(outLines.lastIndex)
        }
        val result = buildString {
            outLines.joinTo(this, "\n")
            if (hasTrailingNewline && outLines.isNotEmpty()) {
                append('\n')
            }
        }
        return Result.Success(result)
    }

    private fun resolveNormalizedIndent(
        stack: MutableList<Pair<Int, Int>>,
        rawIndent: Int
    ): Int {
        if (rawIndent == 0) {
            stack.clear()
            return 0
        }
        while (stack.isNotEmpty() && stack.last().first > rawIndent) {
            stack.removeAt(stack.lastIndex)
        }
        val top = stack.lastOrNull()
        return when {
            top == null -> {
                val norm = 2
                stack.add(rawIndent to norm)
                norm
            }
            top.first == rawIndent -> top.second
            else -> {
                val norm = top.second + 2
                stack.add(rawIndent to norm)
                norm
            }
        }
    }

    private fun startsBlockScalar(formattedContent: String): Boolean {
        val codePart = stripYamlInlineComment(formattedContent).trimEnd()
        if (codePart.isEmpty()) return false
        val colonIdx = findKeyColonIndex(codePart, startAt = 0)
        val rhs = if (colonIdx >= 0) {
            codePart.substring(colonIdx + 1).trim()
        } else if (codePart.startsWith("- ")) {
            codePart.substring(2).trim()
        } else {
            return false
        }
        return rhs == "|" || rhs == "|-" || rhs == "|+" ||
            rhs == ">" || rhs == ">-" || rhs == ">+" ||
            rhs.matches(Regex("[|>][+-]?[1-9]?"))
    }

    private fun formatYamlLineContent(content: String): String {
        if (content.startsWith('#')) {
            return formatYamlComment(content)
        }
        var lead = ""
        var rest = content
        if (rest.startsWith('-')) {
            if (rest.length == 1) {
                return "-"
            }
            val next = rest[1]
            if (next == ' ' || next == '\t') {
                lead = "- "
                rest = rest.substring(1).trimStart(' ', '\t')
                if (rest.startsWith('#')) {
                    return "- " + formatYamlComment(rest)
                }
            }
        }

        val commentSplit = splitYamlInlineComment(rest)
        val codePart = commentSplit.first.trimEnd()
        val commentPart = commentSplit.second

        val colonIdx = findKeyColonIndex(codePart, startAt = 0, allowCompactKey = lead.isEmpty())
        val formattedCode = if (colonIdx > 0) {
            val rawKey = codePart.substring(0, colonIdx).trimEnd()
            val rawVal = codePart.substring(colonIdx + 1).trimStart()
            if (rawVal.isEmpty()) {
                "$rawKey:"
            } else {
                "$rawKey: ${normalizeYamlInlineFlow(rawVal)}"
            }
        } else {
            normalizeYamlInlineFlow(codePart)
        }

        return buildString {
            append(lead)
            append(formattedCode)
            if (commentPart != null) {
                if (formattedCode.isNotEmpty() || lead.isNotEmpty()) append(' ')
                append(formatYamlComment(commentPart))
            }
        }
    }

    private fun formatYamlComment(comment: String): String {
        if (comment.length <= 1) return "#"
        val second = comment[1]
        return if (second == ' ' || second == '#' || second == '!') {
            comment.trimEnd()
        } else {
            "# ${comment.substring(1).trimEnd()}"
        }
    }

    private fun normalizeYamlInlineFlow(value: String): String {
        val t = value.trim()
        if (t.length < 2) return t
        val opener = t.first()
        val closer = t.last()
        if (!((opener == '[' && closer == ']') || (opener == '{' && closer == '}'))) {
            return t
        }
        val sb = StringBuilder(t.length)
        var quote = '\u0000'
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (quote != '\u0000') {
                sb.append(c)
                if (c == '\\' && quote == '"' && i + 1 < t.length) {
                    i++
                    sb.append(t[i])
                } else if (c == quote) {
                    quote = '\u0000'
                }
                i++
                continue
            }
            when (c) {
                '"', '\'' -> {
                    quote = c
                    sb.append(c)
                }
                '[' , '{' -> {
                    sb.append(c)
                    while (i + 1 < t.length && t[i + 1].isWhitespace()) i++
                }
                ']', '}' -> {
                    while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
                        sb.deleteCharAt(sb.length - 1)
                    }
                    sb.append(c)
                }
                ',' -> {
                    while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
                        sb.deleteCharAt(sb.length - 1)
                    }
                    sb.append(", ")
                    while (i + 1 < t.length && t[i + 1].isWhitespace()) i++
                }
                ':' -> {
                    while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
                        sb.deleteCharAt(sb.length - 1)
                    }
                    sb.append(": ")
                    while (i + 1 < t.length && t[i + 1].isWhitespace()) i++
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun stripYamlInlineComment(line: String): String = splitYamlInlineComment(line).first

    private fun splitYamlInlineComment(line: String): Pair<String, String?> {
        var quote = '\u0000'
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (quote != '\u0000') {
                if (ch == '\\' && quote == '"' && i + 1 < line.length) {
                    i += 2
                    continue
                }
                if (ch == quote) {
                    if (quote == '\'' && i + 1 < line.length && line[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    quote = '\u0000'
                }
            } else {
                if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '#' && (i == 0 || line[i - 1].isWhitespace())) {
                    return line.substring(0, i) to line.substring(i)
                }
            }
            i++
        }
        return line to null
    }

    private fun findKeyColonIndex(
        codePart: String,
        startAt: Int,
        allowCompactKey: Boolean = false
    ): Int {
        var quote = '\u0000'
        var depth = 0
        var i = startAt
        while (i < codePart.length) {
            val ch = codePart[i]
            if (quote != '\u0000') {
                if (ch == '\\' && quote == '"' && i + 1 < codePart.length) {
                    i += 2
                    continue
                }
                if (ch == quote) {
                    if (quote == '\'' && i + 1 < codePart.length && codePart[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    quote = '\u0000'
                }
            } else {
                when (ch) {
                    '"', '\'' -> quote = ch
                    '[', '{' -> depth++
                    ']', '}' -> if (depth > 0) depth--
                    ':' -> {
                        if (depth == 0) {
                            if (i + 1 == codePart.length || codePart[i + 1].isWhitespace()) {
                                return i
                            }
                            if (allowCompactKey && codePart[i + 1] != '/' &&
                                isSimpleYamlIdentifier(codePart, startAt, i)
                            ) {
                                return i
                            }
                        }
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun isSimpleYamlIdentifier(src: String, start: Int, end: Int): Boolean {
        if (end <= start) return false
        val first = src[start]
        if (!first.isLetter() && first != '_') return false
        for (idx in (start + 1) until end) {
            val c = src[idx]
            if (!c.isLetterOrDigit() && c != '_' && c != '-') return false
        }
        return true
    }

    private fun validateYamlLineDelimiters(content: String): String? {
        val codePart = stripYamlInlineComment(content).trimEnd()
        var quote = '\u0000'
        var bracketDepth = 0
        var braceDepth = 0
        var i = 0
        while (i < codePart.length) {
            val ch = codePart[i]
            if (quote != '\u0000') {
                if (ch == '\\' && quote == '"' && i + 1 < codePart.length) {
                    i += 2
                    continue
                }
                if (ch == quote) {
                    if (quote == '\'' && i + 1 < codePart.length && codePart[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    quote = '\u0000'
                }
            } else {
                when (ch) {
                    '"', '\'' -> quote = ch
                    '[' -> bracketDepth++
                    ']' -> if (--bracketDepth < 0) return "多余的 ']'"
                    '{' -> braceDepth++
                    '}' -> if (--braceDepth < 0) return "多余的 '}'"
                }
            }
            i++
        }
        if (quote != '\u0000') return "引号未闭合"
        if (bracketDepth > 0) return "缺少闭合的 ']'"
        if (braceDepth > 0) return "缺少闭合的 '}'"
        return null
    }

    // --- Streaming JSON Parser & Pretty-Printer ---

    private class JsonParseException(message: String) : Exception(message)

    private class JsonPrettyPrinter(private val src: String) {
        private var pos = 0
        private var line = 1
        private var col = 1
        private val len = src.length

        fun format(out: StringBuilder) {
            skipWhitespace()
            if (pos >= len) {
                throw error("JSON 内容为空")
            }
            writeValue(out, depth = 0)
            skipWhitespace()
            if (pos < len) {
                throw error("末尾存在多余字符 '${src[pos]}'")
            }
            out.append('\n')
        }

        private fun writeValue(out: StringBuilder, depth: Int) {
            skipWhitespace()
            if (pos >= len) {
                throw error("意外结束，缺少 JSON 值")
            }
            when (src[pos]) {
                '{' -> writeObject(out, depth)
                '[' -> writeArray(out, depth)
                '"' -> writeString(out)
                't' -> writeLiteral(out, "true")
                'f' -> writeLiteral(out, "false")
                'n' -> writeLiteral(out, "null")
                '-', in '0'..'9' -> writeNumber(out)
                else -> throw error("非法字符 '${src[pos]}'")
            }
        }

        private fun writeObject(out: StringBuilder, depth: Int) {
            consume('{')
            out.append('{')
            skipWhitespace()
            if (peek() == '}') {
                consume('}')
                out.append('}')
                return
            }
            val childIndent = "  ".repeat(depth + 1)
            val closeIndent = "  ".repeat(depth)
            var first = true
            while (true) {
                skipWhitespace()
                if (!first) {
                    out.append(',')
                }
                out.append('\n').append(childIndent)
                if (peek() != '"') {
                    throw error("对象键名必须使用双引号")
                }
                writeString(out)
                skipWhitespace()
                if (peek() != ':') {
                    throw error("对象键后缺少 ':'")
                }
                consume(':')
                out.append(": ")
                writeValue(out, depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        consume(',')
                        first = false
                    }
                    '}' -> {
                        consume('}')
                        out.append('\n').append(closeIndent).append('}')
                        return
                    }
                    else -> throw error("对象条目后缺少 ',' 或 '}'")
                }
            }
        }

        private fun writeArray(out: StringBuilder, depth: Int) {
            consume('[')
            out.append('[')
            skipWhitespace()
            if (peek() == ']') {
                consume(']')
                out.append(']')
                return
            }
            val childIndent = "  ".repeat(depth + 1)
            val closeIndent = "  ".repeat(depth)
            var first = true
            while (true) {
                if (!first) {
                    out.append(',')
                }
                out.append('\n').append(childIndent)
                writeValue(out, depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        consume(',')
                        first = false
                    }
                    ']' -> {
                        consume(']')
                        out.append('\n').append(closeIndent).append(']')
                        return
                    }
                    else -> throw error("数组元素后缺少 ',' 或 ']'")
                }
            }
        }

        private fun writeString(out: StringBuilder) {
            val startPos = pos
            consume('"')
            while (pos < len) {
                val c = src[pos]
                when {
                    c == '"' -> {
                        advance()
                        out.append(src, startPos, pos)
                        return
                    }
                    c == '\\' -> {
                        advance()
                        if (pos >= len) throw error("字符串转义未结束")
                        val esc = src[pos]
                        when (esc) {
                            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> advance()
                            'u' -> {
                                advance()
                                repeat(4) {
                                    if (pos >= len || !src[pos].isHex()) {
                                        throw error("无效的 Unicode 转义")
                                    }
                                    advance()
                                }
                            }
                            else -> throw error("无效转义符 '\\$esc'")
                        }
                    }
                    c < ' ' -> throw error("字符串包含未转义的控制字符")
                    else -> advance()
                }
            }
            throw error("字符串缺少闭合引号")
        }

        private fun writeLiteral(out: StringBuilder, literal: String) {
            if (pos + literal.length > len || !src.regionMatches(pos, literal, 0, literal.length)) {
                throw error("期望关键字 '$literal'")
            }
            repeat(literal.length) { advance() }
            out.append(literal)
        }

        private fun writeNumber(out: StringBuilder) {
            val startPos = pos
            if (peek() == '-') advance()
            if (pos >= len) throw error("数字格式无效")
            if (src[pos] == '0') {
                advance()
                if (pos < len && src[pos] in '0'..'9') {
                    throw error("数字不能包含前导零")
                }
            } else if (src[pos] in '1'..'9') {
                while (pos < len && src[pos] in '0'..'9') advance()
            } else {
                throw error("数字格式无效")
            }
            if (pos < len && src[pos] == '.') {
                advance()
                if (pos >= len || src[pos] !in '0'..'9') throw error("小数点后缺少数字")
                while (pos < len && src[pos] in '0'..'9') advance()
            }
            if (pos < len && (src[pos] == 'e' || src[pos] == 'E')) {
                advance()
                if (pos < len && (src[pos] == '+' || src[pos] == '-')) advance()
                if (pos >= len || src[pos] !in '0'..'9') throw error("指数缺少数字")
                while (pos < len && src[pos] in '0'..'9') advance()
            }
            out.append(src, startPos, pos)
        }

        private fun skipWhitespace() {
            while (pos < len) {
                when (src[pos]) {
                    ' ', '\t', '\r', '\n' -> advance()
                    else -> return
                }
            }
        }

        private fun peek(): Char = if (pos < len) src[pos] else '\u0000'

        private fun consume(expected: Char) {
            if (pos >= len || src[pos] != expected) {
                throw error("期望 '$expected'")
            }
            advance()
        }

        private fun advance() {
            if (src[pos] == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            pos++
        }

        private fun Char.isHex(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        private fun error(msg: String): JsonParseException =
            JsonParseException("第 $line 行第 $col 列：$msg")
    }
}

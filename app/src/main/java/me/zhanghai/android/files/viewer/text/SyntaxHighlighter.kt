/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.inputmethod.BaseInputConnection
import androidx.annotation.ColorInt
import androidx.core.text.getSpans
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.isLightTheme

enum class SyntaxLanguage {
    PLAIN_TEXT,
    YAML,
    JSON,
    TOML,
    INI,
    XML,
    MARKDOWN,
    KOTLIN,
    JAVA,
    JAVASCRIPT,
    TYPESCRIPT,
    C_CPP,
    CSHARP,
    GO,
    RUST,
    SWIFT,
    DART,
    PYTHON,
    RUBY,
    PHP,
    LUA,
    PERL,
    GROOVY,
    SCALA,
    R,
    PROTOBUF,
    GRAPHQL,
    SHELL,
    POWERSHELL,
    BATCH,
    DOCKERFILE,
    MAKEFILE,
    CMAKE,
    CSS,
    SQL,
    DIFF,
    LOG;

    val isCodeOrConfig: Boolean
        get() = this != PLAIN_TEXT

    companion object {
        fun detect(fileName: String?, sampleText: CharSequence? = null): SyntaxLanguage {
            val lowerName = fileName?.trim()?.lowercase().orEmpty()
            if (lowerName.isNotEmpty()) {
                when (lowerName) {
                    "dockerfile", "containerfile" -> return DOCKERFILE
                    "makefile", "gnumakefile" -> return MAKEFILE
                    "cmakelists.txt" -> return CMAKE
                    "gemfile", "rakefile", "podfile", "vagrantfile", "fastfile", "appfile" ->
                        return RUBY
                    "jenkinsfile" -> return GROOVY
                    "go.mod", "go.sum" -> return GO
                    "cargo.toml", "cargo.lock", "pyproject.toml" -> return TOML
                    "build.gradle", "settings.gradle" -> return GROOVY
                    "build.gradle.kts", "settings.gradle.kts" -> return KOTLIN
                    ".gitignore", ".gitattributes", ".gitconfig", ".gitmodules", ".dockerignore",
                    ".npmignore", ".editorconfig", ".env", "hosts", "build.prop", "fstab" ->
                        return INI
                    ".bashrc", ".bash_profile", ".zshrc", ".zprofile", ".profile", ".ashrc" ->
                        return SHELL
                    ".eslintrc", ".prettierrc", ".babelrc" -> return JSON
                }
                if (lowerName.endsWith(".dockerfile")) return DOCKERFILE
                if (lowerName.endsWith(".gradle.kts")) return KOTLIN
                if (lowerName.endsWith(".d.ts")) return TYPESCRIPT
                if (lowerName.endsWith(".env") || lowerName.contains(".env.")) return INI

                val ext = fileName?.asFileNameOrNull()?.singleExtension?.lowercase().orEmpty()
                extensionMap[ext]?.let { return it }
            }

            if (sampleText != null && sampleText.isNotEmpty()) {
                return sniffFromContent(sampleText)
            }
            return PLAIN_TEXT
        }

        private fun sniffFromContent(text: CharSequence): SyntaxLanguage {
            val end = text.length.coerceAtMost(512)
            var i = 0
            while (i < end && text[i].isWhitespace()) i++
            if (i >= end) return PLAIN_TEXT
            val slice = text.subSequence(i, end).toString()
            val firstLine = slice.lineSequence().firstOrNull().orEmpty().trim()
            if (firstLine.startsWith("#!")) {
                return when {
                    firstLine.contains("python") -> PYTHON
                    firstLine.contains("node") || firstLine.contains("deno") ||
                        firstLine.contains("bun") -> JAVASCRIPT
                    firstLine.contains("ruby") -> RUBY
                    firstLine.contains("perl") -> PERL
                    firstLine.contains("lua") -> LUA
                    firstLine.contains("php") -> PHP
                    firstLine.contains("pwsh") || firstLine.contains("powershell") -> POWERSHELL
                    else -> SHELL
                }
            }
            if (slice.startsWith("<?xml", ignoreCase = true) ||
                slice.startsWith("<!DOCTYPE", ignoreCase = true) ||
                slice.startsWith("<html", ignoreCase = true) ||
                slice.startsWith("<svg", ignoreCase = true) ||
                slice.startsWith("<manifest", ignoreCase = true) ||
                slice.startsWith("<resources", ignoreCase = true)
            ) {
                return XML
            }
            if ((slice.startsWith("{") || slice.startsWith("[")) &&
                (slice.contains("\":") || slice.contains("\" :"))
            ) {
                return JSON
            }
            return PLAIN_TEXT
        }

        private val extensionMap = mapOf(
            "yaml" to YAML, "yml" to YAML,
            "json" to JSON, "jsonc" to JSON, "json5" to JSON, "jsonl" to JSON, "ndjson" to JSON,
            "har" to JSON, "geojson" to JSON, "webmanifest" to JSON,
            "toml" to TOML,
            "ini" to INI, "cfg" to INI, "conf" to INI, "cnf" to INI, "properties" to INI,
            "prop" to INI, "env" to INI, "service" to INI, "desktop" to INI, "reg" to INI,
            "list" to INI,
            "xml" to XML, "html" to XML, "htm" to XML, "xhtml" to XML, "svg" to XML,
            "xaml" to XML, "xsd" to XML, "xsl" to XML, "xslt" to XML, "plist" to XML,
            "axml" to XML, "iml" to XML, "csproj" to XML, "vbproj" to XML, "props" to XML,
            "targets" to XML, "rss" to XML, "atom" to XML, "vue" to XML, "svelte" to XML,
            "astro" to XML,
            "md" to MARKDOWN, "markdown" to MARKDOWN, "mdx" to MARKDOWN, "mkd" to MARKDOWN,
            "kt" to KOTLIN, "kts" to KOTLIN,
            "java" to JAVA,
            "js" to JAVASCRIPT, "mjs" to JAVASCRIPT, "cjs" to JAVASCRIPT, "jsx" to JAVASCRIPT,
            "ts" to TYPESCRIPT, "mts" to TYPESCRIPT, "cts" to TYPESCRIPT, "tsx" to TYPESCRIPT,
            "c" to C_CPP, "h" to C_CPP, "cpp" to C_CPP, "cc" to C_CPP, "cxx" to C_CPP,
            "hpp" to C_CPP, "hh" to C_CPP, "hxx" to C_CPP, "ino" to C_CPP, "m" to C_CPP,
            "mm" to C_CPP, "aidl" to C_CPP,
            "cs" to CSHARP, "razor" to CSHARP,
            "go" to GO,
            "rs" to RUST,
            "swift" to SWIFT,
            "dart" to DART,
            "py" to PYTHON, "pyw" to PYTHON, "pyi" to PYTHON, "pyx" to PYTHON, "py3" to PYTHON,
            "wsgi" to PYTHON,
            "rb" to RUBY, "rake" to RUBY, "gemspec" to RUBY,
            "php" to PHP, "phtml" to PHP, "php3" to PHP, "php4" to PHP, "php5" to PHP,
            "lua" to LUA,
            "pl" to PERL, "pm" to PERL, "t" to PERL,
            "groovy" to GROOVY, "gradle" to GROOVY, "gvy" to GROOVY,
            "scala" to SCALA, "sc" to SCALA, "sbt" to SCALA,
            "r" to R,
            "proto" to PROTOBUF,
            "graphql" to GRAPHQL, "gql" to GRAPHQL,
            "sh" to SHELL, "bash" to SHELL, "zsh" to SHELL, "fish" to SHELL, "ash" to SHELL,
            "ksh" to SHELL, "rc" to SHELL, "csh" to SHELL,
            "ps1" to POWERSHELL, "psm1" to POWERSHELL, "psd1" to POWERSHELL,
            "bat" to BATCH, "cmd" to BATCH,
            "dockerfile" to DOCKERFILE,
            "mk" to MAKEFILE, "mak" to MAKEFILE,
            "cmake" to CMAKE,
            "css" to CSS, "scss" to CSS, "sass" to CSS, "less" to CSS, "styl" to CSS,
            "sql" to SQL, "ddl" to SQL, "dml" to SQL, "psql" to SQL,
            "diff" to DIFF, "patch" to DIFF,
            "log" to LOG
        )
    }
}

class SyntaxHighlightSpan(@ColorInt color: Int) : ForegroundColorSpan(color)

class SyntaxStyleSpan(style: Int) : StyleSpan(style)

data class SyntaxToken(
    val start: Int,
    val end: Int,
    @ColorInt val color: Int,
    val bold: Boolean = false,
    val italic: Boolean = false
)

data class SyntaxHighlightResult(
    val tokens: List<SyntaxToken>
)

private data class SyntaxPalette(
    @ColorInt val comment: Int,
    @ColorInt val property: Int,
    @ColorInt val keyword: Int,
    @ColorInt val controlKeyword: Int,
    @ColorInt val string: Int,
    @ColorInt val number: Int,
    @ColorInt val type: Int,
    @ColorInt val function: Int,
    @ColorInt val punctuation: Int,
    @ColorInt val error: Int,
    @ColorInt val diffAdded: Int
)

private data class CodeLanguageSpec(
    val lineComment: String? = "//",
    val secondaryLineComment: String? = null,
    val blockCommentStart: String? = "/*",
    val blockCommentEnd: String? = "*/",
    val supportsPreprocessor: Boolean = false,
    val supportsAnnotations: Boolean = true,
    val supportsBacktickStrings: Boolean = false,
    val supportsTripleQuotes: Boolean = false,
    val supportsRustLifetimes: Boolean = false,
    val supportsShellVars: Boolean = false,
    val supportsCssSyntax: Boolean = false,
    val caseInsensitiveKeywords: Boolean = false,
    val keywords: Set<String> = emptySet(),
    val controlKeywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet()
)

object SyntaxHighlighter {
    private const val MAX_HIGHLIGHT_LENGTH = 200_000
    private const val MAX_SPANS = 16_000

    private val darkPalette = SyntaxPalette(
        comment = Color.parseColor("#6E7681"),
        property = Color.parseColor("#73D17E"),
        keyword = Color.parseColor("#569CD6"),
        controlKeyword = Color.parseColor("#C586C0"),
        string = Color.parseColor("#8AC8FF"),
        number = Color.parseColor("#B5CEA8"),
        type = Color.parseColor("#4EC9B0"),
        function = Color.parseColor("#DCDCAA"),
        punctuation = Color.parseColor("#9DA5B4"),
        error = Color.parseColor("#F48771"),
        diffAdded = Color.parseColor("#73D17E")
    )

    private val lightPalette = SyntaxPalette(
        comment = Color.parseColor("#6E7781"),
        property = Color.parseColor("#116329"),
        keyword = Color.parseColor("#0550AE"),
        controlKeyword = Color.parseColor("#8250DF"),
        string = Color.parseColor("#0369A1"),
        number = Color.parseColor("#1F6E43"),
        type = Color.parseColor("#0F766E"),
        function = Color.parseColor("#795E26"),
        punctuation = Color.parseColor("#57606A"),
        error = Color.parseColor("#CF222E"),
        diffAdded = Color.parseColor("#116329")
    )

    fun paletteFor(context: Context): Boolean = context.isLightTheme

    fun computeHighlights(
        source: CharSequence,
        language: SyntaxLanguage,
        isLightTheme: Boolean,
        checkCancelled: () -> Unit = {}
    ): SyntaxHighlightResult {
        if (language == SyntaxLanguage.PLAIN_TEXT || source.isEmpty()) {
            return SyntaxHighlightResult(emptyList())
        }
        val limit = source.length.coerceAtMost(MAX_HIGHLIGHT_LENGTH)
        val palette = if (isLightTheme) lightPalette else darkPalette
        val tokens = ArrayList<SyntaxToken>(512)
        val collector = TokenCollector(limit, tokens, checkCancelled)
        when (language) {
            SyntaxLanguage.YAML -> scanYaml(source, limit, palette, collector)
            SyntaxLanguage.JSON -> scanJson(source, limit, palette, collector)
            SyntaxLanguage.TOML -> scanIniConf(source, limit, palette, collector, allowToml = true)
            SyntaxLanguage.INI -> scanIniConf(source, limit, palette, collector, allowToml = false)
            SyntaxLanguage.XML -> scanXmlHtml(source, limit, palette, collector)
            SyntaxLanguage.MARKDOWN -> scanMarkdown(source, limit, palette, collector)
            SyntaxLanguage.DIFF -> scanDiff(source, limit, palette, collector)
            SyntaxLanguage.LOG -> scanLog(source, limit, palette, collector)
            else -> {
                val spec = codeSpecs[language] ?: return SyntaxHighlightResult(emptyList())
                scanCode(source, limit, palette, spec, collector)
            }
        }
        return SyntaxHighlightResult(tokens)
    }

    fun applyHighlights(editable: Editable, result: SyntaxHighlightResult) {
        val oldColorSpans = editable.getSpans<SyntaxHighlightSpan>(0, editable.length)
        for (span in oldColorSpans) {
            editable.removeSpan(span)
        }
        val oldStyleSpans = editable.getSpans<SyntaxStyleSpan>(0, editable.length)
        for (span in oldStyleSpans) {
            editable.removeSpan(span)
        }
        if (result.tokens.isEmpty()) {
            return
        }
        val len = editable.length
        val composingStart = BaseInputConnection.getComposingSpanStart(editable)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(editable)
        val hasComposing = composingStart in 0 until composingEnd && composingEnd <= len

        for (token in result.tokens) {
            val start = token.start
            val end = token.end.coerceAtMost(len)
            if (start < 0 || start >= end) continue
            if (hasComposing && start < composingEnd && end > composingStart) {
                continue
            }
            editable.setSpan(
                SyntaxHighlightSpan(token.color),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (token.bold || token.italic) {
                val style = when {
                    token.bold && token.italic -> Typeface.BOLD_ITALIC
                    token.bold -> Typeface.BOLD
                    else -> Typeface.ITALIC
                }
                editable.setSpan(
                    SyntaxStyleSpan(style),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private class TokenCollector(
        private val limit: Int,
        private val out: MutableList<SyntaxToken>,
        private val checkCancelled: () -> Unit
    ) {
        private var stepCounter = 0

        val isFull: Boolean
            get() = out.size >= MAX_SPANS

        fun tick() {
            if ((++stepCounter and 0x3FF) == 0) {
                checkCancelled()
            }
        }

        fun add(
            start: Int,
            end: Int,
            @ColorInt color: Int,
            bold: Boolean = false,
            italic: Boolean = false
        ) {
            if (out.size >= MAX_SPANS) return
            val s = start.coerceAtLeast(0)
            val e = end.coerceAtMost(limit)
            if (s < e) {
                out.add(SyntaxToken(s, e, color, bold, italic))
            }
        }
    }

    // --- YAML ---

    private fun scanYaml(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = 0
        while (i < limit && !out.isFull) {
            out.tick()
            val lineStart = i
            var lineEnd = i
            while (lineEnd < limit && text[lineEnd] != '\n') lineEnd++
            scanYamlLine(text, lineStart, lineEnd, p, out)
            i = lineEnd + 1
        }
    }

    private fun scanYamlLine(
        text: CharSequence,
        lineStart: Int,
        lineEnd: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var idx = lineStart
        while (idx < lineEnd && (text[idx] == ' ' || text[idx] == '\t')) idx++
        if (idx >= lineEnd) return

        // Comment-only line.
        if (text[idx] == '#') {
            out.add(idx, lineEnd, p.comment, italic = true)
            return
        }

        // Document markers.
        val trimmedLen = lineEnd - idx
        if (trimmedLen >= 3 &&
            ((text[idx] == '-' && text[idx + 1] == '-' && text[idx + 2] == '-') ||
                (text[idx] == '.' && text[idx + 1] == '.' && text[idx + 2] == '.')) &&
            (trimmedLen == 3 || text[idx + 3].isWhitespace())
        ) {
            out.add(idx, idx + 3, p.type, bold = true)
            idx += 3
        }

        // Sequence dash(es) at start of item.
        while (idx < lineEnd && text[idx] == '-' &&
            (idx + 1 == lineEnd || text[idx + 1] == ' ' || text[idx + 1] == '\t')
        ) {
            out.add(idx, idx + 1, p.punctuation)
            idx++
            while (idx < lineEnd && (text[idx] == ' ' || text[idx] == '\t')) idx++
        }
        if (idx >= lineEnd) return
        if (text[idx] == '#') {
            out.add(idx, lineEnd, p.comment, italic = true)
            return
        }

        // Scan line while respecting quotes and finding key: value boundary + inline comment.
        var effectiveEnd = lineEnd
        var quote: Char = '\u0000'
        var colonIndex = -1
        var j = idx
        while (j < lineEnd) {
            val ch = text[j]
            if (quote != '\u0000') {
                if (ch == '\\' && quote == '"' && j + 1 < lineEnd) {
                    j += 2
                    continue
                }
                if (ch == quote) {
                    if (quote == '\'' && j + 1 < lineEnd && text[j + 1] == '\'') {
                        j += 2
                        continue
                    }
                    quote = '\u0000'
                }
            } else {
                if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '#' && (j == lineStart || text[j - 1] == ' ' || text[j - 1] == '\t')) {
                    effectiveEnd = j
                    break
                } else if (ch == ':' && colonIndex < 0 &&
                    (j + 1 == lineEnd || text[j + 1] == ' ' || text[j + 1] == '\t') &&
                    j > idx
                ) {
                    // Do not mistake flow array/map inside value as mapping key if starts with [{
                    if (text[idx] != '[' && text[idx] != '{') {
                        colonIndex = j
                    }
                }
            }
            j++
        }

        var valStart = idx
        if (colonIndex > idx) {
            var keyEnd = colonIndex
            while (keyEnd > idx && (text[keyEnd - 1] == ' ' || text[keyEnd - 1] == '\t')) keyEnd--
            if (keyEnd > idx) {
                out.add(idx, keyEnd, p.property)
            }
            out.add(colonIndex, colonIndex + 1, p.punctuation)
            valStart = colonIndex + 1
        }

        scanYamlValue(text, valStart, effectiveEnd, p, out)

        if (effectiveEnd < lineEnd) {
            out.add(effectiveEnd, lineEnd, p.comment, italic = true)
        }
    }

    private fun scanYamlValue(
        text: CharSequence,
        start: Int,
        end: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = start
        while (i < end) {
            val ch = text[i]
            when {
                ch == ' ' || ch == '\t' -> i++
                ch == '"' || ch == '\'' -> {
                    val strEnd = scanQuoted(text, i, end, ch, doubleEscapeSingle = ch == '\'')
                    out.add(i, strEnd, p.string)
                    i = strEnd
                }
                ch == '&' || ch == '*' || ch == '!' -> {
                    var tokEnd = i + 1
                    while (tokEnd < end && !text[tokEnd].isWhitespace() &&
                        text[tokEnd] != ',' && text[tokEnd] != ']' && text[tokEnd] != '}'
                    ) {
                        tokEnd++
                    }
                    out.add(i, tokEnd, p.type)
                    i = tokEnd
                }
                ch == '|' || ch == '>' -> {
                    out.add(i, i + 1, p.controlKeyword, bold = true)
                    i++
                }
                ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ',' || ch == ':' -> {
                    out.add(i, i + 1, p.punctuation)
                    i++
                }
                else -> {
                    var tokEnd = i + 1
                    while (tokEnd < end && !text[tokEnd].isWhitespace() &&
                        text[tokEnd] != ',' && text[tokEnd] != ']' && text[tokEnd] != '}' &&
                        text[tokEnd] != '"' && text[tokEnd] != '\''
                    ) {
                        tokEnd++
                    }
                    val token = text.subSequence(i, tokEnd).toString()
                    val lower = token.lowercase()
                    when {
                        lower in YAML_BOOL_OR_NULL -> out.add(i, tokEnd, p.keyword)
                        isNumericLiteral(token) -> out.add(i, tokEnd, p.number)
                    }
                    i = tokEnd
                }
            }
        }
    }

    // --- JSON / JSONC / JSON5 ---

    private fun scanJson(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = 0
        while (i < limit && !out.isFull) {
            out.tick()
            val ch = text[i]
            when {
                ch.isWhitespace() -> i++
                ch == '/' && i + 1 < limit && text[i + 1] == '/' -> {
                    val e = findLineEnd(text, i + 2, limit)
                    out.add(i, e, p.comment, italic = true)
                    i = e
                }
                ch == '/' && i + 1 < limit && text[i + 1] == '*' -> {
                    val e = findBlockCommentEnd(text, i + 2, limit, "*/")
                    out.add(i, e, p.comment, italic = true)
                    i = e
                }
                ch == '"' || ch == '\'' -> {
                    val strEnd = scanQuoted(text, i, limit, ch, doubleEscapeSingle = false)
                    var peek = strEnd
                    while (peek < limit && (text[peek] == ' ' || text[peek] == '\t')) peek++
                    val isKey = peek < limit && text[peek] == ':'
                    out.add(i, strEnd, if (isKey) p.property else p.string)
                    i = strEnd
                }
                ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == ':' || ch == ',' -> {
                    out.add(i, i + 1, p.punctuation)
                    i++
                }
                ch.isDigit() || (ch == '-' && i + 1 < limit && text[i + 1].isDigit()) -> {
                    val numEnd = scanNumber(text, i, limit)
                    out.add(i, numEnd, p.number)
                    i = numEnd
                }
                isIdentStart(ch) -> {
                    val idEnd = scanIdentifier(text, i, limit)
                    var peek = idEnd
                    while (peek < limit && (text[peek] == ' ' || text[peek] == '\t')) peek++
                    val word = text.subSequence(i, idEnd).toString()
                    when {
                        peek < limit && text[peek] == ':' -> out.add(i, idEnd, p.property)
                        word in JSON_KEYWORDS -> out.add(i, idEnd, p.keyword)
                        word == "Infinity" || word == "NaN" -> out.add(i, idEnd, p.number)
                    }
                    i = idEnd
                }
                else -> i++
            }
        }
    }

    // --- TOML / INI / Properties / Conf / Env ---

    private fun scanIniConf(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector,
        allowToml: Boolean
    ) {
        var i = 0
        while (i < limit && !out.isFull) {
            out.tick()
            val lineStart = i
            var lineEnd = i
            while (lineEnd < limit && text[lineEnd] != '\n') lineEnd++

            var idx = lineStart
            while (idx < lineEnd && (text[idx] == ' ' || text[idx] == '\t')) idx++
            if (idx < lineEnd) {
                val first = text[idx]
                when {
                    first == '#' || (!allowToml && first == ';') -> {
                        out.add(idx, lineEnd, p.comment, italic = true)
                    }
                    first == '[' -> {
                        val close = indexOfCharInLine(text, idx + 1, lineEnd, ']')
                        val secEnd = if (close >= 0) {
                            if (close + 1 < lineEnd && text[close + 1] == ']') close + 2 else close + 1
                        } else {
                            lineEnd
                        }
                        out.add(idx, secEnd, p.type, bold = true)
                    }
                    else -> {
                        // Look for `=` or `:` assignment before inline comment
                        var assignPos = -1
                        var commentPos = lineEnd
                        var q = '\u0000'
                        var k = idx
                        while (k < lineEnd) {
                            val c = text[k]
                            if (q != '\u0000') {
                                if (c == '\\' && q == '"' && k + 1 < lineEnd) {
                                    k += 2
                                    continue
                                }
                                if (c == q) q = '\u0000'
                            } else {
                                if (c == '"' || c == '\'') {
                                    q = c
                                } else if ((c == '#' || (!allowToml && c == ';')) &&
                                    (k == idx || text[k - 1].isWhitespace())
                                ) {
                                    commentPos = k
                                    break
                                } else if (assignPos < 0 && (c == '=' || (!allowToml && c == ':'))) {
                                    assignPos = k
                                }
                            }
                            k++
                        }
                        val valStart = if (assignPos > idx) {
                            var keyStart = idx
                            if (text.subSequence(idx, assignPos).toString().startsWith("export ")) {
                                out.add(idx, idx + 6, p.keyword)
                                keyStart = idx + 7
                                while (keyStart < assignPos && text[keyStart].isWhitespace()) keyStart++
                            }
                            var keyEnd = assignPos
                            while (keyEnd > keyStart && text[keyEnd - 1].isWhitespace()) keyEnd--
                            out.add(keyStart, keyEnd, p.property)
                            out.add(assignPos, assignPos + 1, p.punctuation)
                            assignPos + 1
                        } else {
                            idx
                        }
                        scanYamlValue(text, valStart, commentPos, p, out)
                        if (commentPos < lineEnd) {
                            out.add(commentPos, lineEnd, p.comment, italic = true)
                        }
                    }
                }
            }
            i = lineEnd + 1
        }
    }

    // --- XML / HTML / SVG ---

    private fun scanXmlHtml(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = 0
        while (i < limit && !out.isFull) {
            out.tick()
            val ch = text[i]
            when {
                ch == '<' -> {
                    when {
                        startsWithAt(text, i, limit, "<!--") -> {
                            val end = findBlockCommentEnd(text, i + 4, limit, "-->")
                            out.add(i, end, p.comment, italic = true)
                            i = end
                        }
                        startsWithAt(text, i, limit, "<![CDATA[") -> {
                            val end = findBlockCommentEnd(text, i + 9, limit, "]]>")
                            out.add(i, (i + 9).coerceAtMost(end), p.type)
                            if (end - 3 > i + 9) out.add(i + 9, end - 3, p.string)
                            if (end >= i + 12) out.add(end - 3, end, p.type)
                            i = end
                        }
                        else -> {
                            val tagEnd = scanXmlTag(text, i, limit, p, out)
                            i = tagEnd
                        }
                    }
                }
                ch == '&' -> {
                    var e = i + 1
                    var steps = 0
                    while (e < limit && steps < 12 && !text[e].isWhitespace() && text[e] != ';') {
                        e++
                        steps++
                    }
                    if (e < limit && text[e] == ';') {
                        out.add(i, e + 1, p.number)
                        i = e + 1
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
    }

    private fun scanXmlTag(
        text: CharSequence,
        start: Int,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ): Int {
        var i = start + 1
        var bracketEnd = i
        if (i < limit && (text[i] == '/' || text[i] == '?' || text[i] == '!')) {
            bracketEnd++
        }
        out.add(start, bracketEnd, p.punctuation)
        i = bracketEnd
        while (i < limit && text[i].isWhitespace()) i++
        // Tag name
        val nameStart = i
        while (i < limit && !text[i].isWhitespace() && text[i] != '>' && text[i] != '/' &&
            text[i] != '?'
        ) {
            i++
        }
        if (i > nameStart) {
            val isDirective = start + 1 < limit && (text[start + 1] == '?' || text[start + 1] == '!')
            out.add(nameStart, i, if (isDirective) p.controlKeyword else p.keyword)
        }
        // Attributes until `>`
        while (i < limit) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '>' -> {
                    out.add(i, i + 1, p.punctuation)
                    return i + 1
                }
                (c == '/' || c == '?') && i + 1 < limit && text[i + 1] == '>' -> {
                    out.add(i, i + 2, p.punctuation)
                    return i + 2
                }
                c == '"' || c == '\'' -> {
                    val strEnd = scanQuoted(text, i, limit, c, doubleEscapeSingle = false)
                    out.add(i, strEnd, p.string)
                    i = strEnd
                }
                c == '=' -> {
                    out.add(i, i + 1, p.punctuation)
                    i++
                }
                c == '<' -> return i // Unclosed tag safety
                else -> {
                    val attrStart = i
                    while (i < limit && !text[i].isWhitespace() && text[i] != '=' &&
                        text[i] != '>' && text[i] != '/' && text[i] != '"' && text[i] != '\''
                    ) {
                        i++
                    }
                    if (i > attrStart) {
                        out.add(attrStart, i, p.property)
                    } else {
                        i++
                    }
                }
            }
        }
        return limit
    }

    // --- Unified Code Scanner ---

    private fun scanCode(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        spec: CodeLanguageSpec,
        out: TokenCollector
    ) {
        var i = 0
        var lineStart = 0
        while (i < limit && !out.isFull) {
            out.tick()
            val ch = text[i]
            if (ch == '\n') {
                lineStart = i + 1
                i++
                continue
            }
            if (ch.isWhitespace()) {
                i++
                continue
            }

            // Line comments
            val lc1 = spec.lineComment
            if (lc1 != null && startsWithAt(text, i, limit, lc1)) {
                // Lua `--[[` is block comment, check blockCommentStart first!
                val bStart = spec.blockCommentStart
                val bEnd = spec.blockCommentEnd
                if (bStart != null && bEnd != null && startsWithAt(text, i, limit, bStart)) {
                    val end = findBlockCommentEnd(text, i + bStart.length, limit, bEnd)
                    out.add(i, end, p.comment, italic = true)
                    i = end
                } else {
                    val end = findLineEnd(text, i + lc1.length, limit)
                    out.add(i, end, p.comment, italic = true)
                    i = end
                }
                continue
            }
            val lc2 = spec.secondaryLineComment
            if (lc2 != null && startsWithAt(text, i, limit, lc2, ignoreCase = spec.caseInsensitiveKeywords)) {
                val end = findLineEnd(text, i + lc2.length, limit)
                out.add(i, end, p.comment, italic = true)
                i = end
                continue
            }
            // Block comments
            val bStart = spec.blockCommentStart
            val bEnd = spec.blockCommentEnd
            if (bStart != null && bEnd != null && startsWithAt(text, i, limit, bStart)) {
                val end = findBlockCommentEnd(text, i + bStart.length, limit, bEnd)
                out.add(i, end, p.comment, italic = true)
                i = end
                continue
            }

            // Preprocessor (#include / #define / #region)
            if (spec.supportsPreprocessor && ch == '#' && isLineLeadingWhitespace(text, lineStart, i)) {
                var e = i + 1
                while (e < limit && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                out.add(i, e, p.controlKeyword)
                i = e
                continue
            }

            // Triple-quoted strings
            if (spec.supportsTripleQuotes && (ch == '"' || ch == '\'') &&
                i + 2 < limit && text[i + 1] == ch && text[i + 2] == ch
            ) {
                val marker = if (ch == '"') "\"\"\"" else "'''"
                val end = findBlockCommentEnd(text, i + 3, limit, marker)
                out.add(i, end, p.string)
                i = end
                continue
            }

            // Rust lifetimes ('a, 'static)
            if (spec.supportsRustLifetimes && ch == '\'' && i + 1 < limit && isIdentStart(text[i + 1])) {
                val afterId = scanIdentifier(text, i + 1, limit)
                if (afterId >= limit || text[afterId] != '\'') {
                    out.add(i, afterId, p.type, italic = true)
                    i = afterId
                    continue
                }
            }

            // Quoted strings / chars
            if (ch == '"' || ch == '\'') {
                val end = scanQuoted(text, i, limit, ch, doubleEscapeSingle = false)
                out.add(i, end, p.string)
                i = end
                continue
            }

            // Backtick strings / identifiers
            if (spec.supportsBacktickStrings && ch == '`') {
                var e = i + 1
                while (e < limit) {
                    if (text[e] == '\\' && e + 1 < limit) {
                        e += 2
                        continue
                    }
                    if (text[e] == '`') {
                        e++
                        break
                    }
                    e++
                }
                out.add(i, e, p.string)
                i = e
                continue
            }

            // Annotations / Decorators / CSS at-rules
            if (spec.supportsAnnotations && ch == '@' && i + 1 < limit && isIdentStart(text[i + 1])) {
                val end = scanIdentifier(text, i + 1, limit)
                out.add(i, end, if (spec.supportsCssSyntax) p.controlKeyword else p.function)
                i = end
                continue
            }

            // Shell / PowerShell / PHP / Batch variables
            if (spec.supportsShellVars && ch == '$' && i + 1 < limit) {
                if (text[i + 1] == '{') {
                    val close = text.indexOf('}', i + 2).takeIf { it in (i + 2) until limit }
                    val end = if (close != null) close + 1 else i + 2
                    out.add(i, end, p.property)
                    i = end
                    continue
                } else if (isIdentStart(text[i + 1]) || text[i + 1].isDigit() ||
                    text[i + 1] in "?$#@*!"
                ) {
                    val end = if (isIdentStart(text[i + 1])) {
                        scanIdentifier(text, i + 1, limit)
                    } else {
                        i + 2
                    }
                    out.add(i, end, p.property)
                    i = end
                    continue
                }
            }

            // CSS selectors (#id, hex colors #fff, properties prop:, units 16px)
            if (spec.supportsCssSyntax && ch == '#') {
                val end = scanIdentifierOrHex(text, i + 1, limit)
                val token = text.subSequence(i + 1, end).toString()
                val isHexColor = token.length in setOf(3, 4, 6, 8) && token.all { it.isHexDigit() }
                out.add(i, end, if (isHexColor) p.number else p.type)
                i = end
                continue
            }

            // Numbers
            if (ch.isDigit() || (ch == '.' && i + 1 < limit && text[i + 1].isDigit())) {
                val end = scanNumber(text, i, limit)
                out.add(i, end, p.number)
                i = end
                continue
            }

            // Identifiers & keywords
            if (isIdentStart(ch) || (spec.supportsCssSyntax && ch == '-' && i + 1 < limit &&
                    isIdentStart(text[i + 1]))
            ) {
                val end = if (spec.supportsCssSyntax) {
                    scanCssIdentifier(text, i, limit)
                } else {
                    scanIdentifier(text, i, limit)
                }
                val raw = text.subSequence(i, end).toString()
                val lookup = if (spec.caseInsensitiveKeywords) raw.uppercase() else raw
                var peek = end
                while (peek < limit && (text[peek] == ' ' || text[peek] == '\t')) peek++

                when {
                    spec.supportsCssSyntax && peek < limit && text[peek] == ':' &&
                        (peek + 1 >= limit || text[peek + 1] != ':') ->
                        out.add(i, end, p.property)
                    lookup in spec.controlKeywords -> out.add(i, end, p.controlKeyword)
                    lookup in spec.keywords -> out.add(i, end, p.keyword)
                    lookup in spec.types -> out.add(i, end, p.type)
                    peek < limit && text[peek] == '(' -> out.add(i, end, p.function)
                    raw[0].isUpperCase() && raw.any { it.isLowerCase() } -> out.add(i, end, p.type)
                }
                i = end
                continue
            }

            if (ch in "{}[]();,.:") {
                out.add(i, i + 1, p.punctuation)
            }
            i++
        }
    }

    // --- Markdown ---

    private fun scanMarkdown(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = 0
        var inFence = false
        while (i < limit && !out.isFull) {
            out.tick()
            val lineStart = i
            var lineEnd = i
            while (lineEnd < limit && text[lineEnd] != '\n') lineEnd++

            var idx = lineStart
            while (idx < lineEnd && (text[idx] == ' ' || text[idx] == '\t')) idx++

            if (startsWithAt(text, idx, lineEnd, "```") ||
                startsWithAt(text, idx, lineEnd, "~~~")
            ) {
                inFence = !inFence
                out.add(idx, lineEnd, p.type, bold = true)
            } else if (inFence) {
                out.add(lineStart, lineEnd, p.string)
            } else if (idx < lineEnd) {
                val c = text[idx]
                when {
                    c == '#' -> out.add(idx, lineEnd, p.controlKeyword, bold = true)
                    c == '>' -> out.add(idx, lineEnd, p.comment, italic = true)
                    (c == '-' || c == '*' || c == '+') && idx + 1 < lineEnd &&
                        text[idx + 1].isWhitespace() -> {
                        out.add(idx, idx + 1, p.keyword, bold = true)
                        scanMarkdownInline(text, idx + 1, lineEnd, p, out)
                    }
                    else -> scanMarkdownInline(text, idx, lineEnd, p, out)
                }
            }
            i = lineEnd + 1
        }
    }

    private fun scanMarkdownInline(
        text: CharSequence,
        start: Int,
        end: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = start
        while (i < end && !out.isFull) {
            when (text[i]) {
                '`' -> {
                    val close = indexOfCharInLine(text, i + 1, end, '`')
                    if (close > i) {
                        out.add(i, close + 1, p.string)
                        i = close + 1
                    } else {
                        i++
                    }
                }
                '*', '_' -> {
                    val marker = text[i]
                    val isDouble = i + 1 < end && text[i + 1] == marker
                    val step = if (isDouble) 2 else 1
                    var j = i + step
                    var found = -1
                    while (j + step - 1 < end) {
                        if (text[j] == marker && (!isDouble || text[j + 1] == marker)) {
                            found = j + step
                            break
                        }
                        j++
                    }
                    if (found > i + step) {
                        out.add(i, found, if (isDouble) p.function else p.type, bold = isDouble, italic = !isDouble)
                        i = found
                    } else {
                        i += step
                    }
                }
                '[' -> {
                    val closeBracket = indexOfCharInLine(text, i + 1, end, ']')
                    if (closeBracket > i && closeBracket + 1 < end && text[closeBracket + 1] == '(') {
                        val closeParen = indexOfCharInLine(text, closeBracket + 2, end, ')')
                        if (closeParen > closeBracket + 1) {
                            out.add(i, closeBracket + 1, p.keyword)
                            out.add(closeBracket + 1, closeParen + 1, p.string)
                            i = closeParen + 1
                            continue
                        }
                    }
                    i++
                }
                else -> i++
            }
        }
    }

    // --- Diff & Log ---

    private fun scanDiff(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = 0
        while (i < limit && !out.isFull) {
            out.tick()
            val lineStart = i
            var lineEnd = i
            while (lineEnd < limit && text[lineEnd] != '\n') lineEnd++
            if (lineEnd > lineStart) {
                when {
                    startsWithAt(text, lineStart, lineEnd, "+++") ||
                        startsWithAt(text, lineStart, lineEnd, "---") ||
                        startsWithAt(text, lineStart, lineEnd, "diff ") ||
                        startsWithAt(text, lineStart, lineEnd, "index ") ->
                        out.add(lineStart, lineEnd, p.keyword, bold = true)
                    startsWithAt(text, lineStart, lineEnd, "@@") ->
                        out.add(lineStart, lineEnd, p.type)
                    text[lineStart] == '+' -> out.add(lineStart, lineEnd, p.diffAdded)
                    text[lineStart] == '-' -> out.add(lineStart, lineEnd, p.error)
                }
            }
            i = lineEnd + 1
        }
    }

    private fun scanLog(
        text: CharSequence,
        limit: Int,
        p: SyntaxPalette,
        out: TokenCollector
    ) {
        var i = 0
        while (i < limit && !out.isFull) {
            out.tick()
            val ch = text[i]
            when {
                ch.isDigit() -> {
                    val e = scanNumber(text, i, limit)
                    out.add(i, e, p.number)
                    i = e
                }
                ch == '"' -> {
                    val e = scanQuoted(text, i, limit, '"', doubleEscapeSingle = false)
                    out.add(i, e, p.string)
                    i = e
                }
                isIdentStart(ch) -> {
                    val e = scanIdentifier(text, i, limit)
                    val word = text.subSequence(i, e).toString().uppercase()
                    when (word) {
                        "ERROR", "FATAL", "EXCEPTION", "FAIL", "FAILED", "CRITICAL", "PANIC" ->
                            out.add(i, e, p.error, bold = true)
                        "WARN", "WARNING" -> out.add(i, e, p.function, bold = true)
                        "INFO", "NOTICE", "OK", "SUCCESS" -> out.add(i, e, p.diffAdded)
                        "DEBUG", "TRACE", "VERBOSE" -> out.add(i, e, p.comment)
                    }
                    i = e
                }
                else -> i++
            }
        }
    }

    // --- Scanning Primitives (O(N), non-backtracking) ---

    private fun scanQuoted(
        text: CharSequence,
        start: Int,
        limit: Int,
        quote: Char,
        doubleEscapeSingle: Boolean
    ): Int {
        var i = start + 1
        while (i < limit) {
            val ch = text[i]
            if (ch == '\n' && quote != '`') {
                return i
            }
            if (ch == '\\' && !(doubleEscapeSingle && quote == '\'') && i + 1 < limit) {
                i += 2
                continue
            }
            if (ch == quote) {
                if (doubleEscapeSingle && quote == '\'' && i + 1 < limit && text[i + 1] == '\'') {
                    i += 2
                    continue
                }
                return i + 1
            }
            i++
        }
        return limit
    }

    private fun scanNumber(text: CharSequence, start: Int, limit: Int): Int {
        var i = start
        if (text[i] == '-') i++
        if (i + 1 < limit && text[i] == '0' && (text[i + 1] == 'x' || text[i + 1] == 'X' ||
                text[i + 1] == 'b' || text[i + 1] == 'B' || text[i + 1] == 'o' || text[i + 1] == 'O')
        ) {
            i += 2
        }
        while (i < limit) {
            val c = text[i]
            if (c.isLetterOrDigit() || c == '_' || c == '.' || c == '+' || c == '-' || c == '%') {
                if ((c == '+' || c == '-') && i > start && text[i - 1] != 'e' && text[i - 1] != 'E') {
                    break
                }
                i++
            } else {
                break
            }
        }
        return i
    }

    private fun scanIdentifier(text: CharSequence, start: Int, limit: Int): Int {
        var i = start + 1
        while (i < limit && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        return i
    }

    private fun scanCssIdentifier(text: CharSequence, start: Int, limit: Int): Int {
        var i = start + 1
        while (i < limit && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
        return i
    }

    private fun scanIdentifierOrHex(text: CharSequence, start: Int, limit: Int): Int {
        var i = start
        while (i < limit && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '-')) i++
        return i
    }

    private fun findLineEnd(text: CharSequence, start: Int, limit: Int): Int {
        var i = start
        while (i < limit && text[i] != '\n') i++
        return i
    }

    private fun findBlockCommentEnd(
        text: CharSequence,
        start: Int,
        limit: Int,
        endMarker: String
    ): Int {
        val mLen = endMarker.length
        var i = start
        while (i + mLen <= limit) {
            if (startsWithAt(text, i, limit, endMarker)) {
                return i + mLen
            }
            i++
        }
        return limit
    }

    private fun startsWithAt(
        text: CharSequence,
        offset: Int,
        limit: Int,
        prefix: String,
        ignoreCase: Boolean = false
    ): Boolean {
        val pLen = prefix.length
        if (offset < 0 || offset + pLen > limit) return false
        for (j in 0 until pLen) {
            if (!text[offset + j].equals(prefix[j], ignoreCase = ignoreCase)) return false
        }
        return true
    }

    private fun isLineLeadingWhitespace(text: CharSequence, lineStart: Int, pos: Int): Boolean {
        for (k in lineStart until pos) {
            if (!text[k].isWhitespace()) return false
        }
        return true
    }

    private fun indexOfCharInLine(text: CharSequence, start: Int, end: Int, target: Char): Int {
        for (i in start until end) {
            if (text[i] == target) return i
        }
        return -1
    }

    private fun isIdentStart(ch: Char): Boolean = ch.isLetter() || ch == '_'

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun isNumericLiteral(s: String): Boolean {
        if (s.isEmpty()) return false
        var hasDigit = false
        var dots = 0
        for (idx in s.indices) {
            val c = s[idx]
            when {
                c in '0'..'9' -> hasDigit = true
                c == '.' -> if (++dots > 3) return false
                c == '-' && idx == 0 -> {}
                (c == 'x' || c == 'X' || c in 'a'..'f' || c in 'A'..'F') &&
                    s.startsWith("0x", ignoreCase = true) -> {}
                else -> return false
            }
        }
        return hasDigit && (dots <= 1 || dots == 3)
    }

    // --- Keyword Tables ---

    private val YAML_BOOL_OR_NULL = setOf("true", "false", "yes", "no", "on", "off", "null", "~")
    private val JSON_KEYWORDS = setOf("true", "false", "null")

    private val kotlinSpec = CodeLanguageSpec(
        supportsTripleQuotes = true,
        supportsBacktickStrings = true,
        keywords = setOf(
            "package", "import", "val", "var", "fun", "class", "interface", "object", "enum",
            "sealed", "data", "inner", "open", "abstract", "final", "override", "private",
            "protected", "public", "internal", "companion", "lateinit", "by", "const", "suspend",
            "inline", "noinline", "crossinline", "reified", "operator", "infix", "tailrec",
            "external", "annotation", "expect", "actual", "typealias", "constructor", "init",
            "get", "set", "field", "it", "this", "super", "true", "false", "null", "is", "as",
            "in", "out", "vararg", "where", "dynamic"
        ),
        controlKeywords = setOf(
            "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw",
            "return", "break", "continue"
        ),
        types = setOf(
            "Any", "Unit", "Nothing", "Boolean", "Byte", "Short", "Int", "Long", "Float", "Double",
            "Char", "String", "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
            "Pair", "Triple", "Result", "Sequence", "Flow"
        )
    )

    private val javaSpec = CodeLanguageSpec(
        supportsTripleQuotes = true,
        keywords = setOf(
            "package", "import", "class", "interface", "enum", "record", "extends", "implements",
            "public", "private", "protected", "static", "final", "abstract", "native",
            "synchronized", "transient", "volatile", "strictfp", "sealed", "non-sealed", "permits",
            "new", "this", "super", "instanceof", "true", "false", "null", "var", "void",
            "boolean", "byte", "short", "int", "long", "float", "double", "char", "default"
        ),
        controlKeywords = setOf(
            "if", "else", "switch", "case", "for", "while", "do", "try", "catch", "finally",
            "throw", "throws", "return", "break", "continue", "assert", "yield"
        ),
        types = setOf(
            "Object", "String", "Integer", "Long", "Boolean", "Double", "Float", "List", "Map",
            "Set", "Optional", "Override", "Deprecated", "SuppressWarnings"
        )
    )

    private val jsSpec = CodeLanguageSpec(
        supportsBacktickStrings = true,
        keywords = setOf(
            "var", "let", "const", "function", "class", "extends", "constructor", "static", "get",
            "set", "async", "await", "new", "this", "super", "typeof", "instanceof", "in", "of",
            "delete", "void", "true", "false", "null", "undefined", "NaN", "Infinity", "debugger"
        ),
        controlKeywords = setOf(
            "import", "export", "from", "as", "default", "if", "else", "switch", "case", "for",
            "while", "do", "try", "catch", "finally", "throw", "return", "break", "continue",
            "yield"
        ),
        types = setOf(
            "Array", "Object", "String", "Number", "Boolean", "Promise", "Map", "Set", "WeakMap",
            "WeakSet", "RegExp", "Date", "Error", "JSON", "Math", "console", "window", "document"
        )
    )

    private val tsSpec = jsSpec.copy(
        keywords = jsSpec.keywords + setOf(
            "interface", "type", "enum", "implements", "private", "protected", "public", "readonly",
            "abstract", "declare", "namespace", "module", "keyof", "infer", "is", "asserts",
            "satisfies", "override"
        ),
        types = jsSpec.types + setOf(
            "any", "unknown", "never", "string", "number", "boolean", "symbol", "bigint",
            "object", "Record", "Partial", "Required", "Readonly", "Pick", "Omit"
        )
    )

    private val cCppSpec = CodeLanguageSpec(
        supportsPreprocessor = true,
        supportsAnnotations = false,
        keywords = setOf(
            "auto", "bool", "char", "class", "const", "constexpr", "consteval", "constinit",
            "decltype", "default", "delete", "double", "enum", "explicit", "export", "extern",
            "false", "float", "friend", "inline", "int", "long", "mutable", "namespace", "new",
            "noexcept", "nullptr", "NULL", "operator", "private", "protected", "public",
            "register", "short", "signed", "sizeof", "static", "static_assert", "struct",
            "template", "this", "thread_local", "true", "typedef", "typeid", "typename", "union",
            "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "override", "final",
            "concept", "requires"
        ),
        controlKeywords = setOf(
            "if", "else", "switch", "case", "for", "while", "do", "goto", "break", "continue",
            "return", "try", "catch", "throw", "co_await", "co_return", "co_yield"
        ),
        types = setOf(
            "size_t", "int8_t", "uint8_t", "int16_t", "uint16_t", "int32_t", "uint32_t",
            "int64_t", "uint64_t", "string", "vector", "map", "set", "unique_ptr", "shared_ptr"
        )
    )

    private val csharpSpec = CodeLanguageSpec(
        supportsPreprocessor = true,
        keywords = setOf(
            "abstract", "as", "base", "bool", "byte", "char", "checked", "class", "const",
            "decimal", "delegate", "double", "dynamic", "enum", "event", "explicit", "extern",
            "false", "fixed", "float", "implicit", "in", "int", "interface", "internal", "is",
            "lock", "long", "namespace", "new", "null", "object", "operator", "out", "override",
            "params", "private", "protected", "public", "readonly", "record", "ref", "sbyte",
            "sealed", "short", "sizeof", "stackalloc", "static", "string", "struct", "this",
            "true", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort", "using", "var",
            "virtual", "void", "volatile", "async", "await", "init", "required"
        ),
        controlKeywords = setOf(
            "if", "else", "switch", "case", "default", "for", "foreach", "while", "do", "break",
            "continue", "goto", "return", "yield", "throw", "try", "catch", "finally", "when"
        ),
        types = setOf("Task", "List", "Dictionary", "IEnumerable", "String", "Console", "Exception")
    )

    private val goSpec = CodeLanguageSpec(
        supportsAnnotations = false,
        supportsBacktickStrings = true,
        keywords = setOf(
            "package", "import", "func", "var", "const", "type", "struct", "interface", "map",
            "chan", "true", "false", "nil", "iota"
        ),
        controlKeywords = setOf(
            "if", "else", "switch", "case", "default", "select", "for", "range", "go", "defer",
            "return", "break", "continue", "goto", "fallthrough"
        ),
        types = setOf(
            "bool", "byte", "complex64", "complex128", "error", "float32", "float64", "int",
            "int8", "int16", "int32", "int64", "rune", "string", "uint", "uint8", "uint16",
            "uint32", "uint64", "uintptr", "any", "comparable", "len", "cap", "make", "new",
            "append", "copy", "close", "delete", "panic", "recover"
        )
    )

    private val rustSpec = CodeLanguageSpec(
        supportsAnnotations = false,
        supportsRustLifetimes = true,
        keywords = setOf(
            "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "type",
            "mod", "pub", "use", "crate", "super", "self", "Self", "as", "where", "unsafe",
            "async", "await", "dyn", "move", "ref", "true", "false", "extern"
        ),
        controlKeywords = setOf(
            "if", "else", "match", "loop", "while", "for", "in", "break", "continue", "return"
        ),
        types = setOf(
            "bool", "char", "str", "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32",
            "u64", "u128", "usize", "f32", "f64", "String", "Vec", "Option", "Result", "Box", "Rc",
            "Arc", "Some", "None", "Ok", "Err"
        )
    )

    private val swiftSpec = CodeLanguageSpec(
        supportsTripleQuotes = true,
        keywords = setOf(
            "import", "let", "var", "func", "class", "struct", "enum", "protocol", "extension",
            "typealias", "init", "deinit", "subscript", "operator", "precedencegroup",
            "associatedtype", "public", "private", "fileprivate", "internal", "open", "static",
            "final", "override", "mutating", "async", "await", "throws", "rethrows", "self",
            "Self", "super", "nil", "true", "false", "is", "as", "inout", "lazy", "weak",
            "unowned", "some", "any", "actor"
        ),
        controlKeywords = setOf(
            "if", "else", "guard", "switch", "case", "default", "for", "in", "while", "repeat",
            "break", "continue", "fallthrough", "return", "throw", "do", "try", "catch", "defer",
            "where"
        ),
        types = setOf(
            "Int", "Double", "Float", "Bool", "String", "Character", "Array", "Dictionary", "Set",
            "Optional", "Result", "Void", "Any", "AnyObject"
        )
    )

    private val dartSpec = CodeLanguageSpec(
        supportsTripleQuotes = true,
        keywords = setOf(
            "import", "export", "library", "part", "show", "hide", "as", "abstract", "class",
            "extends", "implements", "with", "mixin", "enum", "extension", "typedef", "var",
            "final", "const", "late", "static", "external", "factory", "operator", "get", "set",
            "async", "sync", "await", "yield", "this", "super", "new", "null", "true", "false",
            "is", "void", "dynamic", "required", "covariant", "sealed", "base", "interface"
        ),
        controlKeywords = setOf(
            "if", "else", "switch", "case", "default", "for", "in", "while", "do", "break",
            "continue", "return", "throw", "rethrow", "try", "on", "catch", "finally", "assert"
        ),
        types = setOf(
            "int", "double", "num", "bool", "String", "List", "Map", "Set", "Future", "Stream",
            "Object", "Widget", "BuildContext"
        )
    )

    private val pythonSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsTripleQuotes = true,
        keywords = setOf(
            "def", "class", "lambda", "global", "nonlocal", "del", "and", "or", "not", "in", "is",
            "as", "async", "await", "True", "False", "None", "self", "cls"
        ),
        controlKeywords = setOf(
            "import", "from", "if", "elif", "else", "match", "case", "for", "while", "break",
            "continue", "pass", "return", "yield", "try", "except", "finally", "raise", "with",
            "assert"
        ),
        types = setOf(
            "int", "float", "str", "bool", "list", "dict", "set", "tuple", "bytes", "object",
            "len", "range", "print", "enumerate", "zip", "map", "filter", "isinstance", "Exception"
        )
    )

    private val rubySpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = "=begin",
        blockCommentEnd = "=end",
        keywords = setOf(
            "def", "class", "module", "end", "alias", "and", "or", "not", "self", "super", "true",
            "false", "nil", "defined?"
        ),
        controlKeywords = setOf(
            "require", "require_relative", "include", "extend", "if", "elsif", "else", "unless",
            "case", "when", "for", "in", "while", "until", "break", "next", "redo", "return",
            "yield", "begin", "rescue", "ensure", "raise"
        ),
        types = setOf("puts", "print", "attr_reader", "attr_writer", "attr_accessor")
    )

    private val phpSpec = CodeLanguageSpec(
        lineComment = "//",
        secondaryLineComment = "#",
        supportsShellVars = true,
        keywords = setOf(
            "function", "class", "interface", "trait", "enum", "extends", "implements", "public",
            "private", "protected", "static", "final", "abstract", "readonly", "const", "var",
            "global", "new", "clone", "instanceof", "true", "false", "null", "echo", "print",
            "isset", "empty", "unset", "array", "callable", "mixed", "void", "self", "parent", "fn"
        ),
        controlKeywords = setOf(
            "namespace", "use", "require", "require_once", "include", "include_once", "if",
            "elseif", "else", "switch", "case", "default", "match", "for", "foreach", "as",
            "while", "do", "break", "continue", "return", "yield", "try", "catch", "finally",
            "throw"
        ),
        types = setOf("string", "int", "float", "bool", "object", "iterable")
    )

    private val luaSpec = CodeLanguageSpec(
        lineComment = "--",
        blockCommentStart = "--[[",
        blockCommentEnd = "]]",
        supportsAnnotations = false,
        keywords = setOf("local", "function", "end", "and", "or", "not", "true", "false", "nil"),
        controlKeywords = setOf(
            "if", "then", "elseif", "else", "for", "in", "while", "repeat", "until", "do",
            "break", "goto", "return"
        ),
        types = setOf("require", "print", "pairs", "ipairs", "table", "string", "math")
    )

    private val perlSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsAnnotations = false,
        supportsShellVars = true,
        supportsBacktickStrings = true,
        keywords = setOf("sub", "my", "our", "local", "package", "use", "require", "undef"),
        controlKeywords = setOf(
            "if", "elsif", "else", "unless", "for", "foreach", "while", "until", "last", "next",
            "return", "die", "warn"
        ),
        types = setOf("print", "say", "chomp", "split", "join", "push", "pop")
    )

    private val groovySpec = CodeLanguageSpec(
        supportsTripleQuotes = true,
        keywords = javaSpec.keywords + setOf("def", "in", "as", "trait", "it"),
        controlKeywords = javaSpec.controlKeywords,
        types = javaSpec.types + setOf(
            "dependencies", "implementation", "api", "testImplementation", "android", "plugins"
        )
    )

    private val scalaSpec = CodeLanguageSpec(
        supportsTripleQuotes = true,
        keywords = setOf(
            "package", "import", "val", "var", "def", "class", "trait", "object", "case", "sealed",
            "abstract", "final", "override", "private", "protected", "implicit", "given", "using",
            "extends", "with", "new", "this", "super", "true", "false", "null", "type", "lazy",
            "enum"
        ),
        controlKeywords = setOf(
            "if", "else", "match", "for", "yield", "while", "do", "try", "catch", "finally",
            "throw", "return"
        ),
        types = setOf(
            "Any", "Unit", "Int", "Long", "Double", "Boolean", "String", "Option", "Some", "None",
            "List", "Seq", "Map", "Set", "Future", "Try"
        )
    )

    private val rSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsAnnotations = false,
        keywords = setOf("function", "TRUE", "FALSE", "NULL", "NA", "NaN", "Inf", "in"),
        controlKeywords = setOf("library", "require", "if", "else", "for", "while", "repeat", "break", "next", "return"),
        types = setOf("c", "list", "data.frame", "matrix", "print", "summary")
    )

    private val protoSpec = CodeLanguageSpec(
        supportsAnnotations = false,
        keywords = setOf(
            "syntax", "package", "import", "option", "message", "enum", "service", "rpc",
            "returns", "repeated", "optional", "required", "oneof", "map", "reserved", "stream",
            "true", "false"
        ),
        types = setOf(
            "double", "float", "int32", "int64", "uint32", "uint64", "sint32", "sint64",
            "fixed32", "fixed64", "sfixed32", "sfixed64", "bool", "string", "bytes"
        )
    )

    private val graphqlSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsTripleQuotes = true,
        keywords = setOf(
            "query", "mutation", "subscription", "fragment", "on", "type", "interface", "union",
            "enum", "input", "scalar", "schema", "directive", "extend", "implements", "true",
            "false", "null"
        ),
        types = setOf("Int", "Float", "String", "Boolean", "ID")
    )

    private val shellSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsAnnotations = false,
        supportsBacktickStrings = true,
        supportsShellVars = true,
        keywords = setOf(
            "export", "local", "readonly", "declare", "typeset", "unset", "function", "alias",
            "source", "true", "false"
        ),
        controlKeywords = setOf(
            "if", "then", "elif", "else", "fi", "case", "esac", "for", "in", "while", "until",
            "do", "done", "select", "break", "continue", "return", "exit"
        ),
        types = setOf(
            "echo", "printf", "read", "cd", "pwd", "ls", "cp", "mv", "rm", "mkdir", "chmod",
            "chown", "grep", "sed", "awk", "find", "cat", "test"
        )
    )

    private val powershellSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = "<#",
        blockCommentEnd = "#>",
        supportsAnnotations = false,
        supportsBacktickStrings = true,
        supportsShellVars = true,
        caseInsensitiveKeywords = true,
        keywords = setOf(
            "FUNCTION", "PARAM", "BEGIN", "PROCESS", "END", "FILTER", "CLASS", "ENUM", "USING",
            "TRUE", "FALSE", "NULL"
        ),
        controlKeywords = setOf(
            "IF", "ELSEIF", "ELSE", "SWITCH", "FOR", "FOREACH", "IN", "WHILE", "DO", "UNTIL",
            "BREAK", "CONTINUE", "RETURN", "TRY", "CATCH", "FINALLY", "THROW", "TRAP"
        ),
        types = setOf("WRITE-OUTPUT", "WRITE-HOST", "GET-CONTENT", "SET-CONTENT", "SELECT-STRING")
    )

    private val batchSpec = CodeLanguageSpec(
        lineComment = "::",
        secondaryLineComment = "REM ",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsAnnotations = false,
        caseInsensitiveKeywords = true,
        keywords = setOf("SET", "SETLOCAL", "ENDLOCAL", "ECHO", "OFF", "ON", "DEFINED", "EXIST", "NOT", "EQU", "NEQ"),
        controlKeywords = setOf("IF", "ELSE", "FOR", "IN", "DO", "GOTO", "CALL", "EXIT"),
        types = setOf("CD", "DIR", "COPY", "MOVE", "DEL", "MKDIR", "PAUSE")
    )

    private val dockerfileSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsAnnotations = false,
        supportsShellVars = true,
        caseInsensitiveKeywords = true,
        keywords = setOf(
            "FROM", "AS", "ARG", "ENV", "LABEL", "USER", "WORKDIR", "EXPOSE", "VOLUME", "STOPSIGNAL"
        ),
        controlKeywords = setOf(
            "RUN", "CMD", "ENTRYPOINT", "COPY", "ADD", "ONBUILD", "HEALTHCHECK", "SHELL"
        )
    )

    private val makefileSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
        supportsAnnotations = false,
        supportsShellVars = true,
        keywords = setOf("include", "export", "override", "define", "endef", "vpath"),
        controlKeywords = setOf("ifeq", "ifneq", "ifdef", "ifndef", "else", "endif")
    )

    private val cmakeSpec = CodeLanguageSpec(
        lineComment = "#",
        blockCommentStart = "#[[",
        blockCommentEnd = "]]",
        supportsAnnotations = false,
        supportsShellVars = true,
        caseInsensitiveKeywords = true,
        keywords = setOf(
            "CMAKE_MINIMUM_REQUIRED", "PROJECT", "SET", "OPTION", "ADD_EXECUTABLE", "ADD_LIBRARY",
            "TARGET_LINK_LIBRARIES", "TARGET_INCLUDE_DIRECTORIES", "FIND_PACKAGE", "MESSAGE",
            "ON", "OFF", "TRUE", "FALSE"
        ),
        controlKeywords = setOf("IF", "ELSEIF", "ELSE", "ENDIF", "FOREACH", "ENDFOREACH", "WHILE", "ENDWHILE", "FUNCTION", "ENDFUNCTION", "RETURN")
    )

    private val cssSpec = CodeLanguageSpec(
        lineComment = "//",
        supportsCssSyntax = true,
        keywords = setOf(
            "!important", "inherit", "initial", "unset", "none", "auto", "block", "inline",
            "flex", "grid", "absolute", "relative", "fixed", "sticky", "solid", "dashed",
            "center", "transparent", "true", "false"
        ),
        controlKeywords = setOf("media", "import", "keyframes", "supports", "layer", "mixin", "include"),
        types = setOf("px", "dp", "sp", "em", "rem", "vh", "vw", "ms", "s", "deg")
    )

    private val sqlSpec = CodeLanguageSpec(
        lineComment = "--",
        secondaryLineComment = "#",
        supportsAnnotations = false,
        supportsBacktickStrings = true,
        caseInsensitiveKeywords = true,
        keywords = setOf(
            "SELECT", "DISTINCT", "FROM", "WHERE", "AS", "JOIN", "INNER", "LEFT", "RIGHT", "FULL",
            "OUTER", "CROSS", "ON", "USING", "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC",
            "LIMIT", "OFFSET", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE",
            "ALTER", "DROP", "TRUNCATE", "TABLE", "VIEW", "INDEX", "DATABASE", "SCHEMA", "IF",
            "NOT", "EXISTS", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "CHECK",
            "DEFAULT", "AUTO_INCREMENT", "AUTOINCREMENT", "AND", "OR", "IN", "IS", "NULL",
            "LIKE", "BETWEEN", "CASE", "WHEN", "THEN", "ELSE", "END", "UNION", "ALL", "TRUE",
            "FALSE", "PRAGMA", "EXPLAIN", "BEGIN", "COMMIT", "ROLLBACK"
        ),
        types = setOf(
            "INT", "INTEGER", "BIGINT", "SMALLINT", "FLOAT", "DOUBLE", "REAL", "DECIMAL",
            "NUMERIC", "BOOLEAN", "CHAR", "VARCHAR", "TEXT", "BLOB", "DATE", "TIME", "DATETIME",
            "TIMESTAMP", "JSON", "COUNT", "SUM", "AVG", "MIN", "MAX", "COALESCE"
        )
    )

    private val codeSpecs = mapOf(
        SyntaxLanguage.KOTLIN to kotlinSpec,
        SyntaxLanguage.JAVA to javaSpec,
        SyntaxLanguage.JAVASCRIPT to jsSpec,
        SyntaxLanguage.TYPESCRIPT to tsSpec,
        SyntaxLanguage.C_CPP to cCppSpec,
        SyntaxLanguage.CSHARP to csharpSpec,
        SyntaxLanguage.GO to goSpec,
        SyntaxLanguage.RUST to rustSpec,
        SyntaxLanguage.SWIFT to swiftSpec,
        SyntaxLanguage.DART to dartSpec,
        SyntaxLanguage.PYTHON to pythonSpec,
        SyntaxLanguage.RUBY to rubySpec,
        SyntaxLanguage.PHP to phpSpec,
        SyntaxLanguage.LUA to luaSpec,
        SyntaxLanguage.PERL to perlSpec,
        SyntaxLanguage.GROOVY to groovySpec,
        SyntaxLanguage.SCALA to scalaSpec,
        SyntaxLanguage.R to rSpec,
        SyntaxLanguage.PROTOBUF to protoSpec,
        SyntaxLanguage.GRAPHQL to graphqlSpec,
        SyntaxLanguage.SHELL to shellSpec,
        SyntaxLanguage.POWERSHELL to powershellSpec,
        SyntaxLanguage.BATCH to batchSpec,
        SyntaxLanguage.DOCKERFILE to dockerfileSpec,
        SyntaxLanguage.MAKEFILE to makefileSpec,
        SyntaxLanguage.CMAKE to cmakeSpec,
        SyntaxLanguage.CSS to cssSpec,
        SyntaxLanguage.SQL to sqlSpec
    )
}

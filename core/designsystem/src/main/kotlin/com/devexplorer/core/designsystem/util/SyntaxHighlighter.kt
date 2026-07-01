package com.devexplorer.core.designsystem.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** Colors a highlighter paints with — sourced from the M3 theme at the call site. */
data class HighlightColors(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
)

/** Languages we understand well enough to tokenize; everything else is Generic. */
enum class CodeLanguage(
    val lineComment: String?,
    val blockCommentStart: String?,
    val blockCommentEnd: String?,
    val allowSingleQuote: Boolean,
    private val keywords: Set<String>,
) {
    Kotlin("//", "/*", "*/", true, KOTLIN_KEYWORDS),
    Java("//", "/*", "*/", true, JAVA_KEYWORDS),
    Xml(null, "<!--", "-->", false, emptySet()),
    Json(null, null, null, false, setOf("true", "false", "null")),
    Generic("//", "/*", "*/", true, emptySet());

    fun isKeyword(word: String): Boolean = word in keywords

    companion object {
        fun fromFileName(name: String): CodeLanguage = when (name.substringAfterLast('.').lowercase()) {
            "kt", "kts" -> Kotlin
            "java" -> Java
            "xml", "html", "htm" -> Xml
            "json" -> Json
            else -> Generic
        }
    }
}

/**
 * A tiny, dependency-free syntax highlighter. It runs a single left-to-right scan
 * (not overlapping regexes), classifying comments, strings, numbers and keywords,
 * and emits an [AnnotatedString] with a color span per token. Approximate by
 * design — it's a readable code *viewer*, not a compiler — but robust: every
 * branch advances the cursor, so it can't loop, and unknown input just renders
 * as plain text.
 */
fun highlightCode(
    code: String,
    language: CodeLanguage,
    colors: HighlightColors,
): AnnotatedString = buildAnnotatedString {
    val n = code.length
    var i = 0
    while (i < n) {
        val c = code[i]

        // Block comment
        val bStart = language.blockCommentStart
        val bEnd = language.blockCommentEnd
        if (bStart != null && bEnd != null && code.startsWith(bStart, i)) {
            val found = code.indexOf(bEnd, i + bStart.length)
            val stop = if (found < 0) n else found + bEnd.length
            token(code.substring(i, stop), colors.comment)
            i = stop
            continue
        }

        // Line comment
        val lc = language.lineComment
        if (lc != null && code.startsWith(lc, i)) {
            val nl = code.indexOf('\n', i)
            val stop = if (nl < 0) n else nl
            token(code.substring(i, stop), colors.comment)
            i = stop
            continue
        }

        // String / char literal
        if (c == '"' || (language.allowSingleQuote && c == '\'')) {
            var j = i + 1
            while (j < n) {
                when (code[j]) {
                    '\\' -> j += 2
                    c -> { j++; break }
                    '\n' -> break
                    else -> j++
                }
            }
            val stop = if (j > n) n else j
            token(code.substring(i, stop), colors.string)
            i = stop
            continue
        }

        // Number
        if (c.isDigit()) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
            token(code.substring(i, j), colors.number)
            i = j
            continue
        }

        // Identifier / keyword
        if (c.isLetter() || c == '_') {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            val word = code.substring(i, j)
            token(word, if (language.isKeyword(word)) colors.keyword else colors.plain)
            i = j
            continue
        }

        // Anything else
        token(c.toString(), colors.plain)
        i++
    }
}

private fun AnnotatedString.Builder.token(text: String, color: Color) {
    withStyle(SpanStyle(color = color)) { append(text) }
}

private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
    "in", "interface", "is", "null", "object", "package", "return", "super", "this",
    "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
    "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
    "get", "import", "init", "param", "property", "receiver", "set", "setparam",
    "value", "where", "abstract", "actual", "annotation", "companion", "const",
    "crossinline", "data", "enum", "expect", "external", "final", "infix", "inline",
    "inner", "internal", "lateinit", "noinline", "open", "operator", "out", "override",
    "private", "protected", "public", "reified", "sealed", "suspend", "tailrec", "vararg",
)

private val JAVA_KEYWORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
    "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    "interface", "long", "native", "new", "package", "private", "protected", "public",
    "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
)

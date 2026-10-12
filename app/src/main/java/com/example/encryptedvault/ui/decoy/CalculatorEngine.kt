package com.example.encryptedvault.ui.decoy

import java.util.Locale
import kotlin.math.abs

/**
 * The maths behind the calculator disguise. Pure Kotlin (no Android) so it is covered by unit tests.
 * An expression is a plain string such as `12+3×4`; `%` is a postfix "divide by 100"; `−` is a real minus sign.
 */
object CalculatorEngine {
    private const val PLUS = '+'
    private const val MINUS = '−'
    private const val TIMES = '×'
    private const val DIVIDE = '÷'
    private const val PERCENT = '%'
    private val OPERATORS = setOf(PLUS, MINUS, TIMES, DIVIDE)

    /** Applies one key press to [expression] and returns the new expression. `=` is handled by the caller. */
    fun press(
        expression: String,
        key: Char,
    ): String =
        when {
            key.isDigit() -> appendDigit(expression, key)
            key == '.' -> appendDecimalPoint(expression)
            key in OPERATORS -> appendOperator(expression, key)
            key == PERCENT -> if (expression.lastOrNull()?.isDigit() == true) expression + PERCENT else expression
            key == '⌫' -> expression.dropLast(1)
            key == 'C' -> ""
            key == '±' -> toggleSign(expression)
            else -> expression
        }

    /** True if [expression] looks like a PIN entry: only digits, between [minLength] and [maxLength] of them. */
    fun looksLikePin(
        expression: String,
        minLength: Int,
        maxLength: Int,
    ): Boolean = expression.length in minLength..maxLength && expression.all { it.isDigit() }

    /** Evaluates [expression] with normal operator precedence; null if it is incomplete, invalid or divides by zero. */
    fun evaluate(expression: String): Double? {
        val tokens = tokenize(expression) ?: return null
        val output = ArrayDeque<Double>()
        val operators = ArrayDeque<Char>()

        /** Pops one operator and two operands and pushes the result. False on a malformed stack or a division by zero. */
        fun apply(): Boolean {
            val op = operators.removeLastOrNull() ?: return false
            val b = output.removeLastOrNull() ?: return false
            val a = output.removeLastOrNull() ?: return false
            output.addLast(
                when (op) {
                    PLUS -> a + b
                    MINUS -> a - b
                    TIMES -> a * b
                    else -> if (b == 0.0) return false else a / b
                },
            )
            return true
        }
        for (token in tokens) {
            if (token is Double) {
                output.addLast(token)
            } else {
                val op = token as Char
                while (operators.isNotEmpty() && precedence(operators.last()) >= precedence(op)) if (!apply()) return null
                operators.addLast(op)
            }
        }
        while (operators.isNotEmpty()) if (!apply()) return null
        return output.singleOrNull()
    }

    /** `7`, `0.5`, `1234567`, `3.3333333333`; no trailing zeros and no exponent for everyday values. */
    fun format(value: Double): String =
        when {
            value.isNaN() || value.isInfinite() -> "Error"
            value == Math.rint(value) && abs(value) < 1e15 -> value.toLong().toString()
            else ->
                String
                    .format(Locale.US, "%.10f", value)
                    .trimEnd('0')
                    .trimEnd('.')
                    .ifEmpty { "0" }
        }

    // ---- input rules ------------------------------------------------------------------------

    /** Appends a digit, replacing a lone leading zero (`05` becomes `5`). */
    private fun appendDigit(
        expression: String,
        digit: Char,
    ): String {
        val number = currentNumber(expression)
        // "0" followed by a digit replaces the zero ("05" -> "5").
        return if (number == "0") expression.dropLast(1) + digit else expression + digit
    }

    /** Adds a decimal point once per number; a bare `.` becomes `0.`. */
    private fun appendDecimalPoint(expression: String): String {
        val number = currentNumber(expression)
        return when {
            '.' in number -> expression
            number.isEmpty() -> expression + "0."
            else -> "$expression."
        }
    }

    /** Appends an operator or replaces the previous one. At the start only a minus is allowed (negative number). */
    private fun appendOperator(
        expression: String,
        op: Char,
    ): String =
        when {
            expression.isEmpty() -> if (op == MINUS) "$MINUS" else expression // only a leading minus makes sense
            expression.last() in OPERATORS -> if (expression.length == 1) expression else expression.dropLast(1) + op
            expression.last() == '.' -> expression
            else -> expression + op
        }

    /** Flips the sign of the number being typed (the last one). */
    private fun toggleSign(expression: String): String {
        val number = currentNumber(expression)
        if (number.isEmpty()) return expression
        val start = expression.length - number.length
        val before = expression.getOrNull(start - 1)
        val isUnary = before == MINUS && (start - 1 == 0 || expression[start - 2] in OPERATORS)
        return if (isUnary) expression.removeRange(start - 1, start) else expression.substring(0, start) + MINUS + number
    }

    /** The digits/decimal point at the end of [expression] (without a trailing %). */
    private fun currentNumber(expression: String): String = expression.takeLastWhile { it.isDigit() || it == '.' }

    // ---- evaluation helpers -----------------------------------------------------------------

    /** Multiplication and division bind tighter than addition and subtraction. */
    private fun precedence(op: Char) = if (op == TIMES || op == DIVIDE) 2 else 1

    /** Splits into numbers (Double) and operators (Char). Handles unary minus and postfix %. */
    private fun tokenize(expression: String): List<Any>? {
        if (expression.isEmpty()) return null
        val tokens = mutableListOf<Any>()
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            val expectsNumber = tokens.isEmpty() || tokens.last() is Char
            when {
                c == MINUS && expectsNumber -> { // unary minus belongs to the number that follows
                    val (value, next) = readNumber(expression, i + 1) ?: return null
                    tokens += -value
                    i = next
                }
                c.isDigit() || c == '.' -> {
                    val (value, next) = readNumber(expression, i) ?: return null
                    tokens += value
                    i = next
                }
                c in OPERATORS && !expectsNumber -> {
                    tokens += c
                    i++
                }
                else -> return null
            }
        }
        return if (tokens.last() is Double) tokens else null
    }

    /** Reads `123.45` (optionally followed by `%`) starting at [start]; returns the value and the next index. */
    private fun readNumber(
        expression: String,
        start: Int,
    ): Pair<Double, Int>? {
        var end = start
        while (end < expression.length && (expression[end].isDigit() || expression[end] == '.')) end++
        var value = expression.substring(start, end).toDoubleOrNull() ?: return null
        while (end < expression.length && expression[end] == PERCENT) {
            value /= 100
            end++
        }
        return value to end
    }
}

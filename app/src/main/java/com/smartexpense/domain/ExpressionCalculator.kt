package com.smartexpense.domain

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

internal class CalculationProblem(message: String) : IllegalArgumentException(message)
internal data class CalcNumber(val value: BigDecimal, val approximate: Boolean = false)
internal val calcContext = MathContext(34)
internal fun problem(message: String): Nothing = throw CalculationProblem(message)
internal fun decimalText(value: BigDecimal): String {
    val stripped = value.stripTrailingZeros()
    return if (stripped.precision() - stripped.scale() > 100 || stripped.scale() > 100) stripped.toEngineeringString() else stripped.toPlainString()
}
internal fun bounded(value: BigDecimal): BigDecimal {
    if (value.precision() > 512 || abs(value.scale()) > 512 || value.precision() - value.scale() > 301)
        problem("That result is too large. Use smaller numbers or exponents.")
    return value
}
internal fun divideNumbers(a: CalcNumber, b: CalcNumber): CalcNumber {
    if (b.value.signum() == 0) problem("Division by zero is undefined. Choose a nonzero divisor.")
    return try { CalcNumber(bounded(a.value.divide(b.value)), a.approximate || b.approximate) }
    catch (_: ArithmeticException) { CalcNumber(bounded(a.value.divide(b.value, calcContext)), true) }
}

/** Bounded arithmetic AST. Never executes user code or uses eval/reflection. */
internal object ExpressionCalculator {
    private sealed interface Node {
        data class Number(val number: BigDecimal) : Node
        data class Symbol(val name: String) : Node
        data class Unary(val op: String, val node: Node) : Node
        data class Binary(val op: String, val left: Node, val right: Node) : Node
        data class Function(val name: String, val argument: Node) : Node
    }
    private class Parser(val source: String) {
        private val tokens = mutableListOf<String>()
        private var index = 0
        private var depth = 0
        init {
            val lexer = Regex("(?:[0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[a-z_]+|[+*/^%!()=-]")
            var position = 0
            while (position < source.length) {
                if (source[position].isWhitespace()) { position++; continue }
                val match = lexer.find(source, position)
                if (match == null || match.range.first != position) problem("I could not read part of that expression. Use numbers, operators and parentheses.")
                tokens += match.value
                if (tokens.size > 256) problem("That expression is too long. Calculate it in smaller parts.")
                position = match.range.last + 1
            }
        }
        private fun peek() = tokens.getOrNull(index)
        private fun take(): String = tokens.getOrNull(index++) ?: problem("What number comes next? Complete the expression.")
        fun parse(): Node {
            val result = sum()
            if (index != tokens.size) problem("I found extra input. Add an operator or clarify the calculation.")
            return result
        }
        private fun sum(): Node {
            var result = product()
            while (peek() in listOf("+", "-")) { val op = take(); result = Node.Binary(op, result, product()) }
            return result
        }
        private fun product(): Node {
            var result = unary()
            while (true) {
                val next = peek()
                if (next in listOf("*", "/", "mod")) { val op = take(); result = Node.Binary(op, result, unary()) }
                else if (next == "(" || next?.firstOrNull()?.isLetter() == true) result = Node.Binary("*", result, unary())
                else return result
            }
        }
        private fun unary(): Node {
            if (++depth > 32) problem("Too many nested operations. Simplify the expression.")
            val result = when (peek()) {
                "+" -> { take(); unary() }
                "-" -> { take(); Node.Unary("-", unary()) }
                else -> power()
            }
            depth--
            return result
        }
        private fun power(): Node {
            var result = primary()
            while (peek() in listOf("%", "!")) result = Node.Unary(take(), result)
            if (peek() == "^") { take(); result = Node.Binary("^", result, unary()) }
            return result
        }
        private fun primary(): Node {
            val token = take()
            if (token == "(") {
                val result = sum()
                if (take() != ")") problem("Close the parentheses before calculating.")
                return result
            }
            if (token.first().isDigit() || token.first() == '.') {
                if (token.contains(',')) {
                    val integer = token.substringBefore('.').substringBefore('e')
                    if (!Regex("(?:[1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]?(?:,[0-9]{2})*,[0-9]{3})").matches(integer))
                        problem("Check the comma grouping, for example 1,000 or 1,00,000.")
                }
                val clean = token.replace(",", "")
                if (Regex("[eE]([+-]?[0-9]+)").find(clean)?.groupValues?.get(1)?.toIntOrNull()?.let { abs(it) > 100 } == true)
                    problem("Use a scientific exponent between -100 and 100.")
                val number = try { BigDecimal(clean) } catch (_: NumberFormatException) { problem("Check that number.") }
                return Node.Number(bounded(number))
            }
            if (token in listOf("x", "pi", "e")) return Node.Symbol(token)
            if (token.first().isLetter()) {
                if (take() != "(") problem("Use a named function with parentheses, for example sqrt(144).")
                val argument = sum()
                if (take() != ")") problem("Close the function parentheses.")
                return Node.Function(token, argument)
            }
            problem("A number or expression is missing.")
        }
    }

    fun evaluate(expression: String): CalcNumber = evaluate(Parser(expression).parse())
    private fun evaluate(node: Node): CalcNumber = when (node) {
        is Node.Number -> CalcNumber(node.number)
        is Node.Symbol -> when (node.name) {
            "pi" -> CalcNumber(BigDecimal("3.141592653589793238462643383279503"), true)
            "e" -> CalcNumber(BigDecimal("2.718281828459045235360287471352662"), true)
            else -> problem("Use an equation such as 2*x + 3 = 11 to solve for x.")
        }
        is Node.Unary -> {
            val a = evaluate(node.node)
            when (node.op) {
                "-" -> a.copy(value = a.value.negate())
                "%" -> a.copy(value = a.value.movePointLeft(2))
                else -> {
                    val n = try { a.value.intValueExact() } catch (_: ArithmeticException) { problem("Factorial needs a whole number from 0 to 100.") }
                    if (n !in 0..100) problem("Factorial needs a whole number from 0 to 100.")
                    CalcNumber((1..n).fold(BigDecimal.ONE) { total, value -> total.multiply(value.toBigDecimal()) }, a.approximate)
                }
            }
        }
        is Node.Binary -> {
            val a = evaluate(node.left); val b = evaluate(node.right)
            val approximate = a.approximate || b.approximate
            when (node.op) {
                "+" -> CalcNumber(bounded(a.value.add(b.value)), approximate)
                "-" -> CalcNumber(bounded(a.value.subtract(b.value)), approximate)
                "*" -> CalcNumber(bounded(a.value.multiply(b.value)), approximate)
                "/" -> divideNumbers(a, b)
                "mod" -> {
                    if (b.value.signum() == 0) problem("Modulo needs a nonzero divisor.")
                    CalcNumber(a.value.remainder(b.value), approximate)
                }
                else -> {
                    val n = try { b.value.intValueExact() } catch (_: ArithmeticException) { null }
                    if (n != null) {
                        if (n !in -100..100 || a.value.precision().toLong() * abs(n) > 512) problem("Use a smaller exponent, between -100 and 100.")
                        if (n == 0 && a.value.signum() == 0) problem("0^0 is ambiguous. Clarify the expression.")
                        val powered = CalcNumber(bounded(a.value.pow(abs(n))), approximate)
                        if (n < 0) divideNumbers(CalcNumber(BigDecimal.ONE), powered) else powered
                    } else approximate(a.value.toDouble().pow(b.value.toDouble()))
                }
            }
        }
        is Node.Function -> {
            val a = evaluate(node.argument)
            val d = a.value.toDouble()
            when (node.name) {
                "abs" -> a.copy(value = a.value.abs())
                "sqrt" -> {
                    if (a.value.signum() < 0) problem("A negative square root needs complex numbers, which are not supported here.")
                    if (a.value.signum() == 0) CalcNumber(BigDecimal.ZERO, a.approximate) else {
                        var root = BigDecimal.valueOf(sqrt(d))
                        repeat(12) { root = root.add(a.value.divide(root, calcContext)).divide(BigDecimal(2), calcContext) }
                        CalcNumber(root, a.approximate || root.multiply(root).compareTo(a.value) != 0)
                    }
                }
                "ln" -> approximate(ln(d))
                "log" -> approximate(log10(d))
                "exp" -> approximate(exp(d))
                "sin_deg" -> approximate(sin(Math.toRadians(d)))
                "cos_deg" -> approximate(cos(Math.toRadians(d)))
                "tan_deg" -> { if (abs(cos(Math.toRadians(d))) < 1e-14) problem("Tangent is undefined at this angle."); approximate(tan(Math.toRadians(d))) }
                "sin_rad" -> approximate(sin(d))
                "cos_rad" -> approximate(cos(d))
                "tan_rad" -> { if (abs(cos(d)) < 1e-14) problem("Tangent is undefined at this angle."); approximate(tan(d)) }
                "sin", "cos", "tan" -> problem("Which angle unit? Use sin_deg(30) or sin_rad(pi/2), and similarly for cos/tan.")
                else -> problem("That function is not supported yet. Try sqrt, abs, log, ln, exp or explicit degree/radian trigonometry.")
            }
        }
    }
    private fun approximate(value: Double): CalcNumber {
        if (!value.isFinite()) problem("That operation has no finite real answer in the supported range.")
        return CalcNumber(bounded(BigDecimal.valueOf(value).round(MathContext(15))), true)
    }

    fun solve(equation: String): String {
        val sides = equation.split('=')
        if (sides.size != 2) problem("Use one equals sign, for example 2*x + 3 = 11.")
        fun polynomial(node: Node): List<BigDecimal> = when (node) {
            is Node.Number -> listOf(node.number, BigDecimal.ZERO, BigDecimal.ZERO)
            is Node.Symbol -> if (node.name == "x") listOf(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO) else polynomial(Node.Number(evaluate(node).value))
            is Node.Unary -> {
                val p = polynomial(node.node)
                when (node.op) { "-" -> p.map { it.negate() }; "%" -> p.map { it.movePointLeft(2) }; else -> problem("Factorials of expressions containing x are not supported.") }
            }
            is Node.Function -> problem("For equations, use linear or quadratic polynomial expressions in x.")
            is Node.Binary -> {
                val left = polynomial(node.left); val right = polynomial(node.right)
                when (node.op) {
                    "+", "-" -> left.indices.map { if (node.op == "+") left[it] + right[it] else left[it] - right[it] }
                    "*" -> multiplyPolynomial(left, right)
                    "/" -> {
                        if (right.drop(1).any { it.signum() != 0 }) problem("Division by expressions containing x is not supported.")
                        left.map {
                            val quotient = divideNumbers(CalcNumber(it), CalcNumber(right[0]))
                            if (quotient.approximate) problem("Rewrite this equation without repeating-decimal coefficient division, for example x = 3 instead of x/3 = 1.")
                            quotient.value
                        }
                    }
                    "^" -> {
                        if (right.drop(1).any { it.signum() != 0 }) problem("Use a constant whole-number exponent in equations.")
                        val n = try { right[0].intValueExact() } catch (_: ArithmeticException) { problem("Use x or x^2 in equations.") }
                        if (n !in 0..2) problem("This calculator solves linear and quadratic equations only.")
                        (0 until n).fold(listOf(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)) { acc, _ -> multiplyPolynomial(acc, left) }
                    }
                    else -> problem("Modulo is not supported in equations.")
                }
            }
        }
        val left = polynomial(Parser(sides[0]).parse()); val right = polynomial(Parser(sides[1]).parse())
        val c = bounded(left[0] - right[0]); val b = bounded(left[1] - right[1]); val a = bounded(left[2] - right[2])
        if (a.signum() == 0) {
            if (b.signum() == 0) return if (c.signum() == 0) "All real x satisfy this equation" else "No solution"
            return "x = ${decimalText(divideNumbers(CalcNumber(c.negate()), CalcNumber(b)).value)}"
        }
        val discriminant = bounded(b.multiply(b).subtract(BigDecimal(4).multiply(a).multiply(c)))
        if (discriminant.signum() < 0) return "No real solution"
        val root = evaluate(Node.Function("sqrt", Node.Number(discriminant))).value
        val denominator = BigDecimal(2).multiply(a)
        val roots = if (root.signum() == 0) listOf(divideNumbers(CalcNumber(b.negate()), CalcNumber(denominator)).value)
        else {
            // Stable quadratic formula avoids subtracting nearly equal large numbers.
            val q = b.add(if (b.signum() >= 0) root else root.negate()).multiply(BigDecimal("-0.5"))
            listOf(divideNumbers(CalcNumber(q), CalcNumber(a)).value, divideNumbers(CalcNumber(c), CalcNumber(q)).value)
        }.distinctBy(::decimalText).sorted()
        return roots.joinToString(" or ") { "x = ${decimalText(it)}" }
    }
    private fun multiplyPolynomial(a: List<BigDecimal>, b: List<BigDecimal>): List<BigDecimal> {
        val result = MutableList(5) { BigDecimal.ZERO }
        for (i in a.indices) for (j in b.indices) result[i + j] = bounded(result[i + j].add(a[i].multiply(b[j])))
        if (result.drop(3).any { it.signum() != 0 }) problem("This calculator solves linear and quadratic equations only.")
        return result.take(3)
    }
}

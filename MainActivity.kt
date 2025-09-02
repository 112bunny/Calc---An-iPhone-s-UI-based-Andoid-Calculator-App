package com.example.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.ui.theme.CalculatorTheme
import com.example.calculator.ui.theme.DarkGray
import com.example.calculator.ui.theme.LightGray
import com.example.calculator.ui.theme.Orange
import java.util.Locale
import java.util.ArrayDeque
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CalculatorScreen()
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen() {
    var expression by rememberSaveable { mutableStateOf("0") }
    var justEvaluated by rememberSaveable { mutableStateOf(false) }

    fun setExpr(newValue: String) {
        expression = newValue.take(16) // Prevent extreme length
    }

    val animatedFontSize by animateFloatAsState(
        targetValue = when {
            expression.length > 12 -> 30f
            expression.length > 8 -> 38f
            else -> 48f
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 24.dp)
    ) {
        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = expression,
                fontSize = animatedFontSize.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Right,
                lineHeight = (animatedFontSize * 1.2f).sp,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(16.dp))

        // Button grid
        val rows = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { label ->
                        val buttonType = when (label) {
                            in "0".."9", "." -> ButtonType.NUMBER
                            "C", "±", "%", "⌫" -> ButtonType.ACTION
                            "=" -> ButtonType.EQUALS
                            else -> ButtonType.OPERATOR
                        }

                        val weight = if (label == "0") 2f else 1f

                        CalcButton(
                            text = label,
                            type = buttonType,
                            onClick = {
                                val startNewExpression = justEvaluated && label in "0123456789."
                                if (startNewExpression) {
                                    setExpr(if (label == ".") "0." else label)
                                    justEvaluated = false
                                    return@CalcButton
                                }
                                val nextExpression = when (label) {
                                    "C" -> "0"
                                    "⌫" -> CalcEngine.backspace(expression)
                                    "±" -> CalcEngine.toggleSign(expression)
                                    "%" -> CalcEngine.applyPercent(expression)
                                    "=" -> {
                                        val cleaned = CalcEngine.prepareForEval(expression)
                                        val result = CalcEngine.evaluateToString(cleaned)
                                        justEvaluated = result != "Error"
                                        result
                                    }
                                    in "0".."9", "." -> CalcEngine.appendDigitOrDot(expression, label.first())
                                    "+", "−", "×", "÷" -> CalcEngine.appendOperator(expression, label.first())
                                    else -> expression
                                }
                                if (label != "=") {
                                    justEvaluated = false
                                }
                                setExpr(nextExpression)
                            },
                            modifier = Modifier
                                .weight(weight)
                                .aspectRatio(if (label == "0") 2.1f else 1f)
                        )

                        if (label == "0") Spacer(modifier = Modifier.weight(0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun CalcButton(
    text: String,
    type: ButtonType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(targetValue = if (isPressed) 0.9f else 1f)

    val (backgroundColor, textColor) = when (type) {
        ButtonType.NUMBER -> DarkGray to Color.White
        ButtonType.OPERATOR -> Orange to Color.White
        ButtonType.ACTION -> LightGray to Color.Black
        ButtonType.EQUALS -> Orange to Color.White
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = textColor)
    }
}

private object CalcEngine {
    private val displayOps = setOf('+', '−', '×', '÷')

    private fun isOperator(ch: Char): Boolean = ch in displayOps

    fun appendDigitOrDot(expr: String, ch: Char): String {
        if (expr == "0") {
            return if (ch == '.') "0." else ch.toString()
        }
        if (expr == "-0") {
            return if (ch == '.') "-0." else "-$ch"
        }

        val (start, end) = lastNumberBounds(expr)
        val currentNumber = expr.substring(start, end)

        if (ch == '.' && currentNumber.contains('.')) {
            return expr
        }

        if (expr.isNotEmpty() && isOperator(expr.last()) && ch == '.') {
            return expr + "0."
        }

        return expr + ch
    }

    fun appendOperator(expr: String, op: Char): String {
        var s = expr
        if (s.endsWith('.')) {
            s = s.dropLast(1)
        }
        if (s.isNotEmpty() && isOperator(s.last())) {
            return s.dropLast(1) + op
        }
        return s + op
    }

    fun backspace(expr: String): String {
        if (expr.length == 1) return "0"
        return expr.dropLast(1)
    }

    fun toggleSign(expr: String): String {
        val (start, end) = lastNumberBounds(expr)
        if (start == 0) {
            return if (expr.startsWith('-')) expr.removePrefix("-") else "-$expr"
        }

        val before = expr.substring(0, start)
        val current = expr.substring(start, end)

        return if (current.startsWith('−')) {
            if (isOperator(before.last())) {
                "${before.dropLast(1)}+${current.removePrefix("−")}"
            } else {
                "$before${current.removePrefix("−")}"
            }
        } else {
            if (isOperator(before.last())) {
                "${before.dropLast(1)}−$current"
            } else {
                "$before−$current"
            }
        }
    }

    fun applyPercent(expr: String): String {
        val (start, end) = lastNumberBounds(expr)
        if (start == end) return expr

        val current = expr.substring(start, end).replace('−', '-')
        val value = current.toDoubleOrNull() ?: return expr
        val percentValue = value / 100.0

        val before = expr.substring(0, start)
        return before + formatNumber(percentValue)
    }

    fun prepareForEval(expr: String): String {
        var s = expr
        while (s.isNotEmpty() && (isOperator(s.last()) || s.last() == '.')) {
            s = s.dropLast(1)
        }
        return s.ifEmpty { "0" }
    }

    fun evaluateToString(expr: String): String {
        val normalized = expr.replace('×', '*').replace('÷', '/').replace('−', '-')
        return try {
            val result = evaluate(normalized)
            if (!result.isFinite()) "Error" else formatNumber(result)
        } catch (_: Exception) {
            "Error"
        }
    }

    private fun evaluate(expr: String): Double {
        val tokens = tokenize(expr)
        val rpn = toRPN(tokens)
        return evalRPN(rpn)
    }

    private fun tokenize(expr: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            val isUnary = (i == 0 || expr[i - 1] in "*/+-")

            when {
                c.isDigit() || c == '.' || (c == '-' && isUnary) -> {
                    var j = i + 1
                    var hasDot = (c == '.')
                    while (j < expr.length) {
                        val ch = expr[j]
                        if (ch.isDigit()) { j++ }
                        else if (ch == '.' && !hasDot) { hasDot = true; j++ }
                        else break
                    }
                    out += expr.substring(i, j)
                    i = j
                }
                c in "+-*/" -> { out += c.toString(); i++ }
                else -> i++
            }
        }
        return out
    }

    @Suppress("SpellCheckingInspection")
    private fun toRPN(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        val ops = ArrayDeque<String>()
        fun prec(op: String) = if (op in "+-") 1 else if (op in "*/") 2 else 0

        for (t in tokens) {
            when {
                t.toDoubleOrNull() != null -> out += t
                t in "+-*/" -> {
                    while (ops.isNotEmpty() && prec(ops.first()) >= prec(t)) {
                        out += ops.removeFirst()
                    }
                    ops.addFirst(t)
                }
            }
        }
        while (ops.isNotEmpty()) out += ops.removeFirst()
        return out
    }

    private fun evalRPN(rpn: List<String>): Double {
        val stack = ArrayDeque<Double>()
        for (t in rpn) {
            val num = t.toDoubleOrNull()
            if (num != null) {
                stack.addFirst(num)
            } else {
                if (stack.size < 2) return Double.NaN
                val b = stack.removeFirst()
                val a = stack.removeFirst()
                val res = when (t) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (abs(b) < 1e-12) Double.NaN else a / b
                    else -> Double.NaN
                }
                stack.addFirst(res)
            }
        }
        return if (stack.size == 1) stack.removeFirst() else Double.NaN
    }

    private fun lastNumberBounds(expr: String): Pair<Int, Int> {
        if (expr.isEmpty()) return 0 to 0
        var i = expr.length - 1

        if (isOperator(expr[i])) return expr.length to expr.length

        while (i >= 0 && (expr[i].isDigit() || expr[i] == '.')) i--

        if (i >= 0 && expr[i] == '−' && (i == 0 || isOperator(expr[i - 1]))) {
            i--
        }
        return (i + 1) to expr.length
    }

    private fun formatNumber(value: Double): String {
        if (value.isInfinite() || value.isNaN()) return "Error"
        if (value == 0.0 && 1/value < 0) return "0"
        val raw = String.format(Locale.US, "%.10f", value)
        val trimmed = raw.trimEnd('0').trimEnd('.')
        return if (trimmed == "-0") "0" else trimmed
    }
}
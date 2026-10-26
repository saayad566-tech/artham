package `in`.artham.fundamentals.domain

/**
 * The screening query language.
 *
 *   sales > 1000 AND roe > 15
 *   opm BETWEEN 10 AND 30
 *   NOT debt_to_equity > 1
 *   (sales_growth_5y > 12 OR profit_growth_5y > 15) AND market_cap > 500
 *
 * Grammar (lowest to highest precedence):
 *   or      := and (OR and)*
 *   and     := not (AND not)*
 *   not     := NOT not | primary
 *   primary := '(' or ')' | comparison
 *   comparison := additive (('>'|'<'|'>='|'<='|'='|'!=') additive | BETWEEN additive AND additive)?
 *   additive := multiplicative (('+'|'-') multiplicative)*
 *   multiplicative := unary (('*'|'/') unary)*
 *   unary   := '-' unary | atom
 *   atom    := NUMBER | METRIC | '(' additive ')'
 */
sealed interface Expr {
    data class And(val left: Expr, val right: Expr) : Expr
    data class Or(val left: Expr, val right: Expr) : Expr
    data class Not(val inner: Expr) : Expr
    data class Compare(val left: Term, val op: String, val right: Term) : Expr
    data class Between(val term: Term, val low: Term, val high: Term) : Expr
    data class Truthy(val term: Term) : Expr
}

sealed interface Term {
    data class Literal(val value: Double) : Term
    data class Ref(val metricId: String) : Term
    data class Arithmetic(val op: Char, val left: Term, val right: Term) : Term
}

class QueryException(message: String, val position: Int = -1) : Exception(message)

private enum class Kind { NUMBER, IDENT, OP, AND, OR, NOT, BETWEEN, LPAREN, RPAREN, PLUS, MINUS, STAR, SLASH, EOF }

private data class Token(val kind: Kind, val text: String, val position: Int, val number: Double = 0.0)

private fun tokenize(input: String): List<Token> {
    val tokens = ArrayList<Token>()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c.isWhitespace() -> i++
            c.isDigit() || (c == '.' && i + 1 < input.length && input[i + 1].isDigit()) -> {
                val start = i
                while (i < input.length && (input[i].isDigit() || input[i] == '.' || input[i] == ',')) i++
                val text = input.substring(start, i).replace(",", "")
                val value = text.toDoubleOrNull() ?: throw QueryException("'$text' is not a number", start)
                tokens.add(Token(Kind.NUMBER, text, start, value))
            }
            c.isLetter() || c == '_' -> {
                val start = i
                while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_' || input[i] == '%')) i++
                val text = input.substring(start, i)
                val kind = when (text.uppercase()) {
                    "AND" -> Kind.AND
                    "OR" -> Kind.OR
                    "NOT" -> Kind.NOT
                    "BETWEEN" -> Kind.BETWEEN
                    else -> Kind.IDENT
                }
                tokens.add(Token(kind, text, start))
            }
            c == '>' || c == '<' || c == '=' || c == '!' -> {
                val start = i
                val two = if (i + 1 < input.length) input.substring(i, i + 2) else ""
                if (two == ">=" || two == "<=" || two == "!=" || two == "==") i += 2 else i++
                tokens.add(Token(Kind.OP, input.substring(start, i), start))
            }
            c == '(' -> { tokens.add(Token(Kind.LPAREN, "(", i)); i++ }
            c == ')' -> { tokens.add(Token(Kind.RPAREN, ")", i)); i++ }
            c == '+' -> { tokens.add(Token(Kind.PLUS, "+", i)); i++ }
            c == '-' -> { tokens.add(Token(Kind.MINUS, "-", i)); i++ }
            c == '*' -> { tokens.add(Token(Kind.STAR, "*", i)); i++ }
            c == '/' -> { tokens.add(Token(Kind.SLASH, "/", i)); i++ }
            else -> throw QueryException("Unexpected character '$c'", i)
        }
    }
    tokens.add(Token(Kind.EOF, "", input.length))
    return tokens
}

class QueryParser(private val source: String) {
    private val tokens = tokenize(source)
    private var index = 0
    private val referenced = LinkedHashSet<String>()

    private fun peek(): Token = tokens[index]
    private fun advance(): Token = tokens[index++]
    private fun match(kind: Kind): Boolean {
        if (peek().kind == kind) { index++; return true }
        return false
    }

    fun parse(): Pair<Expr, List<String>> {
        if (source.isBlank()) throw QueryException("The screen is empty. Add a condition such as: roe > 15")
        val expr = parseOr()
        if (peek().kind != Kind.EOF) {
            throw QueryException("Unexpected '${peek().text}' after a complete condition", peek().position)
        }
        return expr to referenced.toList()
    }

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (match(Kind.OR)) left = Expr.Or(left, parseAnd())
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseNot()
        while (match(Kind.AND)) left = Expr.And(left, parseNot())
        return left
    }

    private fun parseNot(): Expr {
        if (match(Kind.NOT)) return Expr.Not(parseNot())
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        if (match(Kind.LPAREN)) {
            val inner = parseOr()
            if (!match(Kind.RPAREN)) throw QueryException("Missing ')'", peek().position)
            return inner
        }
        val left = parseAdditive()
        val token = peek()
        if (token.kind == Kind.OP) {
            advance()
            val op = if (token.text == "==") "=" else token.text
            val right = parseAdditive()
            return Expr.Compare(left, op, right)
        }
        if (token.kind == Kind.BETWEEN) {
            advance()
            val low = parseAdditive()
            if (!match(Kind.AND)) throw QueryException("BETWEEN needs the form: metric BETWEEN low AND high", peek().position)
            val high = parseAdditive()
            return Expr.Between(left, low, high)
        }
        return Expr.Truthy(left)
    }

    private fun parseAdditive(): Term {
        var left = parseMultiplicative()
        while (peek().kind == Kind.PLUS || peek().kind == Kind.MINUS) {
            val op = advance().text[0]
            left = Term.Arithmetic(op, left, parseMultiplicative())
        }
        return left
    }

    private fun parseMultiplicative(): Term {
        var left = parseUnary()
        while (peek().kind == Kind.STAR || peek().kind == Kind.SLASH) {
            val op = advance().text[0]
            left = Term.Arithmetic(op, left, parseUnary())
        }
        return left
    }

    private fun parseUnary(): Term {
        if (match(Kind.MINUS)) return Term.Arithmetic('-', Term.Literal(0.0), parseUnary())
        return parseAtom()
    }

    private fun parseAtom(): Term {
        val token = peek()
        return when (token.kind) {
            Kind.NUMBER -> { advance(); Term.Literal(token.number) }
            Kind.IDENT -> {
                advance()
                val metric = MetricCatalog.resolve(token.text)
                    ?: throw QueryException(
                        "Unknown metric '${token.text}'" +
                            MetricCatalog.suggestions(token.text).let { s ->
                                if (s.isEmpty()) "" else ". Did you mean ${s.joinToString(" or ")}?"
                            },
                        token.position
                    )
                referenced.add(metric.id)
                Term.Ref(metric.id)
            }
            Kind.LPAREN -> {
                advance()
                val inner = parseAdditive()
                if (!match(Kind.RPAREN)) throw QueryException("Missing ')'", peek().position)
                inner
            }
            else -> throw QueryException("Expected a number or metric but found '${token.text.ifBlank { "end of query" }}'", token.position)
        }
    }
}

object QueryCompiler {
    fun compile(source: String): Pair<Expr, List<String>> = QueryParser(source).parse()
}

/** Evaluates the AST against one company, with the metric values supplied by the caller. */
object QueryEvaluator {

    fun eval(expr: Expr, values: Map<String, Double?>): Boolean = when (expr) {
        is Expr.And -> eval(expr.left, values) && eval(expr.right, values)
        is Expr.Or -> eval(expr.left, values) || eval(expr.right, values)
        is Expr.Not -> !eval(expr.inner, values)
        is Expr.Compare -> {
            val l = term(expr.left, values)
            val r = term(expr.right, values)
            if (l == null || r == null) false else when (expr.op) {
                ">" -> l > r
                "<" -> l < r
                ">=" -> l >= r
                "<=" -> l <= r
                "=" -> kotlin.math.abs(l - r) < 1e-9
                "!=" -> kotlin.math.abs(l - r) >= 1e-9
                else -> false
            }
        }
        is Expr.Between -> {
            val v = term(expr.term, values)
            val lo = term(expr.low, values)
            val hi = term(expr.high, values)
            if (v == null || lo == null || hi == null) false else v in lo..hi
        }
        is Expr.Truthy -> {
            val v = term(expr.term, values)
            v != null && v != 0.0
        }
    }

    fun term(t: Term, values: Map<String, Double?>): Double? = when (t) {
        is Term.Literal -> t.value
        is Term.Ref -> values[t.metricId]
        is Term.Arithmetic -> {
            val l = term(t.left, values)
            val r = term(t.right, values)
            if (l == null || r == null) null else when (t.op) {
                '+' -> l + r
                '-' -> l - r
                '*' -> l * r
                '/' -> if (r == 0.0) null else l / r
                else -> null
            }
        }
    }
}

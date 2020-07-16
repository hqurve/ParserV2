package com.hqurve.parsing



open class ParserGenerator<T, F>{
    fun empty() = EmptyParser<T, F>()
    fun any() = TokenParser<T, F>( TokenPredicate.any() )
    fun exact(requiredToken: Token) = TokenParser<T, F>( TokenPredicate.exact(requiredToken) )

    inner class WhitespaceSubGenerator{
        operator fun invoke() = TokenParser<T, F>( TokenPredicate.whitespace() )
    }
    val whitespace = WhitespaceSubGenerator()

    inner class LabelSubGenerator{
        operator fun invoke() = TokenParser<T, F>( TokenPredicate.label() )
        operator fun invoke(label: String) = exact(LabelToken(label))
    }
    val label = LabelSubGenerator()

    inner class StringSubGenerator{
        operator fun invoke() = TokenParser<T, F>( TokenPredicate.string() )
        fun mode(m: String) = TokenParser<T, F>( TokenPredicate.string(StringToken.Modes.valueOf(m.toUpperCase())) )
        fun strong() = TokenParser<T, F>( TokenPredicate.string(StringToken.Modes.STRONG))
        fun weak() = TokenParser<T, F>( TokenPredicate.string(StringToken.Modes.WEAK))
    }
    val string = StringSubGenerator()

    inner class NumberSubGenerator{
        operator fun invoke() = TokenParser<T, F>( TokenPredicate.number() )
        operator fun invoke(i: Long) = exact(NumberToken(i))
        operator fun invoke(i: Int) = exact(NumberToken(i.toLong()))
        operator fun invoke(d: Double) = exact(NumberToken(d))
        operator fun invoke(min: Long, max: Long) = TokenParser<T, F>( TokenPredicate.number(min, max) )
        operator fun invoke(min: Int, max: Int) = TokenParser<T, F>( TokenPredicate.number(min, max) )
        operator fun invoke(min: Double, max: Double) = TokenParser<T, F>( TokenPredicate.number(min, max) )
        fun integer() = TokenParser<T, F>( TokenPredicate.number(NumberToken.Modes.INTEGER) )
        fun decimal() = TokenParser<T, F>( TokenPredicate.number(NumberToken.Modes.DECIMAL) )
    }
    val number = NumberSubGenerator()

    inner class SymbolSubGenerator{
        operator fun invoke() = TokenParser<T, F>( TokenPredicate.symbol() )
        operator fun invoke(c: Char) = exact(SymbolToken(c))
        operator fun invoke(chars: Collection<Char>) = TokenParser<T, F>( TokenPredicate.symbol(chars) )
        operator fun invoke(vararg chars: Char) = TokenParser<T, F>(TokenPredicate.symbol(chars.toSet()))
    }
    val symbol = SymbolSubGenerator()
}
object Assist : ParserGenerator<Unit, Unit>()
inline fun <T, F> builder(block: ParserGenerator<T, F>.()->Parser<T, F>): Parser<T, F>{
    return ParserGenerator<T, F>().run(block)
}


operator fun <T, F> Parser<T, F>.rangeTo(other: Parser<T, F>) = SequentialParser(this, other)

infix fun <T, F> Parser<T, F>.or(other: Parser<T, F>) = BranchedParser(this, other)


operator fun <T, F> Parser<T, F>.times(quantifier: Quantifier)
        = QuantifiedParser(this, quantifier)
operator fun <T, F> Parser<T, F>.times(amt: Int) = this * q(amt, amt)

fun greedy(min: Int, max: Int) = Quantifier(min, max, Quantifier.Modes.GREEDY)
fun reluctant(min: Int, max: Int) = Quantifier(min, max, Quantifier.Modes.RELUCTANT)
fun possessive(min: Int, max: Int) = Quantifier(min, max, Quantifier.Modes.POSSESSIVE)
fun q(min: Int, max: Int = Int.MAX_VALUE) = greedy(min, max)

val maybe = greedy(0, 1)

operator fun Quantifier.unaryPlus() = asPossessive()
operator fun Quantifier.unaryMinus() = asReluctant()

fun <T, F> lz(initializer: ()->Parser<T, F>) = LazyParser(initializer)



fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.trans(flagsTransform: (Fo)->Fi, resultTransform: (Result<Ti>, Fo)->Result<To>)
        = TransformParser(this, flagsTransform, resultTransform)
fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.transValue(flagsTransform: (Fo)->Fi, resultTransform: (Result<Ti>, Fo)->To)
        = TransformParser(this, flagsTransform){results, flags -> ValueResult(resultTransform(results, flags))}

infix fun <Ti, To, F> Parser<Ti, F>.transResult(handler: (Result<Ti>, F)->Result<To>)
        = ResultTransformParser(this, handler)
infix fun <Ti, To, F> Parser<Ti, F>.transResultValue(handler: (Result<Ti>, F)->To)
        = ResultTransformParser(this){ results, flags -> ValueResult(handler(results, flags)) }


infix fun <T, Fi, Fo> Parser<T, Fi>.transFlags(handler: (Fo)->Fi)
        = FlagTransformParser(this, handler)




infix fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.fixed(handler: (Fo)->Result<To>)
        = FixedParser(this, handler)
infix fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.fixedValue(handler: (Fo) -> To)
        = FixedParser<To, Fo>(this){flags -> ValueResult(handler(flags)) }
infix fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.fixedValue(value: To)
        = FixedParser<To, Fo>(this){ ValueResult(value) }



class BiParserWrapper<Ta, Fa, Tb, Fb>: ParserGenerator<BiValue<Result<Ta>, Result<Tb>>, Pair<Fa, Fb>>(){
    fun a(aParser: Parser<Ta, Fa>) = BiParser.a<Ta, Fa, Tb, Fb>(aParser)
    fun b(bParser: Parser<Tb, Fb>) = BiParser.b<Ta, Fa, Tb, Fb>(bParser)
}

fun <Ta, Fa, Tb, Fb> biParserBuilder(block: BiParserWrapper<Ta, Fa, Tb, Fb>.()->Parser<BiValue<Result<Ta>, Result<Tb>>, Pair<Fa, Fb>>): Parser<BiValue<Result<Ta>, Result<Tb>>, Pair<Fa, Fb>>{
    return BiParserWrapper<Ta, Fa, Tb, Fb>().run(block)
}
package com.hqurve.parsing

interface Result<out T>{
    fun asCompound() = this as CompoundResult
    fun asValue() = (this as ValueResult).value
    fun <V: Token> asToken() = (this as TokenResult).token as V
}
data class CompoundResult<out T>(val subResults: List<Result<T>>): Result<T>, Iterable<Result<T>>{
    constructor(vararg subs: Result<T>): this(subs.toList())
    val size: Int
        get() = subResults.size

    fun get(index: Int) = subResults[index]
    fun isEmpty() = subResults.isEmpty()
    fun isNotEmpty() = !isEmpty()
    override operator fun iterator() = subResults.iterator()
    fun listIterator() = subResults.listIterator()
    fun subList(fromIndex: Int, toIndex: Int) = subResults.subList(fromIndex, toIndex)

    fun valueAt(index: Int) = get(index).asValue()
    fun <V: Token> tokenAt(index: Int) = get(index).asToken<V>()
    fun compoundAt(index: Int) = get(index) as CompoundResult
}
data class ValueResult<out T>(val value: T): Result<T>
data class TokenResult<out T>(val token: Token): Result<T>



abstract class Parser<out T, in F>{

    abstract fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>

    fun parse(tokens: List<Token>, flags: F): Result<T>?{
        val matcherInstance = createInstance(tokens, 0)
        while(matcherInstance.isMatching() && matcherInstance.end!! < tokens.size){
            matcherInstance.tryAgain()
        }
        return if (matcherInstance.isMatching()){
            matcherInstance.getResult(flags)
        }else{
            null
        }
    }

    abstract class MatcherInstance<out T, in F>(protected val tokens: List<Token>, val pos: Int){
        abstract val end: Int?

        fun isMatching() = end != null
        fun tryAgain(){//Safety function
            if (isMatching()) internalTryAgain()
        }

        protected abstract fun internalTryAgain()

        abstract fun getResult(flags: F): Result<T>
    }
}

internal class EmptyParser<out T>: Parser<T, Any?>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, Any?>(tokens, pos){
        override var end: Int? = pos

        override fun internalTryAgain() {
            end = null
        }

        override fun getResult(flags: Any?): Result<T> {
            return CompoundResult(emptyList())
        }
    }
    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, Any?> {
        return Instance(tokens, pos)
    }
}

class TokenParser<out T>(val tokenPredicate: TokenPredicate): Parser<T, Any?>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, Any?>(tokens, pos){
        override var end: Int? = if (pos < tokens.size && tokens[pos] matches tokenPredicate) pos + 1 else null
        override fun internalTryAgain() {
            end = null
        }
        override fun getResult(flags: Any?) = TokenResult<T>(tokens[pos])
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, Any?> {
        return Instance(tokens, pos)
    }
    class Generator<T>{
        fun any() = TokenParser<T>( TokenPredicate.any() )
        fun exact(requiredToken: Token) = TokenParser<T>( TokenPredicate.exact(requiredToken) )
        
        inner class WhitespaceSubGenerator{
            operator fun invoke() = TokenParser<T>( TokenPredicate.whitespace() )
        }
        val whitespace = WhitespaceSubGenerator()
        
        inner class LabelSubGenerator{
            operator fun invoke() = TokenParser<T>( TokenPredicate.label() )
            operator fun invoke(label: String) = exact(LabelToken(label))
        }
        val label = LabelSubGenerator()
        
        inner class StringSubGenerator{
            operator fun invoke() = TokenParser<T>( TokenPredicate.string() )
            fun mode(m: String) = TokenParser<T>( TokenPredicate.string(StringToken.Modes.valueOf(m.toUpperCase())) )
            fun strong() = TokenParser<T>( TokenPredicate.string(StringToken.Modes.STRONG))
            fun weak() = TokenParser<T>( TokenPredicate.string(StringToken.Modes.WEAK))
        }
        val string = StringSubGenerator()
        
        inner class NumberSubGenerator{
            operator fun invoke() = TokenParser<T>( TokenPredicate.number() )
            operator fun invoke(i: Long) = exact(NumberToken(i))
            operator fun invoke(i: Int) = exact(NumberToken(i.toLong()))
            operator fun invoke(d: Double) = exact(NumberToken(d))
            operator fun invoke(min: Long, max: Long) = TokenParser<T>( TokenPredicate.number(min, max) )
            operator fun invoke(min: Int, max: Int) = TokenParser<T>( TokenPredicate.number(min, max) )
            operator fun invoke(min: Double, max: Double) = TokenParser<T>( TokenPredicate.number(min, max) )
            fun integer() = TokenParser<T>( TokenPredicate.number(NumberToken.Modes.INTEGER) )
            fun decimal() = TokenParser<T>( TokenPredicate.number(NumberToken.Modes.DECIMAL) )
        }
        val number = NumberSubGenerator()
        
        inner class SymbolSubGenerator{
            operator fun invoke() = TokenParser<T>( TokenPredicate.symbol() )
            operator fun invoke(c: Char) = exact(SymbolToken(c))
            operator fun invoke(chars: Collection<Char>) = TokenParser<T>( TokenPredicate.symbol(chars) )
        }
        val symbol = SymbolSubGenerator()
        
    }
}
class SequentialParser<T, F>(val subParsers: List<Parser<T, F>>): Parser<T, F>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>(tokens, pos){
        override var end: Int? = null

        private val subInstances = mutableListOf<MatcherInstance<T, F>>()
        private var state = 0

        init{
            subInstances.add( subParsers[0].createInstance(tokens, pos))
            performTest()
        }
        fun performTest() {
            while (subInstances.isNotEmpty() && state < subParsers.size) {
                if (subInstances.last().isMatching()) {
                    state++
                    if (state < subParsers.size) {
                        subInstances += subParsers[state].createInstance(tokens, subInstances.last().end!!)
                    }
                } else {
                    state--
                    subInstances.removeAt(subInstances.lastIndex)
                    if (state >= 0) {
                        subInstances.last().tryAgain()
                    }
                }
            }
            end =
                if (state == -1){
                    null
                }else{
                    subInstances.last().end
                }
        }

        override fun internalTryAgain() {
            state--
            subInstances.last().tryAgain()

            performTest()
        }

        override fun getResult(flags: F): Result<T> {
            return CompoundResult(subInstances.map{it.getResult(flags)})
        }
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return Instance(tokens, pos)
    }
    companion object{
        operator fun <T, F> Parser<T, F>.rangeTo(other: Parser<T, F>): Parser<T, F> {
            return if (this is SequentialParser) {
                if (other is SequentialParser) {
                    SequentialParser(this.subParsers + other.subParsers)
                } else {
                    SequentialParser(this.subParsers + listOf(other))
                }
            } else {
                if (other is SequentialParser) {
                    SequentialParser(listOf(this) + other.subParsers)
                } else {
                    SequentialParser(listOf(this, other))
                }
            }
        }
    }
}
class BranchedParser<T, F>(val subParsers: List<Parser<T, F>>): Parser<T, F>(){
    constructor(vararg subParsers: Parser<T, F>): this(subParsers.toList())
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>(tokens, pos){
        override var end: Int? = null

        var currentSubInstance
                = subParsers[0].createInstance(tokens, pos)
        var nextSubInstance = 1

        init{
            performTest()
        }
        private fun performTest(){
            while (!currentSubInstance.isMatching() && nextSubInstance < subParsers.size){
                currentSubInstance = subParsers[nextSubInstance].createInstance(tokens, pos)
                nextSubInstance++
            }
            end =
                if (currentSubInstance.isMatching()){
                    currentSubInstance.end
                }else{
                    null
                }
        }

        override fun internalTryAgain() {
            currentSubInstance.tryAgain()
            performTest()
        }

        override fun getResult(flags: F): Result<T> {
            return currentSubInstance.getResult(flags)
        }
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return Instance(tokens, pos)
    }

    companion object{
        infix fun <T, F> Parser<T, F>.or(other: Parser<T, F>): Parser<T, F> {
            return if (this is BranchedParser) {
                if (other is BranchedParser) {
                    BranchedParser(this.subParsers + other.subParsers)
                } else {
                    BranchedParser(this.subParsers + listOf(other))
                }
            } else {
                if (other is BranchedParser) {
                    BranchedParser(listOf(this) + other.subParsers)
                } else {
                    BranchedParser(listOf(this, other))
                }
            }
        }
    }
}
class QuantifiedParser<T, F>(val subParser: Parser<T, F>, val quantifier: Quantifier): Parser<T, F>(){
    data class Quantifier(val min: Int, val max: Int, val mode: Mode){
        init{
            assert(min >= 0)
            assert(max >= min)
        }
        enum class Mode{ GREEDY, POSSESSIVE, RELUCTANT }
        fun isSingle() = min == 1 && max == 1
        fun isEmpty() = max == 0
    }

    private inner class GreedyInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>(tokens, pos){
        val subInstances = mutableListOf<MatcherInstance<T, F>>()
        override var end: Int? = null

        init{
            subInstances.add(subParser.createInstance(tokens, pos))

            performTest()
        }
        fun findNextBranch(){
            while (subInstances.isNotEmpty() && !subInstances.last().isMatching()){
                subInstances.removeAt(subInstances.lastIndex)
                subInstances.lastOrNull()?.tryAgain()
            }
        }
        fun performTest(){
            while(true){
                findNextBranch()

                if (subInstances.isNotEmpty()){
                    while(subInstances.size < quantifier.max && subInstances.last().isMatching()){
                        subInstances.add(subParser.createInstance(tokens, subInstances.last().end!!))
                    }
                    if (!subInstances.last().isMatching()){
                        subInstances.removeAt(subInstances.lastIndex)
                    }
                }

                if (subInstances.size in quantifier.min .. quantifier.max){
                    end = subInstances.lastOrNull()?.end ?:pos
                    break
                }else if(subInstances.isEmpty()){
                    end = null
                    break
                }else{
                    subInstances.last().tryAgain()
                }
            }
        }

        override fun internalTryAgain() {
            if (subInstances.isEmpty() || quantifier.mode == Quantifier.Mode.POSSESSIVE){
                end = null
            }else{
                subInstances.last().tryAgain()
                performTest()
            }
            if (end == null){
                subInstances.clear()
            }
        }

        override fun getResult(flags: F): Result<T> {
            return CompoundResult(subInstances.map{it.getResult(flags)})
        }
    }

    private inner class ReluctantInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>(tokens, pos){
        val subInstances = mutableListOf<MatcherInstance<T, F>>()
        override var end: Int? = null

        init{
            if (quantifier.min == 0){
                end = pos
            }else{
                performTest()
            }
        }
        fun findNextBranch(){
            while(subInstances.isNotEmpty() && !subInstances.last().isMatching()){
                subInstances.removeAt(subInstances.lastIndex)
                subInstances.lastOrNull()?.tryAgain()
            }
        }
        fun performTest(){
            while(true){
                if (subInstances.size == quantifier.max){
                    subInstances.last().tryAgain()
                }else{
                    subInstances.add(subParser.createInstance(tokens, subInstances.lastOrNull()?.end?: pos))
                }

                findNextBranch()

                if (subInstances.isEmpty()){
                    end = null
                    break
                }else if (subInstances.size in quantifier.min .. quantifier.max){
                    end = subInstances.last().end
                    break
                }
            }
        }

        override fun internalTryAgain() {
            performTest()
        }

        override fun getResult(flags: F): Result<T> {
            return CompoundResult(subInstances.map{it.getResult(flags)})
        }
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return when(quantifier.mode){
            Quantifier.Mode.RELUCTANT -> ReluctantInstance(tokens, pos)
            else -> GreedyInstance(tokens, pos)
        }
    }

    companion object{
        operator fun <T, F> Parser<T, F>.times(quantifier: Quantifier): Parser<T, F> {
            return QuantifiedParser(this, quantifier)
        }
        operator fun <T, F> Parser<T, F>.times(amt: Int) = this * q(amt, amt)

        fun greedy(min: Int, max: Int) = Quantifier(min, max, Quantifier.Mode.GREEDY)
        fun reluctant(min: Int, max: Int) = Quantifier(min, max, Quantifier.Mode.RELUCTANT)
        fun possessive(min: Int, max: Int) = Quantifier(min, max, Quantifier.Mode.POSSESSIVE)
        fun q(min: Int, max: Int = Int.MAX_VALUE) = greedy(min, max)

        val maybe = greedy(0, 1)

        operator fun Quantifier.unaryPlus() = this.copy(mode = Quantifier.Mode.POSSESSIVE)
        operator fun Quantifier.unaryMinus() = this.copy(mode = Quantifier.Mode.RELUCTANT)
    }
}




class LazyParser<T, F>(initializer: ()->Parser<T, F>): Parser<T, F>(){
    private val subParser: Parser<T, F> by lazy(initializer)
    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return subParser.createInstance(tokens, pos)
    }
    companion object{
        fun <T, F> lz(initializer: ()->Parser<T, F>) = LazyParser(initializer)
    }
}

class TransformParser<Ti, Fi, To, Fo>(val subParser: Parser<Ti, Fi>, val handler: ((Fi)->Result<Ti>, Fo)->Result<To>): Parser<To, Fo>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<To, Fo>(tokens, pos){
        val subInstance = subParser.createInstance(tokens, pos)
        override val end: Int?
            get() = subInstance.end

        override fun internalTryAgain() {
            subInstance.tryAgain()
        }

        override fun getResult(flags: Fo): Result<To> {
            return handler(subInstance::getResult, flags)
        }

    }
    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<To, Fo> {
        return Instance(tokens, pos)
    }

    companion object{
        infix fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.trans(handler: ((Fi)->Result<Ti>, Fo)->Result<To>)
                = TransformParser(this, handler)
        infix fun <Ti, Fi, To, Fo> Parser<Ti, Fi>.transValue(handler: ((Fi)->Result<Ti>, Fo)->To)
                = TransformParser<Ti, Fi, To, Fo>(this){ resultGetter, flags -> ValueResult(handler(resultGetter, flags)) }

        infix fun <Ti, To, F> Parser<Ti, F>.transResult(handler: (Result<Ti>, F)->Result<To>)
                = TransformParser<Ti, F, To, F>(this){ resultGetter, flags -> handler(resultGetter(flags), flags)}
        infix fun <Ti, To, F> Parser<Ti, F>.transResultValue(handler: (Result<Ti>, F)->To)
                = TransformParser<Ti, F, To, F>(this){ resultGetter, flags -> ValueResult(handler(resultGetter(flags), flags)) }
    }
}
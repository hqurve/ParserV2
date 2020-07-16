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

    operator fun get(index: Int) = subResults[index]
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

abstract class WrappedParser<T, F>: Parser<T, F>(){
    protected abstract val internalParser: Parser<T, F>
    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return internalParser.createInstance(tokens, pos)
    }
}

class EmptyParser<T, F>: Parser<T, F>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>(tokens, pos){
        override var end: Int? = pos

        override fun internalTryAgain() {
            end = null
        }

        override fun getResult(flags: F): Result<T> {
            return CompoundResult(emptyList())
        }
    }
    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return Instance(tokens, pos)
    }
}

class TokenParser<T, F>(val tokenPredicate: TokenPredicate): Parser<T, F>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>(tokens, pos){
        override var end: Int? = if (pos < tokens.size && tokens[pos] matches tokenPredicate) pos + 1 else null
        override fun internalTryAgain() {
            end = null
        }
        override fun getResult(flags: F) = TokenResult<T>(tokens[pos])
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return Instance(tokens, pos)
    }

}
class SequentialParser<T, F>(parsers: List<Parser<T, F>>): Parser<T, F>(){
    constructor(vararg parsers: Parser<T, F>): this(parsers.toList())
    val subParsers: List<Parser<T, F>>
            = parsers.map{
                if (it is SequentialParser){
                    it.subParsers
                }else{
                    listOf(it)
                }
            }.flatten()
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
}
class BranchedParser<T, F>(parsers: List<Parser<T, F>>): Parser<T, F>(){
    constructor(vararg parsers: Parser<T, F>): this(parsers.toList())
    val subParsers: List<Parser<T, F>>
        = parsers.map{
            if (it is BranchedParser){
                it.subParsers
            }else{
                listOf(it)
            }
        }.flatten()
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
}
data class Quantifier(val min: Int, val max: Int, val mode: Modes){
    init{
        assert(min >= 0)
        assert(max >= min)
    }
    enum class Modes{ GREEDY, POSSESSIVE, RELUCTANT }
    fun isSingle() = min == 1 && max == 1
    fun isEmpty() = max == 0

    fun asPossessive() = this.copy(mode = Quantifier.Modes.POSSESSIVE)
    fun asReluctant()  = this.copy(mode = Quantifier.Modes.RELUCTANT)
}
class QuantifiedParser<T, F>(val subParser: Parser<T, F>, val quantifier: Quantifier): Parser<T, F>(){

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
            if (subInstances.isEmpty() || quantifier.mode == Quantifier.Modes.POSSESSIVE){
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
            Quantifier.Modes.RELUCTANT -> ReluctantInstance(tokens, pos)
            else -> GreedyInstance(tokens, pos)
        }
    }


}




class LazyParser<T, F>(val initializer: ()->Parser<T, F>): Parser<T, F>(){
    private val subParser: Parser<T, F> by lazy(initializer)
    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return subParser.createInstance(tokens, pos)
    }
}


class FlagTransformParser<T, Fi, Fo>(val subParser: Parser<T, Fi>, val flagTransform: (Fo)->Fi): Parser<T, Fo>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, Fo>(tokens, pos){
        val subInstance = subParser.createInstance(tokens, pos)
        override val end: Int?
            get() = subInstance.end

        override fun internalTryAgain() {
            subInstance.tryAgain()
        }

        override fun getResult(flags: Fo): Result<T> {
            return subInstance.getResult(flagTransform(flags))
        }
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, Fo> {
        return Instance(tokens, pos)
    }
}

class ResultTransformParser<Ti, To, F>(val subParser: Parser<Ti, F>, val resultTransform: (Result<Ti>, F)-> Result<To>): Parser<To, F>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<To, F>(tokens, pos){
        val subInstance = subParser.createInstance(tokens, pos)
        override val end: Int?
            get() = subInstance.end

        override fun internalTryAgain() {
            subInstance.tryAgain()
        }

        override fun getResult(flags: F): Result<To> {
            return resultTransform(subInstance.getResult(flags), flags)
        }
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<To, F> {
        return Instance(tokens, pos)
    }
}

class TransformParser<Ti, Fi, To, Fo>(parser: Parser<Ti, Fi>, flagTransform: (Fo)->Fi, resultTransform: (Result<Ti>, Fo) -> Result<To>): WrappedParser<To, Fo>(){
    override val internalParser: Parser<To, Fo>
        = ResultTransformParser(FlagTransformParser(parser, flagTransform), resultTransform)
}

class FixedParser<T, F>(val subParser: Parser<*, *>, val handler: (F)->Result<T>): Parser<T, F>(){
    private inner class Instance(tokens: List<Token>, pos: Int): MatcherInstance<T, F>(tokens, pos){
        val subInstance = subParser.createInstance(tokens, pos)
        override val end: Int?
            get() = subInstance.end

        override fun internalTryAgain() {
            subInstance.tryAgain()
        }

        override fun getResult(flags: F): Result<T> {
            return handler(flags)
        }
    }

    override fun createInstance(tokens: List<Token>, pos: Int): MatcherInstance<T, F> {
        return Instance(tokens, pos)
    }
}



class BiValue<Ta, Tb> private constructor(private val mode: Modes, private val m_aVal: Ta?, private val m_bVal: Tb?){
    private enum class Modes{A_VAL, B_VAL}

    val aValue: Ta
        get(){
            if (!isAValue()) error("A-value not set")
            return m_aVal as Ta
        }
    val bValue: Tb
        get(){
            if (!isBValue()) error("B-value not set")
            return m_bVal as Tb
        }

    fun isAValue() = mode == Modes.A_VAL
    fun isBValue() = mode == Modes.B_VAL


    companion object{
        fun <Ta, Tb> a(aVal: Ta) = BiValue<Ta, Tb>(Modes.A_VAL, aVal, null)
        fun <Ta, Tb> b(bVal: Tb) = BiValue<Ta, Tb>(Modes.B_VAL, null, bVal)
    }
}

abstract class BiParser<Ta, Fa, Tb, Fb> private constructor(): WrappedParser<BiValue<Result<Ta>, Result<Tb>>, Pair<Fa, Fb>>(){

    class A<Ta, Fa, Tb, Fb>(parser: Parser<Ta, Fa>): BiParser<Ta, Fa, Tb, Fb>(){
        override val internalParser: Parser<BiValue<Result<Ta>, Result<Tb>>, Pair<Fa, Fb>>
                = TransformParser(parser, {(flags, _) -> flags}, {result, _ -> ValueResult(BiValue.a(result))})
    }
    class B<Ta, Fa, Tb, Fb>(parser: Parser<Tb, Fb>): BiParser<Ta, Fa, Tb, Fb>(){
        override val internalParser: Parser<BiValue<Result<Ta>, Result<Tb>>, Pair<Fa, Fb>>
                = TransformParser(parser, {(_, flags) -> flags}, {result, _ -> ValueResult(BiValue.b(result))})
    }

    companion object{
        fun <Ta, Fa, Tb, Fb> a(aParser: Parser<Ta, Fa>) = A<Ta, Fa, Tb, Fb>(aParser)
        fun <Ta, Fa, Tb, Fb> b(bParser: Parser<Tb, Fb>) = B<Ta, Fa, Tb, Fb>(bParser)
    }
}
package com.hqurve.parsing

infix fun Token.matches(tokenPredicate: TokenPredicate) = tokenPredicate.matches(this)

class TokenPredicate(val matcherFun: (token: Token)->Boolean){
    fun matches(token: Token) = matcherFun(token)

    companion object{
        fun any() = TokenPredicate{true}

        fun exact(requiredToken: Token) = TokenPredicate{it == requiredToken}

        fun whitespace() = TokenPredicate{it is WhitespaceToken}

        fun label() = TokenPredicate{it is LabelToken}


        fun string() = TokenPredicate{it is StringToken}
        fun string(mode: StringToken.Modes) = TokenPredicate{it is StringToken && it.mode == mode}

        fun number() = TokenPredicate{it is NumberToken}
        fun number(mode: NumberToken.Modes) = TokenPredicate{it is NumberToken && it.mode == mode}

        fun number(lowerBound: Long, upperBound: Long) = TokenPredicate{
            it is NumberToken
                    && it.value is Long
                    && it.value in lowerBound..upperBound
        }
        fun number(lowerBound: Int, upperBound: Int) = TokenPredicate{
            it is NumberToken
                    && it.value is Long
                    && it.value in lowerBound..upperBound
        }
        fun number(lowerBound: Double, upperBound: Double) = TokenPredicate{
            it is NumberToken
                    && it.value.toDouble() in lowerBound..upperBound
        }

        fun symbol() = TokenPredicate{it is SymbolToken}
        fun symbol(syms: Collection<Char>): TokenPredicate{
            val symbolSet = syms.toSet()
            return TokenPredicate{
                it is SymbolToken
                        && it.sym in symbolSet
            }
        }
    }
}

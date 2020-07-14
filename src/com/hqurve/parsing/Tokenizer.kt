package com.hqurve.parsing

interface Token{

    companion object{
        val tokenClasses = listOf(
            WhitespaceToken::class,
            LabelToken::class,
            StringToken::class,
            NumberToken::class,
            SymbolToken::class
        )
    }
}
data class StringToken(val str: String, val mode: Modes): Token{
    enum class Modes{STRONG, WEAK}
}
data class NumberToken(val value: Number, val mode: Modes): Token{
    constructor(value: Long): this(value, Modes.INTEGER)
    constructor(value: Double): this(value, Modes.DECIMAL)
    enum class Modes{INTEGER, DECIMAL}
}
data class SymbolToken(val sym: Char): Token{

    companion object{
        val SYMBOL_SET = """
            !   ~   &   ^
            ${'$'}   %   #   @
            =   +   -   *
            /   \   |   _
            ;   :   ?   ,
            .
            [   {   (   <
            ]   }   )   >
        """.replace(Regex("\\s+"), "").toSet()

    }
}
data class LabelToken(val str: String): Token
data class WhitespaceToken(val str: String): Token


class TokenizerException(pos: Int, message: String): RuntimeException("Token Exception at $pos: $message")

class Tokenizer(
    val includeWhitespaces: Boolean = true,
    val labelsHaveDigits: Boolean = true,
    val captureDecimalNumbers: Boolean = true,
    val resolveEscapedStringCharacters: Boolean = true
){
    companion object{
        fun exception(pos: Int, message: String): Nothing = throw TokenizerException(pos, message)
    }
    fun tokenize(string: String): List<Token>{
        val list = mutableListOf<Token>()

        var end = 0
        while (end < string.length) {
            val ch = string[end]

            val pair = when{
                ch.isWhitespace() -> getWhiteSpaceToken(string, end)
                ch.isLetter() -> getLabelToken(string, end)
                ch.let{it == '\'' || it == '\"'} -> getStringToken(string, end)
                ch.isDigit() -> getNumberToken(string, end)
                else -> getSymbolToken(string, end)
            }

            if (includeWhitespaces || pair.first !is WhitespaceToken){
                list.add(pair.first)
            }
            end = pair.second
        }

        if (end != string.length) exception(end, "Unable to parse past here")
        return list.toList()
    }
    private fun getWhiteSpaceToken(string: String, pos: Int): Pair<WhitespaceToken, Int>{
        var end = pos
        while (end < string.length && string[end].isWhitespace()){
            end++
        }

        if (end == pos) exception(pos, "Empty whitespace")
        return WhitespaceToken(string.substring(pos, end)) to end
    }
    private fun getLabelToken(string: String, pos: Int): Pair<LabelToken, Int>{
        if (!string[pos].isLetter()) exception(pos, "Invalid LabelToken")
        var end = pos + 1

        while (end < string.length && if (labelsHaveDigits) string[end].isLetterOrDigit() else string[end].isLetter()){
            end++
        }

        return LabelToken(string.substring(pos, end)) to end
    }
    private fun getStringToken(string: String, pos: Int): Pair<StringToken, Int>{
        val (modeChar, mode) = when(string[pos]){
            '\'' -> '\'' to StringToken.Modes.WEAK
            '\"' -> '\"' to StringToken.Modes.STRONG
            else -> exception(pos, "Invalid StringToken")
        }

        var endPos = pos + 1
        while(true){
            if (endPos == string.length) exception(endPos, "Unexpected StringToken end")
            if (string[endPos] == modeChar) break
            if (string[endPos] == '\\'){
                endPos++
                if (endPos == string.length) exception(endPos, "Badly escaped character in StringToken")
//                    if (string[endPos] != modeChar && string[endPos] != '\\') return null
                endPos++
            }else{
                endPos++
            }
        }


        return StringToken(
            string.substring(pos+1, endPos).run{
                if (resolveEscapedStringCharacters) replace(Regex("\\\\(.)"), "$1")
                else this
            }, mode) to endPos + 1
    }
    private fun getNumberToken(string: String, pos: Int): Pair<NumberToken, Int>{
        var end = pos

        while (end < string.length && string[end].isDigit()){
            end++
        }

        if (end == pos) exception(pos, "Empty number")

        val mode: NumberToken.Modes
        val value: Number
        if (captureDecimalNumbers && end + 1 < string.length && string[end] == '.' && string[end+1].isDigit()){
            mode = NumberToken.Modes.DECIMAL
            end++
            while (end < string.length && string[end].isDigit()){
                end++
            }
            value = string.substring(pos, end).toDouble()
        }else{
            mode = NumberToken.Modes.INTEGER
            value = string.substring(pos, end).toLong()
        }

        return NumberToken(value, mode) to end
    }
    private fun getSymbolToken(string: String, pos: Int): Pair<SymbolToken, Int>{
        if (string[pos] !in SymbolToken.SYMBOL_SET) exception(pos, "Invalid symbol")
        return SymbolToken(string[pos]) to pos + 1
    }
}
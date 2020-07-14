package com.hqurve.parsing.examples

import com.hqurve.parsing.*
import com.hqurve.parsing.BranchedParser.Companion.or
import com.hqurve.parsing.LazyParser.Companion.lz
import com.hqurve.parsing.QuantifiedParser.Companion.maybe
import com.hqurve.parsing.QuantifiedParser.Companion.q
import com.hqurve.parsing.QuantifiedParser.Companion.times
import com.hqurve.parsing.SequentialParser.Companion.rangeTo
import com.hqurve.parsing.TransformParser.Companion.transResultValue
import kotlin.math.pow


class JSONParser{
    private val stringParser: Parser<String, Any?>
    private val numberParser: Parser<Number, Any?>
    private val kvPairParser: Parser<Pair<String, Any?>, Any?>
    private val objectParser: Parser<Map<String, Any?>, Any?>
    private val arrayParser: Parser<List<Any?>, Any?>
    private lateinit var valueParser: Parser<Any?, Any?>

    init {
        numberParser = lz {
            val gen = TokenParser.Generator<Number>()
            gen.symbol('-') * maybe .. gen.number()..
                    ( gen.label("e")..
                            (gen.symbol('+') or gen.symbol('-')) * maybe..
                            gen.number()
                    ) * maybe
        } transResultValue { results, _ ->
            results as CompoundResult
            val isMinus = results.compoundAt(0).isNotEmpty()
            val number = results.tokenAt<NumberToken>(1).value

            val exponent =
                if (results.compoundAt(2).isEmpty()) {
                    null
                } else {
                    val innerResult = results.compoundAt(2).single() as CompoundResult
                    val isNegated = innerResult.compoundAt(1).run {
                        isNotEmpty() && tokenAt<SymbolToken>(0).sym == '-'
                    }
                    val exponent = innerResult.tokenAt<NumberToken>(2).value.toLong()

                    if (isNegated) -exponent else exponent
                }

            when{
                exponent != null -> number.toDouble() * (if (isMinus) -1 else 1) * 10.0.pow(exponent.toDouble())
                number is Long   -> number * (if (isMinus) -1 else 1)
                number is Double -> number * (if (isMinus) -1 else 1)
                else -> error("how did we get here")
            }as Number
        }
    }
    init {
       stringParser = lz {
            val gen = TokenParser.Generator<String>()
            gen.string.strong()
        } transResultValue { results, _ ->
            val escapedString = results.asToken<StringToken>().str
            decodeEscapedString(escapedString)
        }
    }
    init{
        kvPairParser =lz{
            val gen = TokenParser.Generator<Any?>()
            stringParser .. gen.symbol(':') .. valueParser
        } transResultValue{ results, _ ->
            results as CompoundResult
            (results.valueAt(0) as String) to (results.valueAt(2))
        }
    }
    init{
        val gen = TokenParser.Generator<Pair<String, Any?>>()
        objectParser = BranchedParser(
            lz{gen.symbol('{') .. gen.symbol('}')} transResultValue {_, _ -> emptyMap<String, Any?>() },
            lz{
                gen.symbol('{') .. kvPairParser .. (gen.symbol(',') .. kvPairParser)*q(0) .. gen.symbol('}')
            } transResultValue {results, _ ->
                results as CompoundResult
                val primary = results.valueAt(1)
                val trailingResults = results.compoundAt(2).map{it.asCompound().valueAt(1)}
                (listOf(primary) + trailingResults).toMap()
            }
        )
    }
    init{
        val gen = TokenParser.Generator<Any?>()
        arrayParser = BranchedParser(
            lz{gen.symbol('[') .. gen.symbol(']')} transResultValue {_, _ -> emptyList<Any?>()},
            lz{
                gen.symbol('[') .. valueParser .. (gen.symbol(',') .. valueParser) * q(0) .. gen.symbol(']')
            } transResultValue{ results, _ ->
                results as CompoundResult
                val primary = results.valueAt(1)
                val trailingResults = results.compoundAt(2).map{it.asCompound().valueAt(1)}
                listOf(primary) + trailingResults
            }
        )
    }
    init{
        val gen = TokenParser.Generator<Any?>()
        valueParser = BranchedParser(
            gen.label("null") transResultValue {_, _ -> null},
            gen.label("true") transResultValue {_, _ -> true},
            gen.label("false") transResultValue {_, _ -> false},
            objectParser or arrayParser or numberParser or stringParser
        )
    }
    private fun decodeEscapedString(escapedString: String): String{
        val sb = StringBuilder()

        var index = 0
        while(index < escapedString.length){
            if (escapedString[index] in '\u0000'..'\u001F' || escapedString[index] == '\"') throw CharacterCodingException()
            if (escapedString[index] == '\\'){
                if (index + 1 == escapedString.length) throw CharacterCodingException()
                sb.append(when(escapedString[index+1]){
                    '\"' -> '\"'
                    '\\' -> '\\'
                    '/'  -> '/'
                    'b' -> '\u0008'
                    'f' -> '\u000C'
                    'n' -> '\u000A'
                    'r' -> '\u000D'
                    't' -> '\u0009'
                    'u' ->{
                        if (index + 6 > escapedString.length) throw CharacterCodingException()
                        val codePoint = escapedString.substring(index + 2, index + 6).toInt()
                        Character.toChars(codePoint)[0]
                        index += 4
                    }
                    else -> throw CharacterCodingException()
                })
                index += 2
            }else{
                sb.append(escapedString[index])
                index++
            }
        }
        return sb.toString()
    }


    private val tokenizer = Tokenizer(
        includeWhitespaces = false,
        captureDecimalNumbers = true,
        resolveEscapedStringCharacters = false,
        labelsHaveDigits = false
    )

    fun parseObject(jsonString:String) = objectParser.parse(tokenizer.tokenize(jsonString), null)?.asValue()
    fun parseArray(jsonString: String) = arrayParser.parse(tokenizer.tokenize(jsonString), null)?.asValue()
}

fun main(){
    val jsonParser = JSONParser()

    jsonParser.run{
        println(parseObject("""
            {
                "name": "hquvre",
                "friends": ["john", "carl", "carlos"],
                "age": 19,
                "occupation":{
                    "title": "student",
                    "type": "undergrad",
                    "degree": "mathematics",
                    "year": 1
                },
                "is happy": true,
                "gender": "male",
                "message": "\n
                I created this general parsing engine (named \"Parser\").\n
                By using this engine, it was very easy for me to create a jsonparser using a simple set of macros.\n
                Moreover, a large portion of the above code is decyphering the encoded numbers and strings which cannot be avoided.\n
                By using this engine, all pattern matching tasks are easily taken care of and allowed the jsonparser to be created quickly.\n
                Of course, a static parser would most likely be quicker than this parser but it is still quite fast. (I still have to do testing though)\n
                But the parser pre-compiles the patterns for each of the macros and reuses matchers allowing it to be quite quick after the first few runs.
                "
            }
        """.replace(Regex("\\s*\\n\\s*"), "")))
        println(parseArray("""
            [
            {"score": 12.5e2, "name":"player1", "max-level": 502}, 5
            ]
        """.replace(Regex("\\s*\\n\\s*"), "")))
    }
}
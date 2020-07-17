package com.hqurve.parsing.examples

import java.io.File
import java.io.FileInputStream

internal class JSONParserTest{
    @org.junit.jupiter.api.Test
    fun parseObject() {

        println("ParserV2")
        val parser = JSONParser()
        val testDirectory = File("test data/json")

        val listFile = File(testDirectory, "file list.json")

        val fileList = FileInputStream(listFile).run{
            parser.parseArray(String(readAllBytes()))!!.map{it as String}.also{close()}
        }
        println("File list loaded")

        val loadedFiles = fileList.map{fileName ->
            fileName to FileInputStream(File(testDirectory, fileName)).run{String(readAllBytes()).also{close()}}
        }

        println("File contents loaded")

        val loopCount = 1000
        println("Loop count = $loopCount")

        for (pass in 1..3) {
            println()
            println("Starting pass $pass")
            print("Name".padEnd(30))
            print("Total (seconds)".padStart(20))
            print("Average (millis)".padStart(20))
            println()
            for ((fileName, fileContents) in loadedFiles) {
                print("Test $fileName (length=${fileContents.length})".padEnd(30))
                val startTime = System.nanoTime()
                var failed = false
                for (i in 0 until loopCount) {
                    if (parser.parseValue(fileContents) == null) {
                        print("failed on loop $i")
                        failed = true
                        break
                    }
                }
                if (!failed) {
                    val endTime = System.nanoTime()

                    print(String.format("%.6f", (endTime - startTime) / 1_000_000_000.0).padStart(20))
                    print(String.format("%.6f", (endTime - startTime) / (1_000_000.0 * loopCount)).padStart(20))
                }
                println()
            }
            println("Pass $pass complete")
            println()
        }

        println("Test complete")
    }

}
package com.hqurve.parsing.examples

import java.io.File
import java.io.FileInputStream

fun main(vararg args: String){
    println(args.asList())

    test("Parser V2", JSONParser()::parseValue, args[0].takeIf{it.isNotBlank()}?.let{File(it)}, args[1], args[2].toInt(), args[3].toInt())
}


fun test(name: String, parser: (String)->Any?, testDirectory: File? = null, listFileName: String, passCount: Int, loopCount: Int){
    println(name)

    val listFile = File(testDirectory, listFileName)

    val fileList = FileInputStream(listFile).run{
        (parser(String(readAllBytes()))!! as List<*>).map{it as String}.also{close()}
    }
    println("File list loaded")

    val loadedFiles = fileList.map{fileName ->
        fileName to FileInputStream(File(testDirectory, fileName)).run{String(readAllBytes()).also{close()}}
    }

    println("File contents loaded")

    println("Loop count = $loopCount")

    for (pass in 1..passCount) {
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
                if (parser(fileContents) == null) {
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
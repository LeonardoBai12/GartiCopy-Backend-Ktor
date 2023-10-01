package io.lb.util

import java.io.File

val words = readWordList("resources/wordlist.txt")

fun readWordList(fileName: String): List<String> {
    val inputStream = File(fileName).inputStream()
    val words = mutableListOf<String>()
    inputStream.bufferedReader().forEachLine { words.add(it) }
    return words
}

fun getRandomWords(amount: Int): List<String> {
    val result = mutableListOf<String>()
    var i = 0

    while (i < amount) {
        val word = words.random()
        if (word !in words) {
            result.add(word)
            i++
        }
    }

    return result
}

/**
 * Transforms a word to a set of underscores, for example:
 * Breaking Bad -> ________ ___
 */
fun String.transformToUnderscores() =
    toCharArray().map {
        if (it != ' ') '_' else ' '
    }.joinToString(" ")

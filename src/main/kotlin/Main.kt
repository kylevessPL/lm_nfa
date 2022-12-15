/**
 * @author Kacper Piasta, 249105
 *
 * difficulty level: 3
 */

import Symbol.ONE
import Symbol.THREE
import Symbol.TWO
import Symbol.ZERO
import java.io.Closeable
import java.lang.System.lineSeparator
import java.util.Scanner

/**
 * Token separator
 */
private const val TOKEN_SEPARATOR = "#"

/**
 * Exception thrown if read character is not accepted by the automaton
 *
 * (in other words: if NFA input alphabet doesn't contain symbol)
 */
private class SymbolNotAcceptedException(value: Char) : Exception("Automaton doesn't accept symbol: $value")

/**
 * Enumeration class representation of symbols
 *
 * Used for type-safety to prevent read of non-accepted character
 */
enum class Symbol(private val value: Any) {
    ZERO(0), ONE(1), TWO(2), THREE(3);

    companion object {
        /**
         * Gets enumeration object by character
         */
        @JvmStatic
        fun of(value: Char): Symbol {
            return when (value) {
                '0' -> ZERO
                '1' -> ONE
                '2' -> TWO
                '3' -> THREE
                else -> throw SymbolNotAcceptedException(value)
            }
        }
    }

    override fun toString() = value.toString()
}

/**
 * Actual implementation of NFA
 */
class Automaton : Closeable {
    /**
     * Companion object containing static fields
     */
    private companion object {
        /**
         * Transition table representation as a key-value map
         *
         * Key – pair of unique states with corresponding transition values
         *
         * Value – next state
         */
        @JvmField
        val transitionTable = mapOf(
            Pair(0, ZERO) to setOf(0, 1), Pair(0, ONE) to setOf(0, 1), Pair(0, TWO) to setOf(0, 1), Pair(0, THREE) to setOf(0),
            Pair(1, ZERO) to emptySet(), Pair(1, ONE) to setOf(2), Pair(1, TWO) to setOf(2), Pair(1, THREE) to setOf(2),
            Pair(2, ZERO) to setOf(2), Pair(2, ONE) to setOf(2), Pair(2, TWO) to setOf(2, 3), Pair(2, THREE) to setOf(2, 3),
            Pair(3, ZERO) to setOf(3), Pair(3, ONE) to setOf(3), Pair(3, TWO) to setOf(3), Pair(3, THREE) to setOf(3, 4),
            Pair(4, ZERO) to setOf(4), Pair(4, ONE) to setOf(4), Pair(4, TWO) to setOf(4), Pair(4, THREE) to setOf(4)
        )

        /**
         * Set of accepted states
         */
        @JvmField
        val acceptingStates = setOf(2, 3, 4)
    }

    /**
     * Map of symbols with number of their read occurences
     */
    private val _occurrences = mutableMapOf<Symbol, Int>()

    /**
     * Representation of state paths
     *
     * Contains all traversed state paths
     */
    private var _paths = listOf(listOf(0))

    /**
     * If automaton state is in on-hold (no-win) situation
     */
    private var onHold = false

    init {
        printStates()
    }

    /**
     * Consumes symbols and transitions to next states if applicable
     */
    fun consume(symbol: Symbol): Boolean {
        /**
         * Get next state from transition table
         */
        fun List<Int>.nextState() = transitionTable[Pair(last(), symbol)].orEmpty().map { this + it }

        /**
         * Check if automaton is in accepting state
         *
         * (if state changed and any last state is accepting one)
         */
        fun isAccepting() = _paths.any { acceptingStates.contains(it.last()) }

        /**
         * Increments occurrence of specific symbol
         */
        fun incrementOccurrence() = _occurrences.merge(symbol, 1) { a: Int, b: Int -> a + b }

        /**
         * Increments occurrence of specific symbol
         */
        fun printOccurrences() = println("Symbol $this occurred ${_occurrences[symbol]} times already")

        /**
         * Updates state paths
         *
         * @return boolean if current state is accepting or rejecting
         */
        fun List<List<Int>>.updatePaths() = ifEmpty { onHold = true; this }
            .takeIf { it.isNotEmpty() && it != _paths }
            ?.let {
                _paths = this
                incrementOccurrence()
                true
            } == true

        println("Reading symbol: $symbol")

        /** perform actual state transition **/
        return _paths.takeIf { !onHold }
            ?.map(List<Int>::nextState)
            ?.flatten()
            ?.filterNot(List<Int>::isEmpty)
            ?.run {
                val comp = updatePaths()
                printStates()
                !comp && isAccepting()
            }?.also { if (it) printOccurrences() } == true
    }

    /**
     * Prints final results on completion
     */
    override fun close() {
        printFinalState()
        printFinalStateChangePath()
    }

    /**
     * Gets last states of all paths
     */
    private fun lastStates() = _paths
        .map(List<Int>::last)
        .sorted()
        .distinct()

    /**
     * Prints current states
     */
    private fun printStates() = println("Current automaton states: ${lastStates().asString()}")

    /**
     * Prints final state
     *
     * (max state of last ones)
     */
    private fun printFinalState() = println("Final automaton state: q${lastStates().max()}")

    /**
     * Prints state change path
     *
     * (path containing the highest last state)
     */
    private fun printFinalStateChangePath() {
        /** one path in accepting state, get its path as string **/
        val path = _paths.maxBy(List<Int>::last).joinToString(separator = "→", transform = { "q$it" })
        println("State change path: $path")
    }

    /**
     * Gets states as comma-separated string
     */
    private fun Collection<Int>.asString(): String {
        /** if empty or contains only one state, do not use prefix and postfix **/
        val emptyOrSingleton = isEmpty() || size == 1
        val prefix = if (!emptyOrSingleton) "{" else ""
        val postfix = if (!emptyOrSingleton) "}" else ""
        return joinToString(prefix = prefix, postfix = postfix, transform = { "q$it" })
    }
}

/**
 * Reads file from resources directory, splits by separator and runs automaton for each token character
 */
fun main() {
    print("Please enter filename from resources directory: ")
    Scanner(System.`in`).use { scanner ->
        val filename = scanner.next()
        object{}::class.java.getResource(filename)
            ?.readText()
            ?.split(TOKEN_SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.ifEmpty { null }
            ?.forEach { token ->
                println(lineSeparator() + "Reading token: $token")
                Automaton().use { automaton ->
                    token.toCharArray().runCatching {
                        map(Symbol::of).forEach(automaton::consume)
                    }.onFailure { ex ->
                        when (ex) {
                            is SymbolNotAcceptedException -> println(ex.message)
                            else -> println("An error occurred: ${ex.message}")
                        }
                    }
                }
            } ?: println("File not found, is not readable or has not content")
    }
}
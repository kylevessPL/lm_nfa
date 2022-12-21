/**
 * @author Kacper Piasta, 249105
 *
 * difficulty level: 3
 */

import Automaton.Companion.transitionTableMatrix
import Symbol.ONE
import Symbol.THREE
import Symbol.TWO
import Symbol.ZERO
import TransitionTablePrinter.print
import java.io.Closeable
import java.io.File
import java.lang.System.lineSeparator
import java.util.Scanner

/**
 * Type alias for transition table row
 */
typealias Row = Map.Entry<Pair<Int, Symbol>, Set<Int>>

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
 * Utility class to print transition table of an automaton
 */
object TransitionTablePrinter {
    private const val HORIZONTAL_BORDER_KNOT = "+"
    private const val HORIZONTAL_BORDER_PATTERN = "-"
    private const val VERTICAL_BORDER_PATTERN = "|"

    /**
     * Pretty-prints transition table of an automaton (2D array)
     */
    fun Array<Array<String>>.print() = takeIf { isNotEmpty() }?.let {
        val numberOfColumns = maxOfOrNull(Array<String>::size) ?: 0
        val maxColumnWidth = flatten().maxOfOrNull(String::length) ?: 0
        val horizontalBorder = createHorizontalBorder(numberOfColumns, maxColumnWidth)
        println(horizontalBorder)
        forEach { row ->
            println(row.asString(maxColumnWidth))
            println(horizontalBorder)
        }
    } ?: Unit

    /**
     * Converts row to pretty-printed string
     */
    private fun Array<String>.asString(width: Int) = VERTICAL_BORDER_PATTERN.plus(joinToString("") {
        padCell(it, width)
    })

    /**
     * Creates horizontal border for a row
     */
    private fun createHorizontalBorder(numberOfColumns: Int, width: Int) =
        HORIZONTAL_BORDER_KNOT + HORIZONTAL_BORDER_PATTERN
            .repeat(width)
            .plus(HORIZONTAL_BORDER_KNOT)
            .repeat(numberOfColumns)

    /**
     * Pads cell left to particular length
     */
    private fun padCell(text: String, length: Int) = text.padStart(length).plus(VERTICAL_BORDER_PATTERN)
}

/**
 * Actual implementation of NFA
 */
class Automaton : Closeable {
    /**
     * Companion object containing static fields
     */
    companion object {
        /**
         * Delta character of transition function
         */
        private const val DELTA_CHARACTER = "δ"

        /**
         * No-op character for no transition
         */
        private const val NOOP_CHARACTER = "✕"

        /**
         * Set of accepted states
         */
        private val acceptingStates = setOf(9)

        /**
         * Transition table representation as a key-value map
         *
         * Key – pair of unique states with corresponding transition values
         *
         * Value – next state
         */
        private val transitionTable = mapOf(
            Pair(0, ZERO) to setOf(0, 1), Pair(0, ONE) to setOf(0, 2), Pair(0, TWO) to setOf(0, 3), Pair(0, THREE) to setOf(0, 4),
            Pair(1, ZERO) to setOf(5), Pair(1, ONE) to emptySet(), Pair(1, TWO) to emptySet(), Pair(1, THREE) to emptySet(),
            Pair(2, ZERO) to emptySet(), Pair(2, ONE) to setOf(6), Pair(2, TWO) to emptySet(), Pair(2, THREE) to emptySet(),
            Pair(3, ZERO) to emptySet(), Pair(3, ONE) to emptySet(), Pair(3, TWO) to setOf(7), Pair(3, THREE) to emptySet(),
            Pair(4, ZERO) to emptySet(), Pair(4, ONE) to emptySet(), Pair(4, TWO) to emptySet(), Pair(4, THREE) to setOf(8),
            Pair(5, ZERO) to setOf(9), Pair(5, ONE) to emptySet(), Pair(5, TWO) to emptySet(), Pair(5, THREE) to emptySet(),
            Pair(6, ZERO) to emptySet(), Pair(6, ONE) to setOf(9), Pair(6, TWO) to emptySet(), Pair(6, THREE) to emptySet(),
            Pair(7, ZERO) to emptySet(), Pair(7, ONE) to emptySet(), Pair(7, TWO) to setOf(9), Pair(7, THREE) to emptySet(),
            Pair(8, ZERO) to emptySet(), Pair(8, ONE) to emptySet(), Pair(8, TWO) to emptySet(), Pair(8, THREE) to setOf(9),
            Pair(9, ZERO) to setOf(9), Pair(9, ONE) to setOf(9), Pair(9, TWO) to setOf(9), Pair(9, THREE) to setOf(9)
        )

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

        /**
         * Representation of transition table as 2D array
         */
        @JvmField
        val transitionTableMatrix = run {
            fun List<Row>.mapRows() = (listOf(listOf(first().key.first)) + map(Row::value))
                .map { it.asString() }
                .map { it.ifEmpty { NOOP_CHARACTER } }
                .toTypedArray()

            val header = (listOf(DELTA_CHARACTER) + transitionTable.keys
                .map(Pair<Int, Symbol>::second)
                .map(Symbol::toString)
                .distinct())
                .toTypedArray()
            val rows = transitionTable.entries
                .chunked(4)
                .map(List<Row>::mapRows)
                .toTypedArray()
            arrayOf(header).plus(rows)
        }
    }

    /**
     * Map of symbols with number of their temporary read occurences
     */
    private val _tempOccurrences = mutableMapOf<Symbol, Int>()

    /**
     * Map of symbols with number of their triplet occurences
     */
    private val _tripletOccurrences = mutableMapOf<Symbol, Int>()

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
        printCurrentStates()
    }

    /**
     * Consumes symbols and transitions to next states if applicable
     */
    fun consume(symbol: Symbol) {
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
        fun incrementOccurrence() {
            /**
             * Utility function to increment value of specific map key
             */
            fun MutableMap<Symbol, Int>.incrementCurrent() = merge(symbol, 1) { a: Int, b: Int -> a + b }

            /** clear temporary counter of other symbols, increment counter and triplet occurrences for current one **/
            with(_tempOccurrences) {
                entries.removeIf { it.key != symbol }
                incrementCurrent()
                get(symbol)?.takeIf { it >= 3 }?.let {
                    _tripletOccurrences.incrementCurrent()
                }
            }
        }

        /**
         * Prints information about how many times symbol was tripled
         */
        fun printOccurrences() = _tripletOccurrences.forEach { (symbol, occurrences) ->
            println("Symbol $symbol was tripled $occurrences times already")
        }

        /**
         * Updates state paths
         */
        fun List<List<Int>>.updatePaths() = ifEmpty { onHold = true; null }
            ?.takeIf { !onHold }
            ?.let {
                _paths = this
                incrementOccurrence()
            }

        println("Reading symbol: $symbol")

        /** perform state transition, print current states and symbol occurrences if automaton is in accepted state **/
        _paths.takeIf { !onHold }
            ?.map(List<Int>::nextState)
            ?.flatten()
            ?.filterNot(List<Int>::isEmpty)
            ?.updatePaths()
            ?.let { !onHold && isAccepting() }
            ?.run {
                if (this) printOccurrences()
                printCurrentStates()
            }
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
    private fun printCurrentStates() = println("Current automaton states: ${lastStates().asString()}")

    /**
     * Prints final state
     *
     * (max state of last ones)
     */
    private fun printFinalState() {
        val state = lastStates().maxOrNull()
        val label = if (acceptingStates.contains(state)) "accepting" else "rejecting"
        println("Final automaton state: q$state ($label)")
    }

    /**
     * Prints state change path
     *
     * (path containing the highest last state)
     */
    private fun printFinalStateChangePath() {
        /** one path in accepting state, get its path as string **/
        val path = _paths
            .sortedByDescending(List<Int>::sum)
            .maxByOrNull(List<Int>::last)
            ?.joinToString(separator = "→", transform = { "q$it" })
        println("State change path: $path")
    }
}

/**
 * Reads file from user-defined directory, splits by separator and runs automaton for each token character
 *
 */
fun main() {
    println("Transition table:")
    transitionTableMatrix.print()
    print("Please enter file path: ")
    Scanner(System.`in`).use { scanner ->
        val filepath = scanner.next()
        File(filepath).takeIf { it.isFile }
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
            } ?: println("File not found, is not readable or has no content")
    }
}

package com.getbouncer.cardscan.base.ml.ssd

import androidx.test.filters.SmallTest
import com.getbouncer.cardscan.base.util.filterByIndexes
import com.getbouncer.cardscan.base.util.flatten
import com.getbouncer.cardscan.base.util.reshape
import com.getbouncer.cardscan.base.util.updateEach
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.milliseconds

@ExperimentalTime
class ArrayExtensionsTest {

    @Test
    @SmallTest
    fun reshape() {
        val matrix = generateTestMatrix(1_000, 10_000)
        val reshaped: Array<FloatArray>
        val executionTime = measureTime { reshaped = matrix.reshape(100) }

        assertEquals(100_000, reshaped.size)
        assertEquals(100, reshaped[0].size)
        assertEquals(matrix[3][5], reshaped[30][5])

        assertTrue(executionTime < 60.milliseconds, "Array reshape is too slow @ $executionTime")
    }

    @Test
    @SmallTest
    fun updateEach_array() {
        val array = (0 until 1_000_000).toList().toTypedArray()
        val executionTime = measureTime { array.updateEach { it + 1 } }

        Assert.assertArrayEquals((1 .. 1_000_000).toList().toTypedArray(), array)

        assertTrue(executionTime < 35.milliseconds, "Array updateEach is too slow @ $executionTime")
    }

    @Test
    @SmallTest
    fun updateEach_floatArray() {
        val array = generateTestFloatArray(1_000_000)
        val originalValue = array[100]
        val executionTime = measureTime { array.updateEach { it + 0.1F } }

        assertEquals(originalValue + 0.1F, array[100])

        assertTrue(executionTime < 35.milliseconds, "Array updateEach is too slow @ $executionTime")
    }

    @Test
    @SmallTest
    fun filterByIndexes() {
        val array = generateTestFloatArray(1_000_000)
        val selectedIndexes = arrayOf(100).toIntArray() + IntArray(10_000) { Random.nextInt(array.size) }
        val originalValue = array[100]

        val filteredArray: FloatArray
        val executionTime = measureTime { filteredArray = array.filterByIndexes(selectedIndexes) }

        assertEquals(originalValue, filteredArray[0])

        assertTrue(executionTime < 1.milliseconds, "Array filterByIndexes is too slow @ $executionTime")
    }

    @Test
    fun flatten() {
        val matrix = generateTestMatrix(1_000, 10_000)
        val flattened: FloatArray
        val executionTime = measureTime { flattened = matrix.flatten() }

        assertEquals(matrix[3][5], flattened[3005])

        assertTrue(executionTime < 30.milliseconds, "Array flatten is too slow @ $executionTime")
    }

    /**
     * Generate a test matrix.
     */
    private fun generateTestMatrix(width: Int, height: Int) =
        Array(height) { generateTestFloatArray(width) }

    /**
     * Generate a test array.
     */
    private fun generateTestFloatArray(length: Int) = FloatArray(length) { Random.nextFloat() }
}
package com.getbouncer.cardscan.base.util

import com.getbouncer.cardscan.base.domain.FixedMemorySize
import java.util.Vector

class MessageBus<E : FixedMemorySize>(private var maximumSizeInBytes: Int) {

    private val vector: Vector<E> by lazy { Vector<E>() }

    private var sizeInBytes = 0

    @Synchronized
    fun publish(item: E) {
        sizeInBytes += item.sizeInBytes
        cullOverSize()
        vector.addElement(item)
    }

    @Synchronized
    fun popMessage(): E? =
        if (notEmpty()) {
            val item = vector.elementAt(vector.size - 1)
            vector.removeElementAt(vector.size - 1)
            sizeInBytes -= item.sizeInBytes
            item
        } else {
            null
        }

    fun notEmpty(): Boolean = vector.size > 0

    @Synchronized
    private fun cullOverSize() {
        var sizeToRemove = 0
        val iterator = vector.iterator()
        while (sizeInBytes - sizeToRemove > maximumSizeInBytes && iterator.hasNext()) {
            sizeToRemove += iterator.next().sizeInBytes
            iterator.remove()
        }
    }

}
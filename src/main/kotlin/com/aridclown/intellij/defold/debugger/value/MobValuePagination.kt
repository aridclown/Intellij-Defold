package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.DefoldConstants.TABLE_PAGE_SIZE

/**
 * Pure helper that computes paging ranges for debugger value lists.
 */
object MobValuePagination {
    data class Range(val from: Int, val to: Int, val remaining: Int)

    fun range(totalSize: Int, from: Int, pageSize: Int = TABLE_PAGE_SIZE): Range? {
        if (totalSize <= 0 || from >= totalSize) return null
        val upperBound = from.coerceAtLeast(0)
        val to = (upperBound + pageSize).coerceAtMost(totalSize)
        val remaining = (totalSize - to).coerceAtLeast(0)
        return Range(upperBound, to, remaining)
    }
}


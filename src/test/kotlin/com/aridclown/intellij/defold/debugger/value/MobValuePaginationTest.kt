package com.aridclown.intellij.defold.debugger.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MobValuePaginationTest {

    @Test
    fun `range is null for empty collections`() {
        assertThat(MobValuePagination.range(0, 0)).isNull()
        assertThat(MobValuePagination.range(-1, 0)).isNull()
    }

    @Test
    fun `range respects table page size upper bound`() {
        val range = MobValuePagination.range(totalSize = 25, from = 0, pageSize = 10)

        assertThat(range).isNotNull
        assertThat(range!!.from).isZero()
        assertThat(range.to).isEqualTo(10)
        assertThat(range.remaining).isEqualTo(15)
    }

    @Test
    fun `range clamps negative start`() {
        val range = MobValuePagination.range(totalSize = 4, from = -3, pageSize = 2)

        assertThat(range).isNotNull
        assertThat(range!!.from).isZero()
        assertThat(range.to).isEqualTo(2)
        assertThat(range.remaining).isEqualTo(2)
    }

    @Test
    fun `range returns null when from is past total size`() {
        assertThat(MobValuePagination.range(totalSize = 5, from = 5)).isNull()
        assertThat(MobValuePagination.range(totalSize = 5, from = 6)).isNull()
    }
}


package com.studibox.app

import com.studibox.app.util.SubgroupFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class SubgroupFormatTest {

    @Test
    fun fullLabelNeverDuplicatesTheWord() {
        assertEquals("1 подгруппа", SubgroupFormat.fullLabel("1"))
        assertEquals("1 подгруппа", SubgroupFormat.fullLabel("1 подгруппа"))
        assertEquals("2 подгруппа", SubgroupFormat.fullLabel("2 подгруппа"))
        assertEquals("2 подгруппа", SubgroupFormat.fullLabel(" 2 "))
    }

    @Test
    fun shortLabelIsJustTheNumber() {
        assertEquals("1", SubgroupFormat.shortLabel("1"))
        assertEquals("1", SubgroupFormat.shortLabel("1 подгруппа"))
        assertEquals("2", SubgroupFormat.shortLabel("подгруппа 2"))
    }
}

package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TriggerHandleCodecTest {
    @Test
    fun encodeDecode_nullEdgeWidth_roundTripsAsInherit() {
        val handle = TriggerHandle.default().copy(edgeWidthDp = null)
        val decoded = TriggerHandleCodec.decode(TriggerHandleCodec.encode(handle))
        assertNull(decoded?.edgeWidthDp)
    }

    @Test
    fun encodeDecode_explicitZero_roundTrips() {
        val handle = TriggerHandle.default().copy(edgeWidthDp = 0f)
        val decoded = TriggerHandleCodec.decode(TriggerHandleCodec.encode(handle))
        assertEquals(0f, decoded?.edgeWidthDp)
    }

    @Test
    fun decode_legacyZeroMeansInherit_mapsStoredZeroToNull() {
        val handle = TriggerHandle.default().copy(edgeWidthDp = 0f)
        val decoded = TriggerHandleCodec.decode(
            TriggerHandleCodec.encode(handle),
            legacyZeroMeansInherit = true,
        )
        assertNull(decoded?.edgeWidthDp)
    }

    @Test
    fun decode_emptyEdgeWidthPart_meansInherit() {
        assertNull(TriggerHandleCodec.decodeEdgeWidthDp("", legacyZeroMeansInherit = false))
        assertNull(TriggerHandleCodec.decodeEdgeWidthDp(null, legacyZeroMeansInherit = false))
    }
}

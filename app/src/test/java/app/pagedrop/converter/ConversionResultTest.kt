package app.pagedrop.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionResultTest {

    @Test
    fun success_createsSuccessfulResult() {
        val r = ConversionResult(success = true)
        assertTrue(r.success)
        assertEquals(null, r.coverBytes)
        assertEquals(null, r.kindleUuid)
    }

    @Test
    fun failure_createsFailedResult() {
        val r = ConversionResult(success = false)
        assertFalse(r.success)
    }

    @Test
    fun withCoverBytes() {
        val bytes = byteArrayOf(1, 2, 3)
        val r = ConversionResult(success = true, coverBytes = bytes)
        assertTrue(r.coverBytes.contentEquals(bytes))
    }

    @Test
    fun withKindleUuid() {
        val r = ConversionResult(success = true, kindleUuid = "abc-123")
        assertEquals("abc-123", r.kindleUuid)
    }

    @Test
    fun equality_sameData_areEqual() {
        val a = ConversionResult(true, byteArrayOf(1), "uuid")
        val b = ConversionResult(true, byteArrayOf(1), "uuid")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equality_differentSuccess_notEqual() {
        assertNotEquals(
            ConversionResult(success = true),
            ConversionResult(success = false)
        )
    }

    @Test
    fun equality_differentCoverBytes_notEqual() {
        assertNotEquals(
            ConversionResult(true, byteArrayOf(1)),
            ConversionResult(true, byteArrayOf(2))
        )
    }

    @Test
    fun equality_differentUuid_notEqual() {
        assertNotEquals(
            ConversionResult(true, kindleUuid = "a"),
            ConversionResult(true, kindleUuid = "b")
        )
    }

    @Test
    fun equality_nullCoverBytes() {
        assertEquals(
            ConversionResult(true, null),
            ConversionResult(true, null)
        )
    }

    @Test
    fun hashCode_consistent() {
        val r = ConversionResult(true, byteArrayOf(42), "test")
        assertEquals(r.hashCode(), r.hashCode())
    }
}

package app.pagedrop.server

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageDropApiTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun healthResponse_deserialization() {
        val raw = """{"ok":true,"version":"0.1.0","workers":{"calibreCli":true,"articleExtractor":false},"limits":{"maxUploadBytes":50000000,"maxConcurrentJobs":2}}"""
        val health = json.decodeFromString<HealthResponse>(raw)
        assertTrue(health.ok)
        assertEquals("0.1.0", health.version)
        assertEquals(true, health.workers["calibreCli"])
        assertEquals(false, health.workers["articleExtractor"])
        assertEquals(50000000L, health.limits?.maxUploadBytes)
        assertEquals(2, health.limits?.maxConcurrentJobs)
    }

    @Test
    fun healthResponse_withoutLimits() {
        val raw = """{"ok":false,"version":"0.0.0","workers":{}}"""
        val health = json.decodeFromString<HealthResponse>(raw)
        assertFalse(health.ok)
        assertEquals("0.0.0", health.version)
        assertNull(health.limits)
    }

    @Test
    fun convertRequest_serialization() {
        val req = ConvertRequest(
            targetProfile = "kindle-stock",
            outputFormat = "mobi",
            includeCover = true,
            title = "Test",
            author = "Author"
        )
        val serialized = json.encodeToString(ConvertRequest.serializer(), req)
        assertTrue(serialized.contains("kindle-stock"))
        assertTrue(serialized.contains("mobi"))
        assertTrue(serialized.contains("Test"))
    }

    @Test
    fun convertRequest_defaultValues() {
        val req = ConvertRequest()
        assertEquals("kindle-stock", req.targetProfile)
        assertEquals("mobi", req.outputFormat)
        assertEquals(true, req.includeCover)
        assertNull(req.title)
        assertNull(req.author)
    }

    @Test
    fun articleConvertRequest_serialization() {
        val req = ArticleConvertRequest(
            url = "https://example.com/article",
            targetProfile = "koreader",
            outputFormat = "azw3",
            includeCover = false
        )
        val serialized = json.encodeToString(ArticleConvertRequest.serializer(), req)
        assertTrue(serialized.contains("https://example.com/article"))
        assertTrue(serialized.contains("koreader"))
        assertTrue(serialized.contains("azw3"))
    }

    @Test
    fun articleConvertRequest_defaults() {
        val req = ArticleConvertRequest(url = "https://test.com")
        assertEquals("kindle-stock", req.targetProfile)
        assertEquals("mobi", req.outputFormat)
        assertEquals(true, req.includeCover)
    }

    @Test
    fun jobResponse_deserialization() {
        val raw = """{"jobId":"job_123","status":"queued"}"""
        val job = json.decodeFromString<JobResponse>(raw)
        assertEquals("job_123", job.jobId)
        assertEquals("queued", job.status)
    }

    @Test
    fun jobStatus_deserialization() {
        val raw = """{"jobId":"job_123","status":"complete","progress":100,"result":{"artifactId":"artifact_123","coverId":null,"fileName":"test.mobi","format":"MOBI","title":"Test","author":"Author"}}"""
        val status = json.decodeFromString<JobStatus>(raw)
        assertEquals("complete", status.status)
        assertEquals(100, status.progress)
        assertNotNull(status.result)
        assertEquals("artifact_123", status.result?.artifactId)
        assertNull(status.result?.coverId)
    }

    @Test
    fun jobStatus_progressUpdate() {
        val raw = """{"jobId":"job_456","status":"processing","progress":50}"""
        val status = json.decodeFromString<JobStatus>(raw)
        assertEquals("processing", status.status)
        assertEquals(50, status.progress)
        assertNull(status.result)
    }

    @Test
    fun serverSettings_defaultState() {
        assertEquals(false, false) // disabled by default
        assertEquals("", "") // no server URL
        assertEquals("", "") // no API token
    }

    @Test
    fun jobResult_withWarnings() {
        val raw = """{"artifactId":"a","fileName":"f.mobi","format":"MOBI","title":"T","author":"A","warnings":["Large file","Few images"],"logs":["Converted"]}"""
        val result = json.decodeFromString<JobResult>(raw)
        assertEquals(2, result.warnings.size)
        assertEquals("Large file", result.warnings[0])
        assertEquals("Converted", result.logs[0])
    }
}

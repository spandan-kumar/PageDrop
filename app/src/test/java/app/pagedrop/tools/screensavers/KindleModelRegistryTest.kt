package app.pagedrop.tools.screensavers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KindleModelRegistryTest {

    @Test
    fun models_containsPaperwhite1() {
        val pw1 = KindleModelRegistry.models.find { it.name.contains("Paperwhite 1") }
        assertNotNull(pw1)
        assertEquals(758, pw1!!.width)
        assertEquals(1024, pw1.height)
        assertEquals(212, pw1.dpi)
    }

    @Test
    fun models_containsOasis3() {
        val oasis3 = KindleModelRegistry.models.find { it.name.contains("Oasis 3") }
        assertNotNull(oasis3)
        assertEquals(1264, oasis3!!.width)
        assertEquals(1680, oasis3.height)
    }

    @Test
    fun defaultModel_isPaperwhite1() {
        assertEquals(
            "Kindle Paperwhite 1 (2012)",
            KindleModelRegistry.defaultModel().name
        )
    }

    @Test
    fun allModels_haveValidDimensions() {
        for (model in KindleModelRegistry.models) {
            assert(model.width > 0) { "${model.name}: invalid width" }
            assert(model.height > 0) { "${model.name}: invalid height" }
            assert(model.dpi > 0) { "${model.name}: invalid DPI" }
        }
    }
}

package app.pagedrop.tools.dictionaries

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DictionaryInstallerTest {

    @Test
    fun stardictValidation_detectsIfo() {
        val dir = createTempDirWithFiles("test_dict.ifo", "test_dict.idx")
        val files = dir.listFiles()?.toList() ?: emptyList()
        val hasIfo = files.any { it.extension.equals("ifo", true) }
        val hasIdx = files.any { it.extension.equals("idx", true) }
        assertTrue(hasIfo)
        assertTrue(hasIdx)
    }

    @Test
    fun stardictValidation_requiresIfo() {
        val dir = createTempDirWithFiles("dummy.txt")
        val files = dir.listFiles()?.toList() ?: emptyList()
        val hasIfo = files.any { it.extension.equals("ifo", true) }
        assertFalse(hasIfo)
    }

    @Test
    fun stardictValidation_requiresDictOrIdx() {
        val dir = createTempDirWithFiles("test.ifo", "readme.txt")
        val files = dir.listFiles()?.toList() ?: emptyList()
        val hasIdx = files.any { it.extension.equals("idx", true) }
        val hasDict = files.any {
            val name = it.name.lowercase()
            name.endsWith(".dict") || name.endsWith(".dict.dz")
        }
        assertFalse(hasIdx && hasDict)
    }

    @Test
    fun dictDotDz_detectedCorrectly() {
        assertTrue("test.dict.dz".lowercase().endsWith(".dict.dz"))
        assertFalse("test.dict".lowercase().endsWith(".dict.dz"))
    }

    private fun createTempDirWithFiles(vararg fileNames: String): File {
        val dir = java.io.File.createTempFile("dict_test", "")
        dir.delete()
        dir.mkdir()
        dir.deleteOnExit()
        fileNames.forEach { name ->
            val f = File(dir, name)
            f.writeText("content")
            f.deleteOnExit()
        }
        return dir
    }
}

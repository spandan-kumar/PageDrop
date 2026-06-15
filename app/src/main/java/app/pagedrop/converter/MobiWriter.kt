package app.pagedrop.converter

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a valid MOBI file in PDB (Palm Database) format.
 *
 * The output targets Kindle Paperwhite 1st Gen (2012) compatibility:
 * - No compression (PalmDOC type 1)
 * - UTF-8 text encoding
 * - MOBI header version 6
 * - EXTH metadata for author and title
 *
 * Structure:
 *   PDB Header → Record Offset Table → Record 0 (PalmDOC + MOBI + EXTH + title) →
 *   Text Records → Image Records
 */
class MobiWriter(
    private val title: String,
    private val author: String,
    private val htmlContent: ByteArray,
    private val coverImage: ByteArray? = null,
    private val images: List<ByteArray> = emptyList()
) {
    companion object {
        private const val TAG = "MobiWriter"
        private const val RECORD_SIZE = 4096
        private const val PDB_HEADER_SIZE = 78
        // MOBI header: 232 bytes from "MOBI" to end (verified byte-by-byte)
        private const val MOBI_HEADER_LENGTH = 232
        /** Seconds between 1904-01-01 and 1970-01-01 */
        private const val PDB_EPOCH_OFFSET = 2082844800L
    }

    fun write(outputFile: File) {
        val textRecords = splitIntoRecords(htmlContent, RECORD_SIZE)
        val textRecordCount = textRecords.size

        val allImages = mutableListOf<ByteArray>()
        if (coverImage != null) allImages.add(coverImage)
        allImages.addAll(images)

        val firstImageIndex = if (allImages.isNotEmpty()) textRecordCount + 1 else 0
        val firstNonBookIndex = textRecordCount + 1
        val totalRecords = 1 + textRecordCount + allImages.size

        val record0 = buildRecord0(
            textLength = htmlContent.size,
            textRecordCount = textRecordCount,
            firstImageIndex = firstImageIndex,
            firstNonBookIndex = firstNonBookIndex
        )

        val allRecordData = mutableListOf<ByteArray>()
        allRecordData.add(record0)
        allRecordData.addAll(textRecords)
        allRecordData.addAll(allImages)

        val recordOffsetTableSize = totalRecords * 8 + 2
        var currentOffset = PDB_HEADER_SIZE + recordOffsetTableSize

        val offsets = mutableListOf<Int>()
        for (recordData in allRecordData) {
            offsets.add(currentOffset)
            currentOffset += recordData.size
        }

        Log.d(TAG, "Writing MOBI: $totalRecords records, ${textRecords.size} text, ${allImages.size} images, record0=${record0.size} bytes")

        FileOutputStream(outputFile).use { fos ->
            DataOutputStream(fos).use { dos ->
                writePdbHeader(dos, totalRecords)

                for (i in 0 until totalRecords) {
                    dos.writeInt(offsets[i])
                    dos.writeByte(0)
                    dos.writeByte(0)
                    dos.writeShort(i)
                }
                dos.writeShort(0)

                for (recordData in allRecordData) {
                    dos.write(recordData)
                }
                dos.flush()
            }
        }

        Log.d(TAG, "MOBI written: ${outputFile.length()} bytes → ${outputFile.absolutePath}")
    }

    private fun writePdbHeader(dos: DataOutputStream, numRecords: Int) {
        val safeName = title.replace(Regex("[^\\x20-\\x7E]"), "")
        val nameBytes = safeName.toByteArray(Charsets.US_ASCII)
        val nameField = ByteArray(32)
        System.arraycopy(nameBytes, 0, nameField, 0, minOf(nameBytes.size, 31))
        dos.write(nameField)                          // 32

        dos.writeShort(0)                             // attributes: 2
        dos.writeShort(0)                             // version: 2

        val nowPdb = (System.currentTimeMillis() / 1000L) + PDB_EPOCH_OFFSET
        dos.writeInt(nowPdb.toInt())                  // creationDate: 4
        dos.writeInt(nowPdb.toInt())                  // modificationDate: 4
        dos.writeInt(0)                               // lastBackup: 4
        dos.writeInt(0)                               // modificationNumber: 4
        dos.writeInt(0)                               // appInfoID: 4
        dos.writeInt(0)                               // sortInfoID: 4

        dos.write("BOOK".toByteArray(Charsets.US_ASCII)) // type: 4
        dos.write("MOBI".toByteArray(Charsets.US_ASCII)) // creator: 4

        dos.writeInt(2 * numRecords + 1)              // uniqueIDseed: 4
        dos.writeInt(0)                               // nextRecordListID: 4
        dos.writeShort(numRecords)                    // numRecords: 2
        // Total: 78 bytes ✓
    }

    private fun buildRecord0(
        textLength: Int,
        textRecordCount: Int,
        firstImageIndex: Int,
        firstNonBookIndex: Int
    ): ByteArray {
        val baos = ByteArrayOutputStream(1024)
        val dos = DataOutputStream(baos)

        // ── PalmDOC Header (16 bytes) ──
        dos.writeShort(1)                    // compression: no compression
        dos.writeShort(0)                    // unused
        dos.writeInt(textLength)             // text length
        dos.writeShort(textRecordCount)      // record count
        dos.writeShort(RECORD_SIZE)          // record size
        dos.writeInt(0)                      // current position
        // PalmDOC total: 16 bytes ✓

        // ── MOBI Header (232 bytes) ──
        // Build EXTH first so we know the fullNameOffset
        val exthData = buildExthHeader()
        val fullNameBytes = title.toByteArray(Charsets.UTF_8)
        val fullNameOffset = 16 + MOBI_HEADER_LENGTH + exthData.size

        dos.write("MOBI".toByteArray(Charsets.US_ASCII))  // +4  = 4
        dos.writeInt(MOBI_HEADER_LENGTH)                   // +4  = 8
        dos.writeInt(2)                                    // +4  = 12  mobiType (Mobipocket Book)
        dos.writeInt(65001)                                // +4  = 16  textEncoding (UTF-8)
        dos.writeInt(title.hashCode())                     // +4  = 20  uniqueID
        dos.writeInt(6)                                    // +4  = 24  fileVersion

        // 4 index fields = 0xFFFFFFFF
        dos.writeInt(-1)  // orthoIndex                    // +4  = 28
        dos.writeInt(-1)  // inflectionIndex               // +4  = 32
        dos.writeInt(-1)  // indexNames                    // +4  = 36
        dos.writeInt(-1)  // indexKeys                     // +4  = 40

        // 6 extra index fields = 0xFFFFFFFF
        repeat(6) { dos.writeInt(-1) }                     // +24 = 64

        dos.writeInt(firstNonBookIndex)                    // +4  = 68
        dos.writeInt(fullNameOffset)                        // +4  = 72
        dos.writeInt(fullNameBytes.size)                    // +4  = 76
        dos.writeInt(0x09)                                 // +4  = 80  locale (English)
        dos.writeInt(0)                                    // +4  = 84  inputLanguage
        dos.writeInt(0)                                    // +4  = 88  outputLanguage
        dos.writeInt(6)                                    // +4  = 92  minVersion
        dos.writeInt(if (firstImageIndex > 0)              // +4  = 96  firstImageIndex
            firstImageIndex else 0xFFFFFFFF.toInt())

        // Huffman fields (4 fields)
        dos.writeInt(0)                                    // +4  = 100 huffmanRecordOffset
        dos.writeInt(0)                                    // +4  = 104 huffmanRecordCount
        dos.writeInt(0)                                    // +4  = 108 huffmanTableOffset
        dos.writeInt(0)                                    // +4  = 112 huffmanTableLength

        dos.writeInt(0x40)                                 // +4  = 116 exthFlags (has EXTH)

        // 32 bytes unknown/reserved
        dos.write(ByteArray(32))                           // +32 = 148

        // DRM fields
        dos.writeInt(-1)                                   // +4  = 152 drmOffset (0xFFFFFFFF = no DRM)
        dos.writeInt(0)                                    // +4  = 156 drmCount
        dos.writeInt(0)                                    // +4  = 160 drmSize
        dos.writeInt(0)                                    // +4  = 164 drmFlags

        // 8 bytes reserved
        dos.write(ByteArray(8))                            // +8  = 172

        dos.writeShort(1)                                  // +2  = 174 firstContentRecord
        dos.writeShort(textRecordCount)                    // +2  = 176 lastContentRecord

        dos.writeInt(1)                                    // +4  = 180 unknown

        dos.writeInt(-1)                                   // +4  = 184 fcisRecordNumber
        dos.writeInt(-1)                                   // +4  = 188 fcisRecordCount
        dos.writeInt(-1)                                   // +4  = 192 flisRecordNumber
        dos.writeInt(-1)                                   // +4  = 196 flisRecordCount

        // 8 bytes unknown
        dos.write(ByteArray(8))                            // +8  = 204

        dos.writeInt(-1)                                   // +4  = 208 unknown
        dos.writeInt(0)                                    // +4  = 212 unknown
        dos.writeInt(-1)                                   // +4  = 216 unknown
        dos.writeInt(-1)                                   // +4  = 220 unknown

        dos.writeInt(0)                                    // +4  = 224 extraRecordDataFlags
        dos.writeInt(-1)                                   // +4  = 228 indxRecordOffset
        dos.writeInt(-1)                                   // +4  = 232 unknown (MOBI v6 padding)
        // MOBI header total: 232 bytes ✓

        // ── EXTH Header ──
        dos.write(exthData)

        // ── Full book title ──
        dos.write(fullNameBytes)

        dos.flush()

        // Pad record 0 to 4-byte boundary
        return padTo4(baos.toByteArray())
    }

    private fun buildExthHeader(): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val dos = DataOutputStream(baos)

        val records = mutableListOf<ByteArray>()
        records.add(buildExthRecord(100, author.toByteArray(Charsets.UTF_8)))   // author
        records.add(buildExthRecord(503, title.toByteArray(Charsets.UTF_8)))    // updated title

        val recordsData = ByteArrayOutputStream()
        for (record in records) {
            recordsData.write(record)
        }
        val recordsBytes = recordsData.toByteArray()

        val rawSize = 12 + recordsBytes.size
        val paddedSize = ((rawSize + 3) / 4) * 4

        dos.write("EXTH".toByteArray(Charsets.US_ASCII))
        dos.writeInt(paddedSize)
        dos.writeInt(records.size)
        dos.write(recordsBytes)

        val padding = paddedSize - rawSize
        if (padding > 0) dos.write(ByteArray(padding))

        dos.flush()
        return baos.toByteArray()
    }

    private fun buildExthRecord(type: Int, data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream(data.size + 8)
        val dos = DataOutputStream(baos)
        dos.writeInt(type)
        dos.writeInt(data.size + 8)
        dos.write(data)
        dos.flush()
        return baos.toByteArray()
    }

    private fun splitIntoRecords(data: ByteArray, chunkSize: Int): List<ByteArray> {
        val records = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            records.add(data.copyOfRange(offset, end))
            offset = end
        }
        if (records.isEmpty()) records.add(ByteArray(0))
        return records
    }

    private fun padTo4(data: ByteArray): ByteArray {
        val remainder = data.size % 4
        if (remainder == 0) return data
        val padded = ByteArray(data.size + (4 - remainder))
        System.arraycopy(data, 0, padded, 0, data.size)
        return padded
    }
}

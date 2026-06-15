package app.pagedrop.converter

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a valid MOBI file using the PDB/PalmDOC/MOBI structure.
 *
 * Header layout verified byte-for-byte against a Calibre-generated MOBI
 * ("The Geography of Bliss") that opens on Kindle Paperwhite 1st Gen.
 *
 * Record layout:
 *   0          = Header record (PalmDOC + MOBI + EXTH + title)
 *   1..N       = Text records
 *   N+1..M     = Image records (if any)
 *   M+1        = FLIS record
 *   M+2        = FCIS record
 *   M+3        = EOF record
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
        private const val MOBI_HEADER_LENGTH = 232
        private const val PDB_EPOCH_OFFSET = 2082844800L

        /** FLIS record: fixed 36-byte structure (from reference file) */
        private val FLIS_RECORD = byteArrayOf(
            0x46, 0x4C, 0x49, 0x53,  // "FLIS"
            0x00, 0x00, 0x00, 0x08,  // fixed
            0x00, 0x41, 0x00, 0x00,  // fixed
            0x00, 0x00, 0x00, 0x00,  // fixed
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00, 0x01, 0x00, 0x03,  // fixed
            0x00, 0x00, 0x00, 0x03,  // fixed
            0x00, 0x00, 0x00, 0x01,  // fixed
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )

        /** EOF record: fixed 4-byte marker */
        private val EOF_RECORD = byteArrayOf(
            0xE9.toByte(), 0x8E.toByte(), 0x0D, 0x0A
        )
    }

    fun write(outputFile: File) {
        val textRecords = splitIntoRecords(htmlContent, RECORD_SIZE)
        val textRecordCount = textRecords.size

        val allImages = mutableListOf<ByteArray>()
        if (coverImage != null) allImages.add(coverImage)
        allImages.addAll(images)

        val firstImageIndex = if (allImages.isNotEmpty()) textRecordCount + 1 else 0xFFFFFFFF.toInt()
        val lastContentRecord = if (allImages.isNotEmpty()) textRecordCount + allImages.size else textRecordCount

        // FLIS, FCIS, EOF come after text + images
        val flisRecordIndex = 1 + textRecordCount + allImages.size
        val fcisRecordIndex = flisRecordIndex + 1
        val eofRecordIndex = fcisRecordIndex + 1
        val firstNonBookIndex = flisRecordIndex
        val totalRecords = eofRecordIndex + 1  // 0-indexed, so +1

        // Build FCIS record (references text length)
        val fcisRecord = buildFcisRecord(htmlContent.size)

        val record0 = buildRecord0(
            textLength = htmlContent.size,
            textRecordCount = textRecordCount,
            firstImageIndex = firstImageIndex,
            firstNonBookIndex = firstNonBookIndex,
            lastContentRecord = lastContentRecord,
            flisRecordIndex = flisRecordIndex,
            fcisRecordIndex = fcisRecordIndex,
        )

        // Assemble all records in order
        val allRecordData = mutableListOf<ByteArray>()
        allRecordData.add(record0)
        allRecordData.addAll(textRecords)
        allRecordData.addAll(allImages)
        allRecordData.add(FLIS_RECORD)
        allRecordData.add(fcisRecord)
        allRecordData.add(EOF_RECORD)

        // Calculate offsets
        val recordOffsetTableSize = totalRecords * 8 + 2
        var currentOffset = PDB_HEADER_SIZE + recordOffsetTableSize
        val offsets = mutableListOf<Int>()
        for (recordData in allRecordData) {
            offsets.add(currentOffset)
            currentOffset += recordData.size
        }

        Log.d(TAG, "Writing MOBI: records=$totalRecords text=$textRecordCount images=${allImages.size} record0=${record0.size}B")

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

    /** PDB Header: 78 bytes. */
    private fun writePdbHeader(dos: DataOutputStream, numRecords: Int) {
        val safeName = title.take(31).replace(Regex("[^\\x20-\\x7E]"), "_")
        val nameBytes = safeName.toByteArray(Charsets.US_ASCII)
        val nameField = ByteArray(32)
        System.arraycopy(nameBytes, 0, nameField, 0, minOf(nameBytes.size, 31))
        dos.write(nameField)

        dos.writeShort(0)
        dos.writeShort(0)
        val nowPdb = (System.currentTimeMillis() / 1000L) + PDB_EPOCH_OFFSET
        dos.writeInt(nowPdb.toInt())
        dos.writeInt(nowPdb.toInt())
        dos.writeInt(0)
        dos.writeInt(0)
        dos.writeInt(0)
        dos.writeInt(0)
        dos.write("BOOK".toByteArray(Charsets.US_ASCII))
        dos.write("MOBI".toByteArray(Charsets.US_ASCII))
        dos.writeInt(2 * numRecords + 1)
        dos.writeInt(0)
        dos.writeShort(numRecords)
    }

    /**
     * Record 0: PalmDOC(16) + MOBI(232) + EXTH + title + padding.
     * All offsets from start of record 0.
     */
    private fun buildRecord0(
        textLength: Int,
        textRecordCount: Int,
        firstImageIndex: Int,
        firstNonBookIndex: Int,
        lastContentRecord: Int,
        flisRecordIndex: Int,
        fcisRecordIndex: Int,
    ): ByteArray {
        val baos = ByteArrayOutputStream(1024)
        val dos = DataOutputStream(baos)

        val exthData = buildExthHeader()
        val fullNameBytes = title.toByteArray(Charsets.UTF_8)
        val fullNameOffset = 16 + MOBI_HEADER_LENGTH + exthData.size

        // ── PalmDOC Header (16 bytes) ──
        dos.writeShort(1)                     //  0: compression (1=none)
        dos.writeShort(0)                     //  2: unused
        dos.writeInt(textLength)              //  4: text length
        dos.writeShort(textRecordCount)       //  8: record count
        dos.writeShort(RECORD_SIZE)           // 10: record size
        dos.writeShort(0)                     // 12: encryption (0=none)
        dos.writeShort(0)                     // 14: unknown

        // ── MOBI Header (232 bytes) ──
        dos.write("MOBI".toByteArray(Charsets.US_ASCII)) // 16
        dos.writeInt(MOBI_HEADER_LENGTH)      // 20: header length = 232
        dos.writeInt(2)                       // 24: mobi type
        dos.writeInt(65001)                   // 28: UTF-8
        dos.writeInt(title.hashCode())        // 32: unique ID
        dos.writeInt(6)                       // 36: file version

        dos.writeInt(-1)                      // 40: ortho index
        dos.writeInt(-1)                      // 44: inflection index
        dos.writeInt(-1)                      // 48: index names
        dos.writeInt(-1)                      // 52: index keys
        repeat(6) { dos.writeInt(-1) }        // 56-76: extra indexes

        dos.writeInt(firstNonBookIndex)       // 80
        dos.writeInt(fullNameOffset)          // 84
        dos.writeInt(fullNameBytes.size)      // 88
        dos.writeInt(0x09)                    // 92: locale
        dos.writeInt(0)                       // 96: input language
        dos.writeInt(0)                       //100: output language
        dos.writeInt(6)                       //104: min version
        dos.writeInt(firstImageIndex)         //108: first image

        dos.writeInt(0)                       //112: huffman offset
        dos.writeInt(0)                       //116: huffman count
        dos.writeInt(0)                       //120: huffman table offset
        dos.writeInt(0)                       //124: huffman table length

        dos.writeInt(0x50)                    //128: EXTH flags

        dos.write(ByteArray(32))              //132: 32 unknown bytes

        dos.writeInt(-1)                      //164: unknown
        dos.writeInt(-1)                      //168: DRM offset (no DRM)
        dos.writeInt(0)                       //172: DRM count = 0
        dos.writeInt(0)                       //176: DRM size
        dos.writeInt(0)                       //180: DRM flags

        dos.write(ByteArray(8))               //184: 8 unknown bytes

        dos.writeShort(1)                     //192: first content record
        dos.writeShort(lastContentRecord)     //194: last content record

        dos.writeInt(1)                       //196: unknown

        dos.writeInt(fcisRecordIndex)         //200: FCIS record number
        dos.writeInt(1)                       //204: FCIS record count
        dos.writeInt(flisRecordIndex)         //208: FLIS record number
        dos.writeInt(1)                       //212: FLIS record count

        dos.write(ByteArray(8))               //216: 8 unknown bytes

        dos.writeInt(-1)                      //224: unknown
        // End MOBI header (232 bytes)

        // ── EXTH Header ──
        dos.write(exthData)

        // ── Full book title ──
        dos.write(fullNameBytes)

        dos.flush()

        // Pad to 4-byte boundary
        val raw = baos.toByteArray()
        val remainder = raw.size % 4
        if (remainder == 0) return raw
        val padded = ByteArray(raw.size + (4 - remainder))
        System.arraycopy(raw, 0, padded, 0, raw.size)
        return padded
    }

    /**
     * Builds FCIS record (44 bytes). Contains text length reference.
     * Structure copied from reference Calibre MOBI file.
     */
    private fun buildFcisRecord(textLength: Int): ByteArray {
        val baos = ByteArrayOutputStream(44)
        val dos = DataOutputStream(baos)
        dos.write("FCIS".toByteArray(Charsets.US_ASCII)) // "FCIS"
        dos.writeInt(0x00000014)              // fixed
        dos.writeInt(0x00000010)              // fixed
        dos.writeInt(0x00000001)              // fixed
        dos.writeInt(0x00000000)              // fixed
        dos.writeInt(textLength)              // text length
        dos.writeInt(0x00000000)              // fixed
        dos.writeInt(0x00000020)              // fixed
        dos.writeInt(0x00000008)              // fixed
        dos.writeShort(0x0001)                // fixed
        dos.writeShort(0x0001)                // fixed
        dos.writeInt(0x00000000)              // fixed
        dos.flush()
        return baos.toByteArray()
    }

    /** EXTH header. Padding NOT included in header length. */
    private fun buildExthHeader(): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val dos = DataOutputStream(baos)

        val records = mutableListOf<ByteArray>()
        records.add(buildExthRecord(100, author.toByteArray(Charsets.UTF_8)))
        records.add(buildExthRecord(503, title.toByteArray(Charsets.UTF_8)))

        val recordsData = ByteArrayOutputStream()
        for (record in records) recordsData.write(record)
        val recordsBytes = recordsData.toByteArray()

        val headerLength = 12 + recordsBytes.size

        dos.write("EXTH".toByteArray(Charsets.US_ASCII))
        dos.writeInt(headerLength)
        dos.writeInt(records.size)
        dos.write(recordsBytes)

        val remainder = headerLength % 4
        if (remainder != 0) dos.write(ByteArray(4 - remainder))

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
}

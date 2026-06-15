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
        private const val MOBI_HEADER_LENGTH = 232
        /** Seconds between 1904-01-01 and 1970-01-01 */
        private const val PDB_EPOCH_OFFSET = 2082844800L
    }

    /**
     * Writes the complete MOBI file to [outputFile].
     */
    fun write(outputFile: File) {
        // --- Prepare text records ---
        val textRecords = splitIntoRecords(htmlContent, RECORD_SIZE)
        val textRecordCount = textRecords.size

        // --- Prepare image records ---
        val allImages = mutableListOf<ByteArray>()
        if (coverImage != null) {
            allImages.add(coverImage)
        }
        allImages.addAll(images)

        // Record layout:
        //   0        = header record (PalmDOC + MOBI + EXTH + title)
        //   1..N     = text records
        //   N+1..M   = image records (if any)
        val firstImageIndex = if (allImages.isNotEmpty()) textRecordCount + 1 else 0
        val firstNonBookIndex = textRecordCount + 1
        val totalRecords = 1 + textRecordCount + allImages.size

        // --- Build Record 0 (header record) ---
        val record0 = buildRecord0(
            textLength = htmlContent.size,
            textRecordCount = textRecordCount,
            firstImageIndex = firstImageIndex,
            firstNonBookIndex = firstNonBookIndex
        )

        // --- Build all record data for offset calculation ---
        val allRecordData = mutableListOf<ByteArray>()
        allRecordData.add(record0)
        allRecordData.addAll(textRecords)
        allRecordData.addAll(allImages)

        // --- Calculate offsets ---
        // PDB header = 78 bytes
        // Record offset table = totalRecords * 8 bytes + 2 bytes padding
        val recordOffsetTableSize = totalRecords * 8 + 2
        var currentOffset = PDB_HEADER_SIZE + recordOffsetTableSize

        val offsets = mutableListOf<Int>()
        for (recordData in allRecordData) {
            offsets.add(currentOffset)
            currentOffset += recordData.size
        }

        // --- Write the file ---
        Log.d(TAG, "Writing MOBI: $totalRecords records, ${textRecords.size} text, ${allImages.size} images")

        FileOutputStream(outputFile).use { fos ->
            DataOutputStream(fos).use { dos ->
                // 1. PDB Header
                writePdbHeader(dos, totalRecords)

                // 2. Record Offset Table
                // Each entry: 4 bytes offset + 1 byte attributes + 3 bytes uniqueID
                for (i in 0 until totalRecords) {
                    dos.writeInt(offsets[i])        // offset (4 bytes)
                    dos.writeByte(0)               // attributes (1 byte)
                    dos.writeByte(0)               // uniqueID high byte
                    dos.writeShort(i)              // uniqueID low 2 bytes
                }
                dos.writeShort(0) // 2 bytes padding after record list

                // 3. All records
                for (recordData in allRecordData) {
                    dos.write(recordData)
                }

                dos.flush()
            }
        }

        Log.d(TAG, "MOBI written: ${outputFile.length()} bytes → ${outputFile.absolutePath}")
    }

    /**
     * Writes the 78-byte PDB header.
     */
    private fun writePdbHeader(dos: DataOutputStream, numRecords: Int) {
        // name: 32 bytes, null-padded. Use only ASCII-safe chars.
        val safeName = title.replace(Regex("[^\\x20-\\x7E]"), "")
        val nameBytes = safeName.toByteArray(Charsets.US_ASCII)
        val nameField = ByteArray(32)
        System.arraycopy(nameBytes, 0, nameField, 0, minOf(nameBytes.size, 31))
        dos.write(nameField)

        // attributes: 2 bytes
        dos.writeShort(0)
        // version: 2 bytes
        dos.writeShort(0)

        // creation date: 4 bytes (seconds since 1904-01-01)
        val nowPdb = (System.currentTimeMillis() / 1000L) + PDB_EPOCH_OFFSET
        dos.writeInt(nowPdb.toInt())
        // modification date: 4 bytes
        dos.writeInt(nowPdb.toInt())
        // last backup: 4 bytes
        dos.writeInt(0)
        // modification number: 4 bytes
        dos.writeInt(0)
        // appInfoID: 4 bytes
        dos.writeInt(0)
        // sortInfoID: 4 bytes
        dos.writeInt(0)

        // type: "BOOK"
        dos.write("BOOK".toByteArray(Charsets.US_ASCII))
        // creator: "MOBI"
        dos.write("MOBI".toByteArray(Charsets.US_ASCII))

        // uniqueIDseed: 4 bytes
        dos.writeInt(2 * numRecords + 1)
        // nextRecordListID: 4 bytes
        dos.writeInt(0)
        // numRecords: 2 bytes
        dos.writeShort(numRecords)
    }

    /**
     * Builds Record 0: PalmDOC header + MOBI header + EXTH header + full title.
     * Padded to a 4-byte boundary.
     */
    private fun buildRecord0(
        textLength: Int,
        textRecordCount: Int,
        firstImageIndex: Int,
        firstNonBookIndex: Int
    ): ByteArray {
        val baos = ByteArrayOutputStream(1024)
        val dos = DataOutputStream(baos)

        // ── PalmDOC Header (16 bytes) ──
        dos.writeShort(1)                   // compression: 1 = no compression
        dos.writeShort(0)                   // unused
        dos.writeInt(textLength)            // textLength
        dos.writeShort(textRecordCount)     // recordCount
        dos.writeShort(RECORD_SIZE)         // recordSize = 4096
        dos.writeInt(0)                     // currentPosition

        // ── MOBI Header ──
        val exthData = buildExthHeader()
        val fullNameBytes = title.toByteArray(Charsets.UTF_8)
        val fullNameOffset = 16 + MOBI_HEADER_LENGTH + exthData.size

        // identifier: "MOBI"
        dos.write("MOBI".toByteArray(Charsets.US_ASCII))   // 4 bytes
        // headerLength: 232
        dos.writeInt(MOBI_HEADER_LENGTH)                    // 4 bytes
        // mobiType: 2 = Mobipocket Book
        dos.writeInt(2)                                     // 4 bytes
        // textEncoding: 65001 = UTF-8
        dos.writeInt(65001)                                 // 4 bytes
        // uniqueID
        dos.writeInt((System.nanoTime() and 0xFFFFFFFFL).toInt()) // 4 bytes
        // fileVersion: 6
        dos.writeInt(6)                                     // 4 bytes

        // orthoIndex, inflectionIndex, indexNames, indexKeys = 0xFFFFFFFF
        dos.writeInt(-1)  // orthoIndex                     // 4 bytes
        dos.writeInt(-1)  // inflectionIndex                 // 4 bytes
        dos.writeInt(-1)  // indexNames                      // 4 bytes
        dos.writeInt(-1)  // indexKeys                       // 4 bytes

        // extraIndex0-5 = 0xFFFFFFFF each
        for (i in 0 until 6) {                               // 24 bytes
            dos.writeInt(-1)
        }

        // firstNonBookIndex
        dos.writeInt(firstNonBookIndex)                      // 4 bytes
        // fullNameOffset (from start of record 0)
        dos.writeInt(fullNameOffset)                          // 4 bytes
        // fullNameLength
        dos.writeInt(fullNameBytes.size)                      // 4 bytes
        // locale: 0x09 = English
        dos.writeInt(0x09)                                    // 4 bytes
        // inputLanguage
        dos.writeInt(0)                                       // 4 bytes
        // outputLanguage
        dos.writeInt(0)                                       // 4 bytes
        // minVersion: 6
        dos.writeInt(6)                                       // 4 bytes
        // firstImageIndex
        dos.writeInt(if (firstImageIndex > 0) firstImageIndex else 0xFFFFFFFF.toInt()) // 4 bytes

        // huffman fields (4 ints, all 0)
        dos.writeInt(0)  // huffmanRecordOffset               // 4 bytes
        dos.writeInt(0)  // huffmanRecordCount                 // 4 bytes
        dos.writeInt(0)  // huffmanTableOffset                 // 4 bytes
        dos.writeInt(0)  // huffmanTableLength                 // 4 bytes

        // exthFlags: bit 6 set = has EXTH header (0x40)
        dos.writeInt(0x40)                                     // 4 bytes

        // 32 bytes unknown = 0
        dos.write(ByteArray(32))                               // 32 bytes

        // DRM fields
        dos.writeInt(-1)  // drmOffset = 0xFFFFFFFF            // 4 bytes
        dos.writeInt(0)   // drmCount = 0                      // 4 bytes
        dos.writeInt(0)   // drmSize = 0                       // 4 bytes
        dos.writeInt(0)   // drmFlags = 0                      // 4 bytes

        // 8 bytes padding
        dos.write(ByteArray(8))                                // 8 bytes

        // firstContentRecord: 2 bytes = 1
        dos.writeShort(1)                                      // 2 bytes
        // lastContentRecord: 2 bytes
        dos.writeShort(textRecordCount)                        // 2 bytes

        // unknown: 4 bytes = 1
        dos.writeInt(1)                                        // 4 bytes

        // fcisRecordNumber, fcisRecordCount = 0xFFFFFFFF
        dos.writeInt(-1)                                       // 4 bytes
        dos.writeInt(-1)                                       // 4 bytes
        // flisRecordNumber, flisRecordCount = 0xFFFFFFFF
        dos.writeInt(-1)                                       // 4 bytes
        dos.writeInt(-1)                                       // 4 bytes

        // 8 bytes unknown = 0
        dos.write(ByteArray(8))                                // 8 bytes

        // unknown = 0xFFFFFFFF
        dos.writeInt(-1)                                       // 4 bytes
        // unknown = 0
        dos.writeInt(0)                                        // 4 bytes
        // unknown = 0xFFFFFFFF
        dos.writeInt(-1)                                       // 4 bytes
        // unknown = 0xFFFFFFFF
        dos.writeInt(-1)                                       // 4 bytes

        // extraRecordDataFlags = 0
        dos.writeInt(0)                                        // 4 bytes
        // indxRecordOffset = 0xFFFFFFFF
        dos.writeInt(-1)                                       // 4 bytes
        // Total MOBI header: 232 bytes ✓

        // ── EXTH Header ──
        dos.write(exthData)

        // ── Full book title ──
        dos.write(fullNameBytes)

        dos.flush()

        // Pad to 4-byte boundary
        val raw = baos.toByteArray()
        return padTo4(raw)
    }

    /**
     * Builds the EXTH header with author (type 100) and updated title (type 503).
     * Padded to 4-byte boundary.
     */
    private fun buildExthHeader(): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val dos = DataOutputStream(baos)

        // Build the individual EXTH records
        val records = mutableListOf<ByteArray>()
        records.add(buildExthRecord(100, author.toByteArray(Charsets.UTF_8)))  // author
        records.add(buildExthRecord(503, title.toByteArray(Charsets.UTF_8)))   // updated title

        val recordsData = ByteArrayOutputStream()
        for (record in records) {
            recordsData.write(record)
        }
        val recordsBytes = recordsData.toByteArray()

        // EXTH header size = 12 (identifier + headerLength + recordCount) + records data
        val rawSize = 12 + recordsBytes.size
        // Pad to 4-byte boundary
        val paddedSize = ((rawSize + 3) / 4) * 4

        // identifier: "EXTH"
        dos.write("EXTH".toByteArray(Charsets.US_ASCII))
        // headerLength (including padding)
        dos.writeInt(paddedSize)
        // recordCount
        dos.writeInt(records.size)
        // records
        dos.write(recordsBytes)

        // padding
        val padding = paddedSize - rawSize
        if (padding > 0) {
            dos.write(ByteArray(padding))
        }

        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Builds a single EXTH record.
     * Format: type (4 bytes) + length (4 bytes, includes 8-byte header) + data
     */
    private fun buildExthRecord(type: Int, data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream(data.size + 8)
        val dos = DataOutputStream(baos)
        dos.writeInt(type)
        dos.writeInt(data.size + 8) // length includes type + length fields
        dos.write(data)
        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Splits [data] into chunks of [chunkSize] bytes.
     */
    private fun splitIntoRecords(data: ByteArray, chunkSize: Int): List<ByteArray> {
        val records = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            records.add(data.copyOfRange(offset, end))
            offset = end
        }
        // Ensure at least one text record exists
        if (records.isEmpty()) {
            records.add(ByteArray(0))
        }
        return records
    }

    /**
     * Pads a byte array to a 4-byte boundary.
     */
    private fun padTo4(data: ByteArray): ByteArray {
        val remainder = data.size % 4
        if (remainder == 0) return data
        val padded = ByteArray(data.size + (4 - remainder))
        System.arraycopy(data, 0, padded, 0, data.size)
        return padded
    }
}

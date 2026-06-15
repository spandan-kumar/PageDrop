package app.pagedrop.converter

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a valid MOBI file using the PDB/PalmDOC/MOBI structure.
 *
 * Header layout verified against a real Calibre-generated MOBI file that
 * opens correctly on Kindle Paperwhite 1st Gen.
 *
 * Reference: "The Geography of Bliss" (Calibre 3.21.0)
 * - Header length: 232
 * - EXTH flags: 0x50
 * - DRM count: 0
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
        /** MOBI header length: 232 bytes (matching Calibre output). */
        private const val MOBI_HEADER_LENGTH = 232
        private const val PDB_EPOCH_OFFSET = 2082844800L
    }

    fun write(outputFile: File) {
        val textRecords = splitIntoRecords(htmlContent, RECORD_SIZE)
        val textRecordCount = textRecords.size

        val allImages = mutableListOf<ByteArray>()
        if (coverImage != null) allImages.add(coverImage)
        allImages.addAll(images)

        val firstImageIndex = if (allImages.isNotEmpty()) textRecordCount + 1 else 0xFFFFFFFF.toInt()
        val firstNonBookIndex = textRecordCount + 1
        val lastContentRecord = if (allImages.isNotEmpty()) textRecordCount + allImages.size else textRecordCount
        val totalRecords = 1 + textRecordCount + allImages.size

        val record0 = buildRecord0(
            textLength = htmlContent.size,
            textRecordCount = textRecordCount,
            firstImageIndex = firstImageIndex,
            firstNonBookIndex = firstNonBookIndex,
            lastContentRecord = lastContentRecord,
        )

        val allRecordData = mutableListOf<ByteArray>()
        allRecordData.add(record0)
        allRecordData.addAll(textRecords)
        allRecordData.addAll(allImages)

        // Calculate record offsets
        val recordOffsetTableSize = totalRecords * 8 + 2
        var currentOffset = PDB_HEADER_SIZE + recordOffsetTableSize
        val offsets = mutableListOf<Int>()
        for (recordData in allRecordData) {
            offsets.add(currentOffset)
            currentOffset += recordData.size
        }

        Log.d(TAG, "Writing MOBI: records=$totalRecords text=${textRecords.size} images=${allImages.size} record0=${record0.size}B")

        FileOutputStream(outputFile).use { fos ->
            DataOutputStream(fos).use { dos ->
                writePdbHeader(dos, totalRecords)

                for (i in 0 until totalRecords) {
                    dos.writeInt(offsets[i])   // 4 bytes offset
                    dos.writeByte(0)           // 1 byte attributes
                    dos.writeByte(0)           // 1 byte uniqueID[0]
                    dos.writeShort(i)          // 2 bytes uniqueID[1-2]
                }
                dos.writeShort(0)              // 2-byte gap

                for (recordData in allRecordData) {
                    dos.write(recordData)
                }
                dos.flush()
            }
        }
        Log.d(TAG, "MOBI written: ${outputFile.length()} bytes → ${outputFile.absolutePath}")
    }

    /** PDB Header: exactly 78 bytes. */
    private fun writePdbHeader(dos: DataOutputStream, numRecords: Int) {
        val safeName = title.take(31).replace(Regex("[^\\x20-\\x7E]"), "_")
        val nameBytes = safeName.toByteArray(Charsets.US_ASCII)
        val nameField = ByteArray(32)
        System.arraycopy(nameBytes, 0, nameField, 0, minOf(nameBytes.size, 31))
        dos.write(nameField)

        dos.writeShort(0)        // attributes
        dos.writeShort(0)        // version
        val nowPdb = (System.currentTimeMillis() / 1000L) + PDB_EPOCH_OFFSET
        dos.writeInt(nowPdb.toInt())  // creation date
        dos.writeInt(nowPdb.toInt())  // modification date
        dos.writeInt(0)          // last backup
        dos.writeInt(0)          // modification number
        dos.writeInt(0)          // appInfoID
        dos.writeInt(0)          // sortInfoID
        dos.write("BOOK".toByteArray(Charsets.US_ASCII))
        dos.write("MOBI".toByteArray(Charsets.US_ASCII))
        dos.writeInt(2 * numRecords + 1)  // uniqueIDseed
        dos.writeInt(0)          // nextRecordListID
        dos.writeShort(numRecords)
    }

    /**
     * Builds Record 0: PalmDOC(16) + MOBI(232) + EXTH + title + padding.
     *
     * All offsets in comments are from start of record 0.
     * Values verified against reference Calibre file.
     */
    private fun buildRecord0(
        textLength: Int,
        textRecordCount: Int,
        firstImageIndex: Int,
        firstNonBookIndex: Int,
        lastContentRecord: Int,
    ): ByteArray {
        val baos = ByteArrayOutputStream(1024)
        val dos = DataOutputStream(baos)

        val exthData = buildExthHeader()
        val fullNameBytes = title.toByteArray(Charsets.UTF_8)
        val fullNameOffset = 16 + MOBI_HEADER_LENGTH + exthData.size

        // ── PalmDOC Header (16 bytes, offsets 0-15) ──
        dos.writeShort(1)                     //  0: compression (1=none)
        dos.writeShort(0)                     //  2: unused
        dos.writeInt(textLength)              //  4: text length
        dos.writeShort(textRecordCount)       //  8: record count
        dos.writeShort(RECORD_SIZE)           // 10: record size
        dos.writeShort(0)                     // 12: encryption type (0=none)
        dos.writeShort(0)                     // 14: unknown

        // ── MOBI Header (232 bytes, offsets 16-247) ──
        dos.write("MOBI".toByteArray(Charsets.US_ASCII)) // 16: identifier
        dos.writeInt(MOBI_HEADER_LENGTH)      // 20: header length = 232
        dos.writeInt(2)                       // 24: mobi type
        dos.writeInt(65001)                   // 28: encoding UTF-8
        dos.writeInt(title.hashCode())        // 32: unique ID
        dos.writeInt(6)                       // 36: file version

        dos.writeInt(-1)                      // 40: ortho index
        dos.writeInt(-1)                      // 44: inflection index
        dos.writeInt(-1)                      // 48: index names
        dos.writeInt(-1)                      // 52: index keys
        dos.writeInt(-1)                      // 56: extra index 0
        dos.writeInt(-1)                      // 60: extra index 1
        dos.writeInt(-1)                      // 64: extra index 2
        dos.writeInt(-1)                      // 68: extra index 3
        dos.writeInt(-1)                      // 72: extra index 4
        dos.writeInt(-1)                      // 76: extra index 5

        dos.writeInt(firstNonBookIndex)       // 80: first non-book index
        dos.writeInt(fullNameOffset)          // 84: full name offset
        dos.writeInt(fullNameBytes.size)      // 88: full name length
        dos.writeInt(0x09)                    // 92: locale (English)
        dos.writeInt(0)                       // 96: input language
        dos.writeInt(0)                       //100: output language
        dos.writeInt(6)                       //104: min version
        dos.writeInt(firstImageIndex)         //108: first image index

        dos.writeInt(0)                       //112: huffman record offset
        dos.writeInt(0)                       //116: huffman record count
        dos.writeInt(0)                       //120: huffman table offset
        dos.writeInt(0)                       //124: huffman table length

        dos.writeInt(0x50)                    //128: EXTH flags (0x50 = matches Calibre)

        dos.write(ByteArray(32))              //132: 32 unknown bytes

        dos.writeInt(-1)                      //164: unknown = 0xFFFFFFFF
        dos.writeInt(-1)                      //168: DRM offset = 0xFFFFFFFF (no DRM)
        dos.writeInt(0)                       //172: DRM count = 0 (matches Calibre)
        dos.writeInt(0)                       //176: DRM size = 0
        dos.writeInt(0)                       //180: DRM flags = 0

        dos.write(ByteArray(8))               //184: 8 unknown bytes

        dos.writeShort(1)                     //192: first content record
        dos.writeShort(lastContentRecord)     //194: last content record

        dos.writeInt(1)                       //196: unknown = 1

        dos.writeInt(-1)                      //200: FCIS record number (0xFFFFFFFF = none)
        dos.writeInt(1)                       //204: FCIS record count = 1
        dos.writeInt(-1)                      //208: FLIS record number (0xFFFFFFFF = none)
        dos.writeInt(1)                       //212: FLIS record count = 1

        dos.write(ByteArray(8))               //216: 8 unknown bytes

        dos.writeInt(-1)                      //224: unknown = 0xFFFFFFFF
        // End of MOBI header (232 bytes: offsets 16..247)

        // ── EXTH Header ──
        dos.write(exthData)

        // ── Full book title ──
        dos.write(fullNameBytes)

        dos.flush()

        // Pad record 0 to 4-byte boundary
        val raw = baos.toByteArray()
        val remainder = raw.size % 4
        if (remainder == 0) return raw
        val padded = ByteArray(raw.size + (4 - remainder))
        System.arraycopy(raw, 0, padded, 0, raw.size)
        return padded
    }

    /** EXTH header. Padding NOT included in header length (per reference). */
    private fun buildExthHeader(): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val dos = DataOutputStream(baos)

        val records = mutableListOf<ByteArray>()
        records.add(buildExthRecord(100, author.toByteArray(Charsets.UTF_8)))  // author
        records.add(buildExthRecord(503, title.toByteArray(Charsets.UTF_8)))   // updated title

        val recordsData = ByteArrayOutputStream()
        for (record in records) recordsData.write(record)
        val recordsBytes = recordsData.toByteArray()

        val headerLength = 12 + recordsBytes.size

        dos.write("EXTH".toByteArray(Charsets.US_ASCII))
        dos.writeInt(headerLength)  // NOT including padding
        dos.writeInt(records.size)
        dos.write(recordsBytes)

        // Pad to 4-byte boundary
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

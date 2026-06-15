package app.pagedrop.converter

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Writes a valid MOBI file using the PDB/PalmDOC/MOBI structure.
 *
 * Verified against a Calibre-generated MOBI that opens on Kindle PW1.
 * Key requirements discovered through testing:
 *   - PalmDOC compression (type 2) — Kindle PW1 may reject uncompressed
 *   - EXTH record 501 = "EBOK" — identifies content as ebook to Kindle
 *   - FLIS, FCIS, EOF magic records
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
        private const val COMPRESSION_PALMDOC = 2

        private val FLIS_RECORD = byteArrayOf(
            0x46, 0x4C, 0x49, 0x53,
            0x00, 0x00, 0x00, 0x08,
            0x00, 0x41, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00, 0x01, 0x00, 0x03,
            0x00, 0x00, 0x00, 0x03,
            0x00, 0x00, 0x00, 0x01,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )

        private val EOF_RECORD = byteArrayOf(
            0xE9.toByte(), 0x8E.toByte(), 0x0D, 0x0A
        )
    }

    fun write(outputFile: File) {
        // Compress text records using PalmDOC compression
        val textRecords = splitIntoRecords(htmlContent, RECORD_SIZE)
        val compressedRecords = textRecords.map { compressPalmDoc(it) }
        val textRecordCount = compressedRecords.size

        val allImages = mutableListOf<ByteArray>()
        if (coverImage != null) allImages.add(coverImage)
        allImages.addAll(images)

        val firstImageIndex = if (allImages.isNotEmpty()) textRecordCount + 1 else 0xFFFFFFFF.toInt()
        val lastContentRecord = if (allImages.isNotEmpty()) textRecordCount + allImages.size else textRecordCount

        val flisRecordIndex = 1 + textRecordCount + allImages.size
        val fcisRecordIndex = flisRecordIndex + 1
        val eofRecordIndex = fcisRecordIndex + 1
        val firstNonBookIndex = flisRecordIndex
        val totalRecords = eofRecordIndex + 1

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

        val allRecordData = mutableListOf<ByteArray>()
        allRecordData.add(record0)
        allRecordData.addAll(compressedRecords)
        allRecordData.addAll(allImages)
        allRecordData.add(FLIS_RECORD)
        allRecordData.add(fcisRecord)
        allRecordData.add(EOF_RECORD)

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
        dos.writeShort(COMPRESSION_PALMDOC)   //  0: compression = 2 (PalmDOC)
        dos.writeShort(0)                     //  2: unused
        dos.writeInt(textLength)              //  4: uncompressed text length
        dos.writeShort(textRecordCount)       //  8: record count
        dos.writeShort(RECORD_SIZE)           // 10: max record size (before compression)
        dos.writeShort(0)                     // 12: encryption (0=none)
        dos.writeShort(0)                     // 14: unknown

        // ── MOBI Header (232 bytes) ──
        dos.write("MOBI".toByteArray(Charsets.US_ASCII))
        dos.writeInt(MOBI_HEADER_LENGTH)
        dos.writeInt(2)                       // mobi type
        dos.writeInt(65001)                   // UTF-8
        dos.writeInt(title.hashCode())        // unique ID
        dos.writeInt(6)                       // file version

        dos.writeInt(-1)                      // ortho index
        dos.writeInt(-1)                      // inflection index
        dos.writeInt(-1)                      // index names
        dos.writeInt(-1)                      // index keys
        repeat(6) { dos.writeInt(-1) }        // extra indexes

        dos.writeInt(firstNonBookIndex)       // 80
        dos.writeInt(fullNameOffset)          // 84
        dos.writeInt(fullNameBytes.size)      // 88
        dos.writeInt(0x09)                    // 92: locale
        dos.writeInt(0)                       // 96
        dos.writeInt(0)                       //100
        dos.writeInt(6)                       //104: min version
        dos.writeInt(firstImageIndex)         //108

        dos.writeInt(0)                       //112: huffman
        dos.writeInt(0)                       //116
        dos.writeInt(0)                       //120
        dos.writeInt(0)                       //124

        dos.writeInt(0x50)                    //128: EXTH flags

        dos.write(ByteArray(32))              //132: 32 unknown bytes

        dos.writeInt(-1)                      //164
        dos.writeInt(-1)                      //168: DRM offset (no DRM)
        dos.writeInt(0)                       //172: DRM count
        dos.writeInt(0)                       //176: DRM size
        dos.writeInt(0)                       //180: DRM flags

        dos.write(ByteArray(8))               //184

        dos.writeShort(1)                     //192: first content record
        dos.writeShort(lastContentRecord)     //194: last content record

        dos.writeInt(1)                       //196

        dos.writeInt(fcisRecordIndex)         //200: FCIS
        dos.writeInt(1)                       //204
        dos.writeInt(flisRecordIndex)         //208: FLIS
        dos.writeInt(1)                       //212

        dos.write(ByteArray(8))               //216

        dos.writeInt(-1)                      //224
        // End MOBI header

        dos.write(exthData)
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

    private fun buildFcisRecord(textLength: Int): ByteArray {
        val baos = ByteArrayOutputStream(44)
        val dos = DataOutputStream(baos)
        dos.write("FCIS".toByteArray(Charsets.US_ASCII))
        dos.writeInt(0x00000014)
        dos.writeInt(0x00000010)
        dos.writeInt(0x00000001)
        dos.writeInt(0x00000000)
        dos.writeInt(textLength)
        dos.writeInt(0x00000000)
        dos.writeInt(0x00000020)
        dos.writeInt(0x00000008)
        dos.writeShort(0x0001)
        dos.writeShort(0x0001)
        dos.writeInt(0x00000000)
        dos.flush()
        return baos.toByteArray()
    }

    /**
     * EXTH header with critical records for Kindle compatibility:
     *   100 = author
     *   503 = updated title
     *   501 = "EBOK" (CDE type — tells Kindle this is an ebook)
     *   113 = UUID (unique identifier)
     */
    private fun buildExthHeader(): ByteArray {
        val baos = ByteArrayOutputStream(256)
        val dos = DataOutputStream(baos)

        val uuid = UUID.randomUUID().toString()
        val records = mutableListOf<ByteArray>()
        records.add(buildExthRecord(100, author.toByteArray(Charsets.UTF_8)))
        records.add(buildExthRecord(503, title.toByteArray(Charsets.UTF_8)))
        records.add(buildExthRecord(501, "EBOK".toByteArray(Charsets.UTF_8)))
        records.add(buildExthRecord(113, uuid.toByteArray(Charsets.UTF_8)))

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

    /**
     * PalmDOC LZ77 compression.
     * Algorithm from Calibre/MobileRead wiki:
     *   - Literal bytes 0x01-0x08, 0x80-0xFF pass through (with 0x00 escaped)
     *   - Bytes 0x09-0x7F are printable ASCII, stored as-is
     *   - Back-references: 2-byte token encoding (distance, length) for matches
     *   - Space+char optimization: 0x80|char for ' '+printable char
     */
    private fun compressPalmDoc(input: ByteArray): ByteArray {
        if (input.isEmpty()) return input

        val output = ByteArrayOutputStream(input.size)
        var i = 0

        while (i < input.size) {
            // Try to find a back-reference match (LZ77)
            if (i > 0) {
                var bestLen = 0
                var bestDist = 0
                val maxDist = minOf(i, 2047)
                val maxLen = minOf(input.size - i, 10)

                if (maxLen >= 3) {
                    var dist = 1
                    while (dist <= maxDist) {
                        var len = 0
                        while (len < maxLen && input[i + len] == input[i - dist + len]) {
                            len++
                        }
                        if (len >= 3 && len > bestLen) {
                            bestLen = len
                            bestDist = dist
                            if (bestLen == maxLen) break
                        }
                        dist++
                    }
                }

                if (bestLen >= 3) {
                    // Encode as 2-byte back-reference
                    // Byte 1: 0x80 | (distance >> 5) & 0x3F  (high bit set + 6 bits of distance)
                    // Byte 2: ((distance & 0x1F) << 3) | (length - 3)
                    val byte1 = 0x80 or ((bestDist shr 5) and 0x3F)
                    val byte2 = ((bestDist and 0x1F) shl 3) or (bestLen - 3)
                    output.write(byte1)
                    output.write(byte2)
                    i += bestLen
                    continue
                }
            }

            val b = input[i].toInt() and 0xFF

            // Space + printable char optimization
            if (b == 0x20 && i + 1 < input.size) {
                val next = input[i + 1].toInt() and 0xFF
                if (next in 0x40..0x7F) {
                    output.write(0x80 or next)
                    i += 2
                    continue
                }
            }

            // Literal byte
            if (b == 0x00 || (b in 0x01..0x08) || b >= 0x80) {
                // These bytes need to be escaped with a 0x01 prefix
                // Actually in PalmDOC: 0x00 is literal, bytes 1-8 are copy-count
                // We just output literal bytes with count=1
                output.write(0x01)  // copy next 1 byte literally
                output.write(b)
            } else {
                // 0x09-0x7F: printable ASCII, output directly
                output.write(b)
            }
            i++
        }

        return output.toByteArray()
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

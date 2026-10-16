package com.imgmiax.app.rom

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SparseImageParserTest {

    @Test
    fun testParseValidSparseHeader() {
        val bb = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0xED26FF3A.toInt()) // magic
        bb.putShort(1) // major
        bb.putShort(0) // minor
        bb.putShort(28) // file_hdr_sz
        bb.putShort(12) // chunk_hdr_sz
        bb.putInt(4096) // blk_sz
        bb.putInt(100) // total_blks
        bb.putInt(5) // total_chunks
        bb.putInt(0) // checksum

        val stream = ByteArrayInputStream(bb.array())
        val header = SparseImageParser.parseHeader(stream)

        assertNotNull(header)
        assertEquals(0xED26FF3AL, header?.magic)
        assertEquals(1, header?.majorVersion)
        assertEquals(0, header?.minorVersion)
        assertEquals(4096L, header?.blockSize)
        assertEquals(100L, header?.totalBlocks)
        assertEquals(5L, header?.totalChunks)
        assertEquals(409600L, header?.uncompressedSizeBytes)
    }

    @Test
    fun testParseInvalidSparseHeader() {
        val invalidBytes = ByteArray(28) { 0 }
        val stream = ByteArrayInputStream(invalidBytes)
        val header = SparseImageParser.parseHeader(stream)
        assertNull("Invalid header magic should return null", header)
    }
}

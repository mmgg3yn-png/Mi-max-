package com.imgmiax.app.rom

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BootImageParserTest {

    @Test
    fun testParseBootImageV0() {
        val bb = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("ANDROID!".toByteArray(Charsets.US_ASCII))
        bb.putInt(1048576) // kernel_size: 1MB
        bb.putInt(0x10008000) // kernel_addr
        bb.putInt(2097152) // ramdisk_size: 2MB
        bb.putInt(0x11000000) // ramdisk_addr
        bb.putInt(0) // second_size
        bb.putInt(0) // second_addr
        bb.putInt(0x10000100) // tags_addr
        bb.putInt(2048) // page_size
        bb.putInt(0) // header_version = 0

        val stream = ByteArrayInputStream(bb.array())
        val info = BootImageParser.parse(stream)

        assertNotNull(info)
        assertEquals(0, info?.headerVersion)
        assertEquals(1048576L, info?.kernelSize)
        assertEquals(2097152L, info?.ramdiskSize)
        assertEquals(2048L, info?.pageSize)
    }
}

package com.fasterxml.util.membuf;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import org.junit.Assert;

import com.fasterxml.util.membuf.MemBuffer;
import com.fasterxml.util.membuf.MemBuffers;

/**
 * Unit test that uses a sample file, sending all entries, one by
 * one (buffering all), then reading one-by-one and verifying
 * results.
 */
public class TestLonger extends MembufTestBase
{
    final Charset ENCODING = Charset.forName("ISO-8859-1");

    public void testShakespeareLineByLine() throws Exception
    {
        _testShakespeareLineByLine(Allocator.BYTE_BUFFER_DIRECT);
        _testShakespeareLineByLine(Allocator.BYTE_BUFFER_FAKE);
        _testShakespeareLineByLine(Allocator.BYTE_ARRAY);
    }
    
    /*
    /**********************************************************************
    /* Actual test impls
    /**********************************************************************
     */
    
    private void _testShakespeareLineByLine(Allocator aType) throws Exception
    {
        // First, read the data
        BufferedReader br = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/hamlet.xml"), ENCODING));
        List<byte[]> rows = readRows(br);
        br.close();

        /* Input file is about 280k; assume modest overhead (even if
         * entries are lines, relatively short). So, 330k should
         * be enough; use 11 buffers of 30k each (one more than
         * absolutely needed)
         */
        MemBuffers bufs = createBuffers(aType, 30 * 1024, 2, 11);
        MemBuffer buffer = bufs.createBuffer(8, 11);

        // then append/remove multiple times
        appendAndRemove(rows, buffer);
        appendAndRemove(rows, buffer);
        appendAndRemove(rows, buffer);
        appendAndRemove(rows, buffer);
        appendAndRemove(rows, buffer);

        // then try appends with partial read, clear
        appendAndClear(rows, buffer);

        // and repeat once for bot
        appendAndRemove(rows, buffer);
        appendAndClear(rows, buffer);
    }

    // And then a more mechanical test:
    public void test12SegmentBuffer() throws Exception
    {
        // 48kB, in 12 x 4kB segments
        MemBuffers bufs = new MemBuffers(4 * 1024, 2, 12);
        MemBuffer buffer = bufs.createBuffer(8, 12);

        /* should have space for at least 11 * 4 == 44kB at any point;
         * but use uneven length to force boundary conditions.
         */
        final byte[] chunk = buildChunk(257);
        final int initialCount = (44 * 1024) / 259;
        _write(buffer, chunk, initialCount); // 258 per entry due to 2-byte length prefix

        // and then read some, append some..... one third, say
        final int deltaCount = initialCount / 3;

        _read(buffer, chunk, deltaCount);
        _write(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
        _write(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
        _write(buffer, chunk, deltaCount);
        _read(buffer, chunk, deltaCount);
    }

    private void _write(MemBuffer buffer, byte[] chunk, int count) throws Exception
    {
        final int initialCount = buffer.getEntryCount();
        final long initialLength = buffer.getTotalPayloadLength();
        for (int i = 0; i < count; ++i) {
            if (!buffer.tryAppendEntry(chunk)) {
                fail("Failed to append; i = "+i+" / "+count);
            }
        }
        assertEquals(initialCount + count, buffer.getEntryCount());
        assertEquals(initialLength + (count * chunk.length), buffer.getTotalPayloadLength());
    }

    private void _read(MemBuffer buffer, byte[] chunk, int count) throws Exception
    {
        final int initialCount = buffer.getEntryCount();
        final long initialLength = buffer.getTotalPayloadLength();
        for (int i = 0; i < count; ++i) {
            byte[] next = buffer.getNextEntry(1L);
            Assert.assertArrayEquals(chunk, next);
        }
        assertEquals(initialCount - count, buffer.getEntryCount());
        assertEquals(initialLength - (count * chunk.length), buffer.getTotalPayloadLength());
    }
    
    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    protected List<byte[]> readRows(BufferedReader br) throws IOException
    {
        ArrayList<byte[]> lines = new ArrayList<byte[]>(1000);
        String line;
        while ((line = br.readLine()) != null) {
            lines.add(line.getBytes(ENCODING));
        }
        return lines;
    }

    protected void appendAndRemove(List<byte[]> rows, MemBuffer buffer)
        throws InterruptedException
    {
        long totalPayload = 0L;
        for (byte[] b : rows) {
            totalPayload += b.length;
            buffer.appendEntry(b);
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
        }

        // ok: should have added enough; verify book-keeping
        assertEquals(rows.size(), buffer.getEntryCount());
        assertEquals(totalPayload, buffer.getTotalPayloadLength());
        // we measured that it will take 10 segments for this data
        assertEquals(10, buffer.getSegmentCount());

        /*
        System.err.println("DEBUG: read "+lines.size()+"; space left = "+buffer.getMaximumAvailableSpace()
                +"; paylad = "+buffer.getTotalPayloadLength()
                +", segments = "+buffer.getSegmentCount()
                );
                */

        // then read, verify ordering, invariants:
        Iterator<byte[]> it = rows.iterator();
        int left = rows.size();
        while (it.hasNext()) {
            byte[] b = it.next();
            // pass timeout only to prevent infinite wait in case of a bug
            byte[] actual = buffer.getNextEntry(100L);
            Assert.assertArrayEquals(b, actual);
            totalPayload -= b.length;
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
            --left;
            assertEquals(left, buffer.getEntryCount());
        }
        // All done, should be empty...
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0L, buffer.getTotalPayloadLength());
        // always have at least one segment
        assertEquals(1, buffer.getSegmentCount());
    }

    protected void appendAndClear(List<byte[]> rows, MemBuffer buffer)
        throws InterruptedException
    {
        long totalPayload = 0L;
        for (byte[] b : rows) {
            totalPayload += b.length;
            buffer.appendEntry(b);
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
        }

        assertEquals(rows.size(), buffer.getEntryCount());
        assertEquals(totalPayload, buffer.getTotalPayloadLength());
        // we measured that it will take 10 segments for this data
        assertEquals(10, buffer.getSegmentCount());

        // then only read first 5 lines

        int left = rows.size();
        for (int i = 0; i < 5; ++i) {
            byte[] exp = rows.get(i);
            byte[] actual = buffer.getNextEntry(100L);
            Assert.assertArrayEquals(exp, actual);
            totalPayload -= exp.length;
            assertEquals(totalPayload, buffer.getTotalPayloadLength());
            --left;
            assertEquals(left, buffer.getEntryCount());
        }
        // then clear again, verify it's empty...
        buffer.clear();
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0L, buffer.getTotalPayloadLength());
        // always have at least one segment
        assertEquals(1, buffer.getSegmentCount());
    }
}

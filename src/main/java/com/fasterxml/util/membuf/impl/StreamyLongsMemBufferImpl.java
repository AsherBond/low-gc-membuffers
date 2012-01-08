package com.fasterxml.util.membuf.impl;

import com.fasterxml.util.membuf.*;
import com.fasterxml.util.membuf.base.LongsSegment;

/**
 * {@link StreamyBytesMemBuffer} implementation used for storing a sequence
 * of long values in a single long sequence which does not retain
 * boundaries implied by appends (as per {@link StreamyMemBuffer}).
 * This means that ordering of longs appended is retained on long-by-long
 * basis, but there are no discrete grouping of sub-sequences (entries);
 * all content is seen as sort of stream.
 *<p>
 * Access to queue is fully synchronized -- meaning that all methods are
 * synchronized by implementations as necessary, and caller should not need
 * to use external synchronization -- since parts will have to be anyway
 * (updating of stats, pointers), and since all real-world use cases will
 * need some level of synchronization anyway, even with just single producer
 * and consumer. If it turns out that there are bottlenecks that could be
 * avoided with more granular (or external) locking, this design can be
 * revisited.
 *<p>
 * Note that if instances are discarded, they <b>MUST</b> be closed:
 * finalize() method is not implemented since it is both somewhat unreliable
 * (i.e. should not be counted on) and can add overhead for GC processing.
 */
public class StreamyLongsMemBufferImpl extends StreamyLongsMemBuffer
{
    /**
     * @param allocator Allocator used for allocating underlying segments
     * @param minSegmentsToAllocate Maximum number of segments to hold on to
     *   (for reuse) after being released. Increases minimum memory usage
     *   but can improve performance by avoiding unnecessary re-allocation;
     *   and also guarantees that buffer always has at least this much
     *   storage capacity.
     * @param maxSegmentsToAllocate Maximum number of segments that can be
     *   allocated for this buffer: limits maximum capacity and memory usage
     * @param initialSegments Chain of pre-allocated segments, containing
     *   <code>_maxSegmentsForReuse</code> segments that are allocated to ensure
     *   that there is always specified minimum capacity available
     */
    public StreamyLongsMemBufferImpl(SegmentAllocator<LongsSegment> allocator,
                int minSegmentsToAllocate, int maxSegmentsToAllocate,
                LongsSegment initialSegments) {
        super(allocator, minSegmentsToAllocate, maxSegmentsToAllocate, initialSegments);
    }

    /*
    /**********************************************************************
    /* Public API, simple statistics (not data) accessors
    /**********************************************************************
     */

    @Override
    public synchronized boolean isEmpty() {
        return _totalPayloadLength > 0L;
    }

    @Override
    public synchronized long available() {
        return _totalPayloadLength;
    }
    
    /*
    /**********************************************************************
    /* Public API, appending
    /**********************************************************************
     */

    //public boolean tryAppend(long[] data);
    //public void append(long[] data, int dataOffset, int dataLength);
    //public synchronized void append(long value);

    @Override
    public synchronized boolean tryAppend(long value)
    {
        if (_head == null) {
            _reportClosed();
        }
        if (!_head.tryAppend(value)) {
        }
    }
    
    @Override
    public synchronized boolean tryAppend(long[] data, int dataOffset, int dataLength)
    {
        if (_head == null) {
            _reportClosed();
        }
        int freeInCurrent = _head.availableForAppend();
        // First, simple case: can fit it in the current buffer?
        if (freeInCurrent >= dataLength) {
            _head.append(data, dataOffset, dataLength);
        } else {
            // if not, must check whether we could allocate enough segments to fit in
            int neededSegments = ((dataLength - freeInCurrent) + (_segmentSize-1)) / _segmentSize;
    
            // Which may need reusing local segments, or allocating new ones via allocates
            int segmentsToAlloc = neededSegments - _freeSegmentCount;
            if (segmentsToAlloc > 0) { // nope: need more
                // ok, but are allowed to grow that big?
                if ((_usedSegmentsCount + _freeSegmentCount + segmentsToAlloc) > _maxSegmentsToAllocate) {
                    return false;
                }
                // if we are, let's try allocate: will be added to "free" segments first, then used
                LongsSegment newFree = _segmentAllocator.allocateSegments(segmentsToAlloc, _firstFreeSegment);
                if (newFree == null) {
                    return false;
                }
                _freeSegmentCount += segmentsToAlloc;
                _firstFreeSegment = newFree;
            }
    
            // and if we got this far, it's just simple matter of writing pieces into segments
            _doAppendChunked(data, dataOffset, dataLength);
        }
        boolean wasEmpty = (_totalPayloadLength == 0);
        _totalPayloadLength += dataLength;        
        if (wasEmpty) {
            this.notifyAll();
        }
        return true;
    }

    protected void _doAppendChunked(long[] buffer, int offset, int length)
    {
        if (length < 1) {
            return;
        }
        LongsSegment seg = _head;
        while (true) {
            int actual = seg.tryAppend(buffer, offset, length);
            offset += actual;
            length -= actual;
            if (length == 0) { // complete, can leave
                return;
            }
            // otherwise, need another segment, so complete current write
            seg.finishWriting();
            // and allocate, init-for-writing new one:
            LongsSegment newSeg = _reuseFree().initForWriting();
            seg.relink(newSeg);
            _head = seg = newSeg;
        }
    }
    /*
    /**********************************************************************
    /* Public API, reading
    /**********************************************************************
     */
    
    @Override
    public long read() throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int read(long[] buffer, int offset, int length)
            throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int readIfAvailable(long[] buffer, int offset, int length) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int read(long timeoutMsecs, long[] buffer, int offset, int length)
            throws InterruptedException {
        // TODO Auto-generated method stub
        return 0;
    }
    
    /*
    /**********************************************************************
    /* Abstract method impls
    /**********************************************************************
     */

    // // // No peeked data, so these are simple
    
    @Override
    protected void _clearPeeked() { }

    @Override
    protected int _peekedLength() {
        return 0;
    }
}
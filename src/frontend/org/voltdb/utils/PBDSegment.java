/* This file is part of VoltDB.
 * Copyright (C) 2008-2016 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.utils;

import org.voltcore.utils.DBBPool;
import org.voltcore.utils.DeferredSerialization;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

public abstract class PBDSegment {
    private static final String TRUNCATOR_CURSOR = "__truncator__";
    static final int NO_FLAGS = 0;
    static final int FLAG_COMPRESSED = 1;

    static final int COUNT_OFFSET = 0;
    static final int SIZE_OFFSET = 4;

    // Has to be able to hold at least one object (compressed or not)
    public static final int CHUNK_SIZE = (1024 * 1024) * 64;
    static final int OBJECT_HEADER_BYTES = 8;
    static final int SEGMENT_HEADER_BYTES = 8;
    protected final File m_file;

    protected boolean m_closed = true;
    protected RandomAccessFile m_ras;
    protected FileChannel m_fc;
    //Avoid unecessary sync with this flag
    protected boolean m_syncedSinceLastEdit = true;

    public PBDSegment(File file)
    {
        m_file = file;
    }

    abstract long segmentId();
    abstract File file();

    abstract void reset();

    abstract int getNumEntries() throws IOException;

    abstract boolean isBeingPolled();

    abstract int readIndex(String cursorId);

    abstract boolean isOpenForReading(String cursorId);

    abstract void openForRead(String cursorId) throws IOException;
    /**
     * @param forWrite    Open the file in read/write mode
     * @param emptyFile   true to overwrite the header with 0 entries, essentially emptying the file
     * @throws IOException
     */
    abstract protected void openForWrite(boolean emptyFile) throws IOException;

    abstract void initNumEntries(int count, int size) throws IOException;

    abstract void closeAndDelete() throws IOException;

    abstract boolean isClosed();

    abstract void close() throws IOException;

    abstract void sync() throws IOException;

    abstract boolean hasMoreEntries(String cursorId) throws IOException;
    abstract boolean hasAllFinishedReading() throws IOException;

    abstract boolean isCursorEmpty(String cursorId) throws IOException;
    abstract boolean isSegmentEmpty() throws IOException;

    abstract boolean offer(DBBPool.BBContainer cont, boolean compress) throws IOException;

    abstract int offer(DeferredSerialization ds) throws IOException;

    abstract DBBPool.BBContainer poll(String cursorId, BinaryDeque.OutputContainerFactory factory) throws IOException;

    /*
     * Don't use size in bytes to determine empty, could potentially
     * diverge from object count on crash or power failure
     * although incredibly unlikely
     */
    abstract int uncompressedBytesToRead(String cursorId);

    // TODO: javadoc
    abstract int size();

    abstract protected long readOffset(String cursorId);
    abstract protected void rewindReadOffset(String cursorId, int byBytes);
    abstract protected int writeTruncatedEntry(BinaryDeque.TruncatorResponse entry, int length) throws IOException;

    /**
     * Parse the segment and truncate the file if necessary.
     * @param truncator    A caller-supplied truncator that decides where in the segment to truncate
     * @return The number of objects that was truncated. This number will be subtracted from the total number
     * of available objects in the PBD. -1 means that this whole segment should be removed.
     * @throws IOException
     */
    int parseAndTruncate(BinaryDeque.BinaryDequeTruncator truncator) throws IOException {
        if (!m_closed) throw new IOException(("Segment should not be open before truncation"));

        openForWrite(false);
        openForRead(TRUNCATOR_CURSOR);

        // Do stuff
        final int initialEntryCount = getNumEntries();
        int entriesTruncated = 0;
        int sizeInBytes = 0;

        DBBPool.BBContainer cont;
        while (true) {
            final long beforePos = readOffset(TRUNCATOR_CURSOR);

            cont = poll(TRUNCATOR_CURSOR, PersistentBinaryDeque.UNSAFE_CONTAINER_FACTORY);
            if (cont == null) {
                break;
            }

            final int compressedLength = (int) (readOffset(TRUNCATOR_CURSOR) - beforePos - OBJECT_HEADER_BYTES);
            final int uncompressedLength = cont.b().limit();

            try {
                //Handoff the object to the truncator and await a decision
                BinaryDeque.TruncatorResponse retval = truncator.parse(cont);
                if (retval == null) {
                    //Nothing to do, leave the object alone and move to the next
                    sizeInBytes += uncompressedLength;
                } else {
                    //If the returned bytebuffer is empty, remove the object and truncate the file
                    if (retval.status == BinaryDeque.TruncatorResponse.Status.FULL_TRUNCATE) {
                        if (readIndex(TRUNCATOR_CURSOR) == 1) {
                            /*
                             * If truncation is occuring at the first object
                             * Whammo! Delete the file.
                             */
                            entriesTruncated = -1;
                        } else {
                            entriesTruncated = initialEntryCount - (readIndex(TRUNCATOR_CURSOR) - 1);
                            //Don't forget to update the number of entries in the file
                            initNumEntries(readIndex(TRUNCATOR_CURSOR) - 1, sizeInBytes);
                            m_fc.truncate(readOffset(TRUNCATOR_CURSOR) - (compressedLength + OBJECT_HEADER_BYTES));
                        }
                    } else {
                        assert retval.status == BinaryDeque.TruncatorResponse.Status.PARTIAL_TRUNCATE;
                        entriesTruncated = initialEntryCount - readIndex(TRUNCATOR_CURSOR);
                        //Partial object truncation
                        rewindReadOffset(TRUNCATOR_CURSOR, compressedLength + OBJECT_HEADER_BYTES);
                        final long partialEntryBeginOffset = readOffset(TRUNCATOR_CURSOR);
                        m_fc.position(partialEntryBeginOffset);

                        final int written = writeTruncatedEntry(retval, compressedLength);
                        sizeInBytes += written;

                        initNumEntries(readIndex(TRUNCATOR_CURSOR), sizeInBytes);
                        m_fc.truncate(partialEntryBeginOffset + written + OBJECT_HEADER_BYTES);
                    }

                    break;
                }
            } finally {
                cont.discard();
            }
        }

        close();

        return entriesTruncated;
    }
}

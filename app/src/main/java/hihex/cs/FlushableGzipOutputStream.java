/*
 * CatSaver
 * Copyright (C) 2015 HiHex Ltd.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package hihex.cs;

import com.google.common.base.Preconditions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A replacement of {@code GZIPOutputStream(FileOutputStream)}, which can be flushed anytime to make sure everything
 * written so far is visible to readers from other processes.
 */
public class FlushableGzipOutputStream extends OutputStream {
    static {
        System.loadLibrary("catsaver");
    }

    private static native long nativeCreate(final String filename);

    private static native void nativeClose(final long ptr);

    private static native void nativeWrite(final long ptr, final byte[] buf, final int offset, final int length);

    private static native void nativePutc(final long ptr, final int c);

    private static native void nativeFlush(final long ptr);

    private long mNative;

    public FlushableGzipOutputStream(final File file) throws FileNotFoundException {
        mNative = nativeCreate(file.getPath());
        if (mNative == 0) {
            throw new FileNotFoundException(file + " cannot be written");
        }
    }

    @Override
    public void close() throws IOException {
        nativeClose(mNative);
        mNative = 0;
    }

    @Override
    public void flush() throws IOException {
        nativeFlush(mNative);
    }

    @Override
    public void write(final byte[] buffer, final int offset, final int count) throws IOException {
        Preconditions.checkPositionIndexes(offset, offset + count, buffer.length);
        nativeWrite(mNative, buffer, offset, count);
    }

    @Override
    public void write(final int oneByte) throws IOException {
        nativePutc(mNative, oneByte);
    }
}

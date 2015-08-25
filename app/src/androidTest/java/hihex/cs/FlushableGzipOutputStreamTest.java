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

import android.test.MoreAsserts;

import com.google.common.io.ByteStreams;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class FlushableGzipOutputStreamTest extends TestCase {
    public void testOutputCanBeReadUsingStandardGzipInputStream() throws IOException {
        final File tempFile = File.createTempFile("tmp", ".gz");

        final ByteArrayOutputStream standardStream = new ByteArrayOutputStream();
        final FlushableGzipOutputStream zippedStream = new FlushableGzipOutputStream(tempFile);

        for (byte i = 0; i < 100; ++ i) {
            final byte[] buffer = {i, i, i, i, i, i, i, i, i, i, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, i, i, i, i, i, i, i, i};
            for (int j = 0; j < buffer.length; ++ j) {
                standardStream.write(buffer, j, buffer.length - j);
                zippedStream.write(buffer, j, buffer.length - j);
            }
            zippedStream.flush();
        }

        // DO NOT CLOSE THE STREAM. We are here to check whether flush() can write everything without the final close.

        final byte[] expected = standardStream.toByteArray();

        MoreAsserts.assertNotEqual(0, tempFile.length());
        assertTrue(tempFile.length() < expected.length);

        final GZIPInputStream inputStream = new GZIPInputStream(new FileInputStream(tempFile));
        final ByteArrayOutputStream actualStream = new ByteArrayOutputStream();
        try {
            ByteStreams.copy(inputStream, actualStream);
        } catch (final EOFException e) {
            // Ignore, we do expect an EOFException.
        }

        inputStream.close();

        MoreAsserts.assertEquals(expected, actualStream.toByteArray());

        tempFile.delete();
    }
}

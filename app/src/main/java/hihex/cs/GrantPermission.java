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
 */

package hihex.cs;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class to try to get root/shell permission to grant this package the READ_LOGS permission.
 */
public final class GrantPermission {
    private static final String GRANT_COMMAND = "pm grant hihex.cs android.permission.READ_LOGS";
    private static final String ADB_CONNECT_MESSAGE = "CNXN\0\0\0\1\0\u0010\0\0\7\0\0\u00002\2\0\0\u00bc\u00b1\u00a7\u00b1host::\0";
    private static final String ADB_GRANT_MESSAGE = "OPEN\1\0\0\0\0\0\0\u00005\0\0\0\u00fb\u0012\0\0\u00b0\u00af\u00ba\u00b1shell:" + GRANT_COMMAND + '\0';

    /**
     * Try to grant this package the READ_LOGS permission using various (legitimate) methods. There is no guarantee this
     * will succeed &mdash; you still have to use {@link android.content.Context#checkCallingOrSelfPermission(String)
     * checkCallingOrSelfPermission()} to ensure you have got the correct permissions.
     *
     * <p>This method will run external programs and access sockets, so it must not be called in the main thread.</p>
     */
    public static void tryGrantPermission() {
        tryGrantPermissionViaSu();
        tryGrantPermissionViaAdb();
    }

    private static void tryGrantPermissionViaSu() {
        final Runtime runtime = Runtime.getRuntime();
        final String[] args = {"su", "-c", GRANT_COMMAND};
        try {
            final Process process = runtime.exec(args);
            process.waitFor();
        } catch (final IOException e) {
            CsLog.e("Failed to grant permission using `su`: " + e);
        } catch (final InterruptedException e) {
            CsLog.e("Interrupted while granting permission with `su`");
        }
    }

    private static void tryGrantPermissionViaAdb() {
        final Socket socket = new Socket();
        try {
            final SocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), 5555);
            socket.setSoTimeout(1000);
            socket.connect(address);

            final byte[] readHeader = new byte[24];
            final OutputStream outputStream = socket.getOutputStream();
            final InputStream inputStream = socket.getInputStream();

            outputStream.write(ADB_CONNECT_MESSAGE.getBytes(Charsets.ISO_8859_1));
            readAdbPacket(inputStream, readHeader);
            outputStream.write(ADB_GRANT_MESSAGE.getBytes(Charsets.ISO_8859_1));
            readAdbPacket(inputStream, readHeader);
            readAdbPacket(inputStream, readHeader);

        } catch (final IOException e) {
            CsLog.e("Failed to grant permission using `adb`: " + e);
        } finally {
            try {
                socket.close();
            } catch (final IOException e) {
                // ignore
            }
        }
    }

    private static void readAdbPacket(final InputStream stream, final byte[] header) throws IOException {
        ByteStreams.readFully(stream, header);
        long length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).get(12);
        ByteStreams.skipFully(stream, length);
    }
}

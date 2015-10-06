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

import com.google.common.eventbus.Subscribe;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;

public class LiveEventSource extends NanoHTTPD.Response {
    final LinkedBlockingQueue<LogEntry> mEntries = new LinkedBlockingQueue<>();

    public LiveEventSource() {
        super(Status.OK, "text/event-stream", "");
    }

    @Override
    protected void send(final OutputStream outputStream) {
        // Do not call super(). The operation of SSE here is totally different from the normal fixed-length source and
        // chunked-encoding response.

        try {
            Events.bus.register(this);

            final SimpleDateFormat dateFormat = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.ROOT);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

            final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nDate: ");
            writer.write(dateFormat.format(new Date()));
            writer.write("\r\nConnection: keep-alive\r\n\r\n");
            writer.flush();

            while (true) {
                final LogEntry entry = mEntries.poll(10, TimeUnit.SECONDS);
                // We use poll() with timeout instead of take(), so that there will be a finite time before we call
                // writer.flush(). The flush() call ensures that if the peer closed the connection, we will eventually
                // notice we can no longer send anything and throw an IOException. This in turn interrupts the infinite
                // loop and terminate the callback thread.

                if (entry != null) {
                    writer.write("event: message\ndata: ");
                    entry.writeJSON(writer);
                    writer.write("\n\n");
                } else {
                    writer.write("event: ping\n\n");
                }
                writer.flush();
            }
        } catch (final IOException | InterruptedException e) {
            //throw new RuntimeException(e);
        } finally {
            Events.bus.unregister(this);
        }
    }

    @Subscribe
    public void log(final Events.LiveEntry entry) {
        try {
            mEntries.put(new LogEntry(entry.entry));
        } catch (final InterruptedException e) {
            // Ignored, the queue is unbounded.
        }
    }
}

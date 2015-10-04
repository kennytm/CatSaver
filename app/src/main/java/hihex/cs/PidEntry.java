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
 */

package hihex.cs;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.Closeables;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Locale;

public final class PidEntry {
    private static final String UNSAFE_FILENAME_PATTERN = "[^-_.,;a-zA-Z0-9]";

    public final int pid;
    public final String processName;
    public final Optional<File> path;
    public final Optional<Writer> writer;

    private PidEntry(final int pid, final String processName, final File path, final Writer writer) {
        this.pid = pid;
        this.processName = processName;
        this.path = Optional.of(path);
        this.writer = Optional.of(writer);
    }

    public PidEntry(final int pid, final String processName) {
        this.pid = pid;
        this.processName = processName;
        path = Optional.absent();
        writer = Optional.absent();
    }

    /**
     * Close the file it is currently writing to.
     */
    public PidEntry close() {
        if (writer.isPresent()) {
            final Writer writer2 = writer.get();
            try {
                writer2.flush();
            } catch (final IOException e) {
                CsLog.e("Failed to flush file, some data may not be written. " + e);
            } finally {
                try {
                    Closeables.close(writer2, true);
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }
        return new PidEntry(pid, processName);
    }

    /**
     * Open a new file, using the timestamp to name the file if possible.
     */
    public PidEntry open(final File parent, final Date timestamp) throws IOException {
        if (writer.isPresent()) {
            return this;
        }

        final String safeName = processName.replaceAll(UNSAFE_FILENAME_PATTERN, "-");
        final String filePrefix = String.format(Locale.ROOT, "%2$s-%1$tF-%1$tH.%1$tM.%1$tS", timestamp, safeName);

        // We try "xxx.html.gz", "xxx (1).html.gz", "xxx (2).html.gz", ... until a filename is free to use.
        File path = null;
        for (int i = 0; path == null || path.isFile(); i++) {
            final String fileSuffix = (i != 0) ? (" (" + i + ").html.gz") : ".html.gz";
            path = new File(parent, filePrefix + fileSuffix);
        }

        final Writer writer = new OutputStreamWriter(new FlushableGzipOutputStream(path), Charsets.UTF_8);

        return new PidEntry(pid, processName, path, writer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PidEntry pidEntry = (PidEntry) o;

        return pid == pidEntry.pid && processName.equals(pidEntry.processName);
    }

    @Override
    public int hashCode() {
        int result = pid;
        result = 31 * result + processName.hashCode();
        return result;
    }
}

/*
 * CatSaver Copyright (C) 2015 HiHex Ltd.
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package hihex.cs;

import android.content.Context;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Shared configuration between the WebServer and LogCollector.
 */
public final class Config {
    public static final int[] EMPTY_PID_ARRAY = {};

    public final Context context;

    public final Preferences preferences;
    public final LogFiles logFiles;
    public final ChunkRenderer renderer;

    public final PidDatabase pidDatabase = new PidDatabase();

    private final AtomicInteger mSystemServerPid = new AtomicInteger(-1);
    private final AtomicInteger mDebuggerdPid = new AtomicInteger(-1);

    public Config(final Context context) {
        this.context = context;
        preferences = new Preferences(context);
        logFiles = new LogFiles(context);
        renderer = new ChunkRenderer(context);
    }

    public void refreshPids() {
        mSystemServerPid.set(-1);
        mDebuggerdPid.set(-1);
        pidDatabase.refresh();
    }

    private int findPidForExactProcessName(final AtomicInteger cache, final String processName) {
        final int pid = cache.get();
        if (pid != -1) {
            return pid;
        }

        final int newPid = pidDatabase.findPidForExactProcessName(processName);
        if (!cache.compareAndSet(-1, newPid)) {
            return cache.get();
        } else {
            return newPid;
        }
    }

    public int systemServerPid() {
        return findPidForExactProcessName(mSystemServerPid, "system_server");
    }

    public int debuggerdPid() {
        return findPidForExactProcessName(mDebuggerdPid, "/system/bin/debuggerd");
    }

    public Optional<Writer> startRecording(final int pid, final Optional<String> processName, final Date timestamp)
            throws IOException {
        final Writer[] optWriter = {null};
        try {
            pidDatabase.startRecording(pid, processName, logFiles, timestamp, new Function<PidEntry, Void>() {
                @Override
                public Void apply(final PidEntry entry) {
                    final Writer writer = entry.writer.get();
                    optWriter[0] = writer;
                    try {
                        renderer.writeHeader(writer, entry.pid, processName.or(entry.processName), timestamp);
                    } catch (final IOException e) {
                        throw new UncheckedExecutionException(e);
                    }
                    return null;
                }
            });
        } catch (final UncheckedExecutionException e) {
            final Throwable cause = e.getCause();
            Throwables.propagateIfInstanceOf(cause, IOException.class);
            throw Throwables.propagate(cause);
        }
        Events.bus.post(new Events.RecordCount(pidDatabase.countRecordingEntries()));
        return Optional.fromNullable(optWriter[0]);
    }

    public void stopRecording(final int pid, final Function<Writer, ?> cleanup) {
        pidDatabase.stopRecording(pid, new Function<Writer, Void>() {
            @Override
            public Void apply(final Writer writer) {
                try {
                    cleanup.apply(writer);
                    renderer.writeFooter(writer);
                } catch (final IOException e) {
                    // Ignore.
                }
                return null;
            }
        });
        Events.bus.post(new Events.RecordCount(pidDatabase.countRecordingEntries()));
    }

    public Optional<Writer> splitLogAndGetWriter(final int pid) {
        final Optional<PidEntry> optEntry = pidDatabase.getEntry(pid);
        if (optEntry.isPresent()) {
            final PidEntry entry = optEntry.get();
            final long splitSize = preferences.getSplitSize();
            if (splitSize >= 0 && entry.path.isPresent() && entry.path.get().length() >= splitSize) {
                try {
                    renderer.writeFooter(entry.writer.get());
                } catch (final IOException e) {
                    // Ignore.
                }
                final PidEntry splitEntry = pidDatabase.splitEntry(pid, logFiles);
                try {
                    renderer.writeHeader(splitEntry.writer.get(), splitEntry.pid, splitEntry.processName, new Date());
                } catch (final IOException e) {
                    // Ignore.
                }
                return splitEntry.writer;
            } else {
                return entry.writer;
            }
        } else {
            return Optional.absent();
        }
    }

    public void flushWriter(final String filename) {
        final Optional<PidEntry> entry = pidDatabase.findEntry(filename);
        if (!entry.isPresent()) {
            return;
        }
        final Optional<Writer> writer = entry.get().writer;
        if (!entry.isPresent()) {
            return;
        }
        try {
            writer.get().flush();
        } catch (final IOException e) {
            // Flush failure, but ignore.
        }
    }

    public final void startRecordingExistingProcesses() {
        final Date now = new Date();
        final Pattern filter = preferences.getFilter();
        for (final HashMap<String, String> entry : pidDatabase.runningProcesses()) {
            final String name = entry.get("name");
            if (filter.matcher(name).find() && !entry.containsKey("recording")) {
                final int pid = Integer.parseInt(entry.get("pid"));
                try {
                    startRecording(pid, Optional.of(name), now);
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public int[] getFilteredPidsForLog(final LogEntry entry, final int sourcePid) {
        final Optional<PidEntry> sourcePidEntry = pidDatabase.getEntry(sourcePid);
        if (!sourcePidEntry.isPresent()) {
            return EMPTY_PID_ARRAY;
        }

        final String source = sourcePidEntry.get().processName;
        final Set<String> targets = pidDatabase.listRecordingProcessNames();
        final HashSet<String> filtered = preferences.getLogFilter().filter(entry, source, targets);
        if (filtered.isEmpty()) {
            return EMPTY_PID_ARRAY;
        }

        final int[] pids = new int[filtered.size()];
        int i = 0;
        for (final String target : filtered) {
            final int targetPid;
            if (target.equals(source)) {
                targetPid = sourcePid;
            } else {
                targetPid = pidDatabase.findPidForExactProcessName(target);
            }
            pids[i] = targetPid;
            ++i;
        }
        return pids;
    }
}

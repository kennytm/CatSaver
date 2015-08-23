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

import android.util.Pair;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.io.Closeables;
import com.x5.template.Chunk;
import com.x5.template.Theme;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The thread that collects logcat events into files.
 */
public final class LogRecorder implements Runnable {
    private final Config mConfig;
    private final Chunk mLogEntryChunk;
    private final Chunk mLogAnrEntryPrefixChunk;
    private final Chunk mLogAnrEntrySuffixChunk;
    private int[] mDebuggedPids = Config.EMPTY_PID_ARRAY;

    public LogRecorder(final Config config) {
        mConfig = config;

        final Theme theme = config.theme;
        mLogEntryChunk = theme.makeChunk("log#entry");
        mLogAnrEntryPrefixChunk = theme.makeChunk("log#anr_entry_prefix");
        mLogAnrEntrySuffixChunk = theme.makeChunk("log#anr_entry_suffix");

        mConfig.eventBus.register(this);
    }

    @Override
    public void run() {
        final Runtime runtime = Runtime.getRuntime();
        try {
            // Clean up all previous logcat processes if we are restarted. See http://stackoverflow.com/q/16173387/.
            try {
                final Process killLogcatProcess = runtime.exec(new String[]{"killall", "-2", "logcat"});
                killLogcatProcess.waitFor();
            } catch (final IOException e) {
                // Ignore if we can't kill existing logcat. It's just minor annoyance.
            }

            // Clear old log, to avoid overwriting existing files.
            final Process clearProcess = runtime.exec(new String[]{"logcat", "-c"});
            clearProcess.waitFor();

            final Process process = runtime.exec(new String[]{"logcat", "-B"});
            final LogEntry entry = new LogEntry();
            final InputStream stream = process.getInputStream();

            while (true) {
                entry.read(stream);
                if (entry.isSystemRestart()) {
                    mConfig.refreshPids();
                }

                final int pid = entry.pid();
                if (pid == mConfig.systemServerPid()) {
                    handleSystemServerLog(entry);
                } else if (pid == mConfig.debuggerdPid()) {
                    handleDebuggerdLog(entry);
                }

                final int[] writeToPids = mConfig.getFilteredPidsForLog(entry, pid);
                if (entry.isJniCrash()) {
                    mDebuggedPids = writeToPids;
                }
                for (final int targetPid : writeToPids) {
                    final Optional<Writer> optWriter = mConfig.getWriter(targetPid);
                    if (optWriter.isPresent()) {
                        final Writer writer = optWriter.get();
                        writeLogEntry(writer, entry);
                        if (entry.isAnr()) {
                            writeAnrTraces(writer, pid);
                            if (pid != targetPid) {
                                writeAnrTraces(writer, targetPid);
                            }
                        }
                    }
                }
            }

        } catch (final IOException | InterruptedException e) {
            CsLog.e("Encountered exception when recording LogCat", e);
        }
    }

    private void handleSystemServerLog(final LogEntry entry) throws IOException {
        final Pair<Integer, String> startProcessInfo = entry.checkStartProcessInfo();
        if (startProcessInfo != null) {
            createProcess(entry, startProcessInfo.first, startProcessInfo.second);
            return;
        }

        final int endProcessPid = entry.checkEndProcessInfo();
        if (endProcessPid != -1) {
            deleteProcess(entry, endProcessPid);
        }
    }

    private void handleDebuggerdLog(final LogEntry entry) throws IOException {
        if (mDebuggedPids.length == 0) {
            return;
        }

        for (final int pid : mDebuggedPids) {
            final Optional<Writer> writer = mConfig.getWriter(pid);
            if (writer.isPresent()) {
                writeLogEntry(writer.get(), entry);
            }
        }

        if (entry.isJniCrashLogEnded()) {
            mDebuggedPids = Config.EMPTY_PID_ARRAY;
        }
    }

    private void createProcess(final LogEntry entry, final int pid, final String processName) throws IOException {
        final Pattern filter = mConfig.getFilter();
        if (!filter.matcher(processName).find()) {
            return;
        }

        mConfig.removeExpiredLogs();

        final Optional<Writer> optWriter = mConfig.startRecording(pid, Optional.of(processName), entry.timestamp());
        if (optWriter.isPresent()) {
            final Writer writer = optWriter.get();
            writeLogEntry(writer, entry);
        }
    }

    private void deleteProcess(final LogEntry entry, final int pid) {
        mConfig.stopRecording(pid, new Function<Writer, Void>() {
            @Override
            public Void apply(final Writer writer) {
                try {
                    writeLogEntry(writer, entry);
                } catch (final IOException e) {
                    // Ignore.
                }
                return null;
            }
        });
    }

    private void writeLogEntry(final Writer writer, final LogEntry entry) throws IOException {
        mLogEntryChunk.set("log_level", entry.logLevelChar());
        mLogEntryChunk.set("date", String.format(Locale.ROOT, "%1$tH:%1$tM:%1$tS.%1$tL", entry.timestamp()));
        mLogEntryChunk.set("tag", entry.tag());
        mLogEntryChunk.set("message", entry.message());
        mLogEntryChunk.set("pid", entry.pid());
        mLogEntryChunk.set("tid", entry.tid());

        mLogEntryChunk.render(writer);
    }

    private void writeAnrTraces(final Writer writer, final int pid) throws IOException {
        mLogAnrEntryPrefixChunk.render(writer);
        BufferedReader tracesFile = null;
        try {
            tracesFile = new BufferedReader(new FileReader("/data/anr/traces.txt"));
            final String beginAnr = "----- pid " + pid + " at ";
            final String endAnr = "----- end " + pid;
            boolean shouldLog = false;

            while (true) {
                final String line = tracesFile.readLine();
                if (line == null) {
                    break;
                }
                if (!shouldLog && line.startsWith(beginAnr)) {
                    shouldLog = true;
                }
                if (shouldLog) {
                    writer.write(line);
                    writer.write('\n');
                }
                if (shouldLog && line.startsWith(endAnr)) {
                    shouldLog = false;
                }
            }
        } finally {
            Closeables.closeQuietly(tracesFile);
            mLogAnrEntrySuffixChunk.render(writer);
        }
    }
}

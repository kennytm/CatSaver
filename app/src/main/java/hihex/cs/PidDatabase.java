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

import android.text.TextUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class PidDatabase {
    private static final File PROC_FILE = new File("/proc");
    private static final Pattern UNSAFE_SHELL_CHARACTERS = Pattern.compile("[^0-9a-zA-Z_@%+=:,./-]");


    private ArrayList<PidEntry> mEntries = new ArrayList<>();

    private static String getProcessName(final String pidString) {
        try {
            final File file = new File("/proc/" + pidString + "/cmdline");
            final String processName = Files.toString(file, Charsets.UTF_8).trim();
            if (!processName.isEmpty()) {
                final String[] arguments = processName.split("\0");
                final StringBuilder builder = new StringBuilder();
                boolean isFirst = true;
                for (final String argument : arguments) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        builder.append(' ');
                    }
                    if (TextUtils.isEmpty(argument)) {
                        builder.append("''");
                    } else if (!UNSAFE_SHELL_CHARACTERS.matcher(argument).find()) {
                        builder.append(argument);
                    } else {
                        builder.append('\'');
                        builder.append(argument.replace("'", "'\"'\"'"));
                        builder.append('\'');
                    }
                }
                return builder.toString();
            }
        } catch (IOException e) {
            // Ignore.
        }

        try {
            final File file = new File("/proc/" + pidString + "/comm");
            final String processName = Files.toString(file, Charsets.UTF_8).trim();
            if (!processName.isEmpty()) {
                return processName;
            }
        } catch (IOException e) {
            // Ignore.
        }

        return "PID:" + pidString;
    }

    public synchronized void refresh() {
        // Dump all running processes from /proc
        final HashMap<Integer, String> pidToProcessNames = new HashMap<>();
        for (final String pidString : PROC_FILE.list()) {
            if (!TextUtils.isDigitsOnly(pidString)) {
                continue;
            }
            final int pid = Integer.parseInt(pidString);
            final String processName = getProcessName(pidString);
            pidToProcessNames.put(pid, processName);
        }

        // Record all entries that are dead.
        final ArrayList<PidEntry> removableEntries = new ArrayList<>();
        for (final PidEntry entry : mEntries) {
            final Object isExistingProcess = pidToProcessNames.remove(entry.pid);
            if (isExistingProcess == null) {
                removableEntries.add(entry);
                entry.close();
            }
        }
        mEntries.removeAll(removableEntries);

        // Do the update.
        for (final Map.Entry<Integer, String> input : pidToProcessNames.entrySet()) {
            mEntries.add(new PidEntry(input.getKey(), input.getValue()));
        }
    }

    /** Find the pid corresponding to the filename, if still recording. Returns -1 if not recording. */
    public synchronized int findPid(final String filename) {
        for (final PidEntry entry : mEntries) {
            if (entry.path.isPresent()) {
                final File path = entry.path.get();
                final String entryFilename = entry.path.get().getName();
                if (filename.equals(entryFilename)) {
                    return entry.pid;
                }
            }
        }
        return -1;
    }

    /** Obtains the list of running processes. */
    public synchronized List<HashMap<String, String>> runningProcesses() {
        final ArrayList<PidEntry> entries = mEntries;
        Collections.sort(entries, new Comparator<PidEntry>() {
            @Override
            public int compare(final PidEntry lhs, final PidEntry rhs) {
                final File aFile = new File("/proc/" + lhs.pid + "/comm");
                final File bFile = new File("/proc/" + rhs.pid + "/comm");
                final long aTime = aFile.lastModified();
                final long bTime = bFile.lastModified();
                int res = Longs.compare(bTime, aTime);
                if (res == 0) {
                    res = Ints.compare(rhs.pid, lhs.pid);
                }
                return res;
            }
        });
        return Lists.transform(entries, new Function<PidEntry, HashMap<String, String>>() {
            @Override
            public HashMap<String, String> apply(final PidEntry input) {
                final HashMap<String, String> summary = new HashMap<String, String>(3);
                summary.put("pid", String.valueOf(input.pid));
                summary.put("name", input.processName);
                if (input.writer.isPresent()) {
                    summary.put("recording", "true");
                }
                return summary;
            }
        });
    }

    public synchronized int findPidForExactProcessName(final String processName) {
        for (final PidEntry entry : mEntries) {
            if (processName.equals(entry.processName)) {
                return entry.pid;
            }
        }
        return -1;
    }

    private int getEntryIndex(final int pid) {
        int i = 0;
        for (final PidEntry entry : mEntries) {
            if (entry.pid == pid) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    public synchronized Optional<PidEntry> getEntry(final int pid) {
        final int index = getEntryIndex(pid);
        if (index >= 0) {
            return Optional.of(mEntries.get(index));
        } else {
            return Optional.absent();
        }
    }

    /** Start recording logs for the specified pid. */
    public synchronized Optional<Writer> startRecording(final int pid,
                                                        final Optional<String> processName,
                                                        final File parent,
                                                        final Date timestamp) throws IOException {
        int index = getEntryIndex(pid);
        final PidEntry oldEntry;
        if (index >= 0) {
            oldEntry = mEntries.get(index);
        } else if (processName.isPresent()) {
            oldEntry = new PidEntry(pid, processName.get());
        } else {
            return Optional.absent();
        }

        final PidEntry newEntry = oldEntry.open(parent, timestamp);
        if (newEntry != oldEntry) {
            if (index >= 0) {
                mEntries.set(index, newEntry);
            } else {
                mEntries.add(newEntry);
            }
        }
        return newEntry.writer;
    }

    public synchronized void stopRecording(final int pid, final Function<Writer, ?> cleanup) {
        final int index = getEntryIndex(pid);
        if (index < 0) {
            return;
        }

        final PidEntry oldEntry = mEntries.get(index);
        if (oldEntry.writer.isPresent()) {
            cleanup.apply(oldEntry.writer.get());
        }
        final PidEntry newEntry = oldEntry.close();
        if (newEntry != oldEntry) {
            mEntries.set(index, newEntry);
        }
    }

    public int countRecordingEntries() {
        int count = 0;
        for (final PidEntry entry : mEntries) {
            if (entry.writer.isPresent()) {
                count += 1;
            }
        }
        return count;
    }
}

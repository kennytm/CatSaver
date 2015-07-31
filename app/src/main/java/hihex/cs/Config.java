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
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.google.common.eventbus.EventBus;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.x5.template.Chunk;
import com.x5.template.Theme;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Shared configuration between the WebServer and LogCollector.
 */
public final class Config {
    public static final String SHARED_PREFS_FILTER_KEY = "filter";
    public static final String SHARED_PREFS_PURGE_FILESIZE_KEY = "purge_filesize";
    public static final String SHARED_PREFS_PURGE_DURATION_KEY = "purge_duration";
    public static final String SHARED_PREFS_SHOW_INDICATOR_KEY = "show_indicator";
    public static final String SHARED_PREFS_RUN_ON_BOOT_KEY = "run_on_boot";
    public static final String SHARED_PREFS_LOG_FILTER_KEY = "log_filter";

    public static final int[] EMPTY_PID_ARRAY = {};

    //{{{ Fixed configuration. These cannot be modified by anyone.

    /**
     * The folder to store all the logs.
     */
    public final File logFolder;

    /**
     * The theme that provides the Chunk templates.
     */
    public final Theme theme;

    public final EventBus eventBus = new EventBus();

    /**
     * The preferences.
     */
    public final SharedPreferences sharedPreferences;

    public final Context context;

    private final PackageManager mPackageManager;

    private final Chunk mLogPrefixChunk;
    private final Chunk mLogSuffixChunk;

    //}}}

    //{{{ In-memory configuration. These may be changed at anytime.

    /**
     * The active process-name filter.
     */
    private volatile Pattern mFilter;

    private volatile String mRawLogFilter;
    private volatile boolean mHasDefaultLogFilter;
    private volatile LogEntryFilter mLogFilter;

    private volatile long mPurgeFilesize;
    private volatile long mPurgeDuration;

    /**
     * The database storing all active PIDs.
     */
    private final PidDatabase mPidDatabase = new PidDatabase();

    private final AtomicInteger mSystemServerPid = new AtomicInteger(-1);
    private final AtomicInteger mDebuggerdPid = new AtomicInteger(-1);

    //}}}

    private static SharedPreferences getSharedPreferences(final Context context) {
        return context.getSharedPreferences("default", Context.MODE_PRIVATE);
    }

    public Config(final Context context) {
        logFolder = new File(context.getFilesDir(), "logs");
        logFolder.mkdir();

        this.context = context;
        mPackageManager = context.getPackageManager();
        theme = ChunkTheme.create(context);
        sharedPreferences = getSharedPreferences(context);
        final String filter = sharedPreferences.getString(SHARED_PREFS_FILTER_KEY, "^(com\\.)?hihex\\.(?!cs$)");
        mFilter = Pattern.compile(filter);
        mPurgeFilesize = sharedPreferences.getLong(SHARED_PREFS_PURGE_FILESIZE_KEY, -1);
        mPurgeDuration = sharedPreferences.getLong(SHARED_PREFS_PURGE_DURATION_KEY, -1);
        readLogFilter();

        mLogPrefixChunk = theme.makeChunk("log#prefix");
        mLogSuffixChunk = theme.makeChunk("log#suffix");
    }

    private String readDefaultLogFilter() {
        final InputStream stream = context.getResources().openRawResource(R.raw.default_filter_config);
        final Reader reader = new InputStreamReader(stream, Charsets.UTF_8);
        try {
            return CharStreams.toString(reader);
        } catch (final IOException e) {
            // Should not happen
            throw new RuntimeException(e);
        } finally {
            Closeables.closeQuietly(reader);
        }
    }

    private void readLogFilter() {
        String logFilter = sharedPreferences.getString(SHARED_PREFS_LOG_FILTER_KEY, null);
        if (logFilter != null) {
            mHasDefaultLogFilter = false;
        } else {
            mHasDefaultLogFilter = true;
            logFilter = readDefaultLogFilter();
        }
        mRawLogFilter = logFilter;
        mLogFilter = LogEntryFilter.parse(logFilter);
    }

    public Pattern getFilter() {
        return mFilter;
    }

    public long getPurgeFilesize() {
        return mPurgeFilesize;
    }

    public long getPurgeDuration() {
        return mPurgeDuration;
    }

    public boolean shouldShowIndicator() {
        return sharedPreferences.getBoolean(SHARED_PREFS_SHOW_INDICATOR_KEY, true);
    }

    public static boolean shouldRunOnBoot(final Context context) {
        final SharedPreferences sharedPreferences = getSharedPreferences(context);
        return sharedPreferences.getBoolean(SHARED_PREFS_RUN_ON_BOOT_KEY, true);
    }

    public boolean shouldRunOnBoot() {
        return sharedPreferences.getBoolean(SHARED_PREFS_RUN_ON_BOOT_KEY, true);
    }

    public void updateSettings(final Pattern filter,
                               final long purgeFilesize,
                               final long purgeDuration,
                               final boolean shouldShowIndicator,
                               final boolean shouldRunOnBoot) {
        mFilter = filter;
        mPurgeFilesize = purgeFilesize;
        mPurgeDuration = purgeDuration;
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SHARED_PREFS_FILTER_KEY, filter.pattern());
        editor.putLong(SHARED_PREFS_PURGE_FILESIZE_KEY, purgeFilesize);
        editor.putLong(SHARED_PREFS_PURGE_DURATION_KEY, purgeDuration);
        editor.putBoolean(SHARED_PREFS_SHOW_INDICATOR_KEY, shouldShowIndicator);
        editor.putBoolean(SHARED_PREFS_RUN_ON_BOOT_KEY, shouldRunOnBoot);
        editor.apply();
        removeExpiredLogs();
        eventBus.post(new Events.RecordIndicatorVisibility(shouldShowIndicator));
    }

    public void updateFilters(final Pattern filter, String logFilter) {
        if (logFilter == null) {
            mHasDefaultLogFilter = true;
            mRawLogFilter = readDefaultLogFilter();
            mLogFilter = LogEntryFilter.parse(mRawLogFilter);
        } else {
            mLogFilter = LogEntryFilter.parse(logFilter);
            mHasDefaultLogFilter = false;
            mRawLogFilter = logFilter;
        }
        mFilter = filter;
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SHARED_PREFS_FILTER_KEY, filter.pattern());
        editor.putString(SHARED_PREFS_LOG_FILTER_KEY, logFilter);
        editor.apply();
    }

    public void refreshPids() {
        mSystemServerPid.set(-1);
        mDebuggerdPid.set(-1);
        mPidDatabase.refresh();
    }

    public List<HashMap<String, String>> runningProcesses() {
        return mPidDatabase.runningProcesses();
    }

    private int findPidForExactProcessName(final AtomicInteger cache, final String processName) {
        final int pid = cache.get();
        if (pid != -1) {
            return pid;
        }

        final int newPid = mPidDatabase.findPidForExactProcessName(processName);
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
            mPidDatabase.startRecording(pid, processName, logFolder, timestamp, new Function<PidEntry, Void>() {
                @Override
                public Void apply(final PidEntry entry) {
                    final Writer writer = entry.writer.get();
                    optWriter[0] = writer;
                    try {
                        writeHeader(writer, entry.pid, processName.or(entry.processName), timestamp);
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
        eventBus.post(new Events.RecordCount(mPidDatabase.countRecordingEntries()));
        return Optional.fromNullable(optWriter[0]);
    }

    public void stopRecording(final int pid, final Function<Writer, ?> cleanup) {
        mPidDatabase.stopRecording(pid, new Function<Writer, Void>() {
            @Override
            public Void apply(final Writer writer) {
                try {
                    cleanup.apply(writer);
                    mLogSuffixChunk.render(writer);
                } catch (final IOException e) {
                    // Ignore.
                }
                return null;
            }
        });
        eventBus.post(new Events.RecordCount(mPidDatabase.countRecordingEntries()));
    }

    private void writeHeader(final Writer writer, final int pid, final String processName, final Date timestamp)
            throws IOException {
        final int colon = processName.indexOf(':');
        final String packageName = (colon >= 0) ? processName.substring(0, colon) : processName;

        try {
            final PackageInfo pkgInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
            mLogPrefixChunk.put("version", pkgInfo.versionName);
            mLogPrefixChunk.put("version_code", String.valueOf(pkgInfo.versionCode));
        } catch (final PackageManager.NameNotFoundException e) {
            // No package information. Ignore.
        }

        final HashMap<String, String> buildSettings = new HashMap<>(8);
        buildSettings.put("product", Build.PRODUCT);
        buildSettings.put("device", Build.DEVICE);
        buildSettings.put("manufacturer", Build.MANUFACTURER);
        buildSettings.put("board", Build.BOARD);
        buildSettings.put("brand", Build.BRAND);
        buildSettings.put("model", Build.MODEL);
        buildSettings.put("release", Build.VERSION.RELEASE);
        buildSettings.put("abi", Build.CPU_ABI);
        buildSettings.put("api_level", String.valueOf(Build.VERSION.SDK_INT));

        mLogPrefixChunk.set("date", timestamp.toString());
        mLogPrefixChunk.set("process", processName);
        mLogPrefixChunk.set("pid", pid);
        mLogPrefixChunk.set("build", buildSettings);
        mLogPrefixChunk.set("addresses", TextUtils.join(" / ", IpAddresses.getAllIpAddresses()));

        mLogPrefixChunk.render(writer);
    }

    public Optional<Writer> getWriter(final int pid) {
        final Optional<PidEntry> entry = mPidDatabase.getEntry(pid);
        if (entry.isPresent()) {
            return entry.get().writer;
        } else {
            return Optional.absent();
        }
    }

    public int findPid(final String filename) {
        return mPidDatabase.findPid(filename);
    }

    public String getProcessName(final int pid) {
        return mPidDatabase.getEntry(pid).get().processName;
    }

    public TreeMultimap<Long, File> listLogFiles() {
        final File[] files = logFolder.listFiles();
        final TreeMultimap<Long, File> sortedFiles =
                TreeMultimap.create(Ordering.natural().reverse(), Ordering.natural());
        for (final File file : files) {
            sortedFiles.put(file.lastModified(), file);
        }
        return sortedFiles;
    }

    public void removeExpiredLogs() {
        long purgeDuration = mPurgeDuration;
        long purgeFilesize = mPurgeFilesize;

        final TreeMultimap<Long, File> files = listLogFiles();

        // Don't use `TreeMultimap.create(files)`! The key comparator will be reverted to the natural one.
        final TreeMultimap<Long, File> filesToKeep = TreeMultimap.create(Ordering.natural().reverse(), Ordering.natural());
        filesToKeep.putAll(files);

        if (purgeDuration >= 0) {
            final long expireDate = System.currentTimeMillis() - purgeDuration;
            filesToKeep.asMap().tailMap(expireDate).clear();
        }

        if (purgeFilesize >= 0) {
            final Iterator<File> iterator = filesToKeep.values().iterator();
            long currentFilesize = 0;
            while (iterator.hasNext()) {
                final File file = iterator.next();
                if (currentFilesize > purgeFilesize) {
                    iterator.remove();
                } else {
                    currentFilesize += file.length();
                }
            }
        }

        files.entries().removeAll(filesToKeep.entries());
        for (final File file : files.values()) {
            file.delete();
        }
    }

    public final void startRecordingExistingProcesses() {
        final Date now = new Date();
        final Pattern filter = getFilter();
        for (final HashMap<String, String> entry : mPidDatabase.runningProcesses()) {
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

    public String getRawLogFilter() {
        return mRawLogFilter;
    }

    public boolean hasDefaultLogFilter() {
        return mHasDefaultLogFilter;
    }

    public LogEntryFilter getLogFilter() {
        return mLogFilter;
    }

    public int[] getFilteredPidsForLog(final LogEntry entry, final int sourcePid) {
        final Optional<PidEntry> sourcePidEntry = mPidDatabase.getEntry(sourcePid);
        if (!sourcePidEntry.isPresent()) {
            return EMPTY_PID_ARRAY;
        }

        final String source = sourcePidEntry.get().processName;
        final Set<String> targets = mPidDatabase.listRecordingProcessNames();
        final HashSet<String> filtered = mLogFilter.filter(entry, source, targets);
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
                targetPid = mPidDatabase.findPidForExactProcessName(target);
            }
            pids[i] = targetPid;
            ++i;
        }
        return pids;
    }
}

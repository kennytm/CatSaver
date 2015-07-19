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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.google.common.eventbus.EventBus;
import com.x5.template.Chunk;
import com.x5.template.Theme;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

    private volatile long mPurgeFilesize;
    private volatile long mPurgeDuration;

    /**
     * The database storing all active PIDs.
     */
    private final PidDatabase mPidDatabase = new PidDatabase();

    private final AtomicInteger mSystemServerPid = new AtomicInteger(-1);
    private final AtomicInteger mDebuggerdPid = new AtomicInteger(-1);

    //}}}

    public Config(final Context context) {
        logFolder = new File(context.getFilesDir(), "logs");
        logFolder.mkdir();

        this.context = context;
        mPackageManager = context.getPackageManager();
        theme = ChunkTheme.create(context);
        sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
        final String filter = sharedPreferences.getString(SHARED_PREFS_FILTER_KEY, "^(com\\.)?hihex\\.");
        mFilter = Pattern.compile(filter);
        mPurgeFilesize = sharedPreferences.getLong(SHARED_PREFS_PURGE_FILESIZE_KEY, -1);
        mPurgeDuration = sharedPreferences.getLong(SHARED_PREFS_PURGE_DURATION_KEY, -1);

        mLogPrefixChunk = theme.makeChunk("log#prefix");
        mLogSuffixChunk = theme.makeChunk("log#suffix");
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

    public void updateSettings(final Pattern filter,
                               final long purgeFilesize,
                               final long purgeDuration,
                               final boolean shouldShowIndicator) {
        mFilter = filter;
        mPurgeFilesize = purgeFilesize;
        mPurgeDuration = purgeDuration;
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SHARED_PREFS_FILTER_KEY, filter.pattern());
        editor.putLong(SHARED_PREFS_PURGE_FILESIZE_KEY, purgeFilesize);
        editor.putLong(SHARED_PREFS_PURGE_DURATION_KEY, purgeDuration);
        editor.putBoolean(SHARED_PREFS_SHOW_INDICATOR_KEY, shouldShowIndicator);
        editor.apply();
        removeExpiredLogs();
        eventBus.post(new Events.RecordIndicatorVisibility(shouldShowIndicator));
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
        final Optional<Writer> optWriter = mPidDatabase.startRecording(pid, processName, logFolder, timestamp);
        if (optWriter.isPresent()) {
            final Writer writer = optWriter.get();
            final String proc;
            if (processName.isPresent()) {
                proc = processName.get();
            } else {
                proc = mPidDatabase.getEntry(pid).get().processName;
            }
            writeHeader(writer, pid, proc, timestamp);
        }
        eventBus.post(new Events.RecordCount(mPidDatabase.countRecordingEntries()));
        return optWriter;
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
}

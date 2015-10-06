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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import com.google.common.collect.TreeMultimap;
import com.google.common.io.Closeables;
import com.x5.template.Chunk;
import com.x5.template.Theme;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class ChunkRenderer {
    private final Theme mTheme;

    private final PackageManager mPackageManager;

    private final Chunk mLogPrefixChunk;
    private final Chunk mLogSuffixChunk;
    private final Chunk mLogEntryChunk;
    private final Chunk mLogAnrEntryPrefixChunk;
    private final Chunk mLogAnrEntrySuffixChunk;

    public ChunkRenderer(final Context context) {
        mTheme = ChunkTheme.create(context);
        mPackageManager = context.getPackageManager();

        mLogPrefixChunk = mTheme.makeChunk("log#prefix");
        mLogSuffixChunk = mTheme.makeChunk("log#suffix");
        mLogEntryChunk = mTheme.makeChunk("log#entry");
        mLogAnrEntryPrefixChunk = mTheme.makeChunk("log#anr_entry_prefix");
        mLogAnrEntrySuffixChunk = mTheme.makeChunk("log#anr_entry_suffix");
    }

    public void writeHeader(final Writer writer, final int pid, final String processName, final Date timestamp)
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
        //noinspection deprecation
        buildSettings.put("abi", Build.CPU_ABI);
        buildSettings.put("api_level", String.valueOf(Build.VERSION.SDK_INT));

        mLogPrefixChunk.set("date", timestamp.toString());
        mLogPrefixChunk.set("process", processName);
        mLogPrefixChunk.set("pid", pid);
        mLogPrefixChunk.set("build", buildSettings);
        mLogPrefixChunk.set("addresses", TextUtils.join(" / ", IpAddresses.getAllIpAddresses()));

        mLogPrefixChunk.render(writer);
    }

    public void writeFooter(final Writer writer) throws IOException {
        mLogSuffixChunk.render(writer);
    }

    public void writeLogEntry(final Writer writer, final LogEntry entry) throws IOException {
        mLogEntryChunk.set("log_level", entry.logLevelChar());
        mLogEntryChunk.set("date", String.format(Locale.ROOT, "%1$tH:%1$tM:%1$tS.%1$tL", entry.timestamp()));
        mLogEntryChunk.set("tag", entry.tag());
        mLogEntryChunk.set("message", entry.message());
        mLogEntryChunk.set("pid", entry.pid());
        mLogEntryChunk.set("tid", entry.tid());

        mLogEntryChunk.render(writer);
    }

    public void writeAnrTraces(final Writer writer, final int pid) throws IOException {
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

    public String renderIndex(final LogFiles logFiles, final PidDatabase database) {
        final List<HashMap<String, String>> processes = database.runningProcesses();
        final Chunk chunk = mTheme.makeChunk("index");

        final TreeMultimap<Long, File> files = logFiles.list();

        final ArrayList<HashMap<String, String>> encodedFiles = new ArrayList<>(files.size());
        long totalSize = 0;
        for (final File file : files.values()) {
            final HashMap<String, String> content = new HashMap<>(5);
            final long fileSize = file.length();
            final String name = file.getName();
            final int pid = database.findPid(name);

            content.put("file_name", name);
            content.put("last_modified", String.valueOf(file.lastModified()));
            content.put("file_size", String.valueOf(fileSize));
            if (pid != -1) {
                content.put("pid", String.valueOf(pid));
                content.put("process_name", database.getProcessName(pid));
            }

            encodedFiles.add(content);
            totalSize += fileSize;
        }

        chunk.set("files", encodedFiles);
        chunk.set("total_size", String.valueOf(totalSize));
        chunk.set("processes", processes);

        return chunk.toString();
    }

    public String renderSettings(final Preferences preferences) {
        final Chunk chunk = mTheme.makeChunk("settings");
        chunk.put("filter", preferences.getFilter().pattern());
        chunk.put("filesize", String.valueOf(preferences.getPurgeFilesize()));
        chunk.put("date", String.valueOf(preferences.getPurgeDuration()));
        chunk.put("show_indicator", String.valueOf(preferences.shouldShowIndicator()));
        chunk.put("split_size", String.valueOf(preferences.getSplitSize()));
        chunk.put("run_on_boot", String.valueOf(preferences.shouldRunOnBoot()));
        return chunk.toString();
    }

    public String renderFilterSettings(final Preferences preferences) {
        final Chunk chunk = mTheme.makeChunk("filters");
        chunk.put("filter", preferences.getFilter().pattern());
        chunk.put("log_filter", preferences.getRawLogFilter());
        if (preferences.hasDefaultLogFilter()) {
            chunk.put("log_filter_use_default", "true");
        }
        return chunk.toString();
    }

    public String renderSettingsError(final String source, final String title, final Throwable e) {
        final Chunk chunk = mTheme.makeChunk("settings_error");
        chunk.put("title", title);
        chunk.put("source", source);
        if (e != null) {
            chunk.put("message", e.getLocalizedMessage());
        }
        return chunk.toString();
    }

    public String renderLive() {
        final Chunk chunk = mTheme.makeChunk("live");
        return chunk.toString();
    }
}

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
import android.content.SharedPreferences;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.regex.Pattern;

/**
 * Persistent preferences
 */
public final class Preferences {
    public static final String SHARED_PREFS_FILTER_KEY = "filter";
    public static final String SHARED_PREFS_PURGE_FILESIZE_KEY = "purge_filesize";
    public static final String SHARED_PREFS_PURGE_DURATION_KEY = "purge_duration";
    public static final String SHARED_PREFS_SHOW_INDICATOR_KEY = "show_indicator";
    public static final String SHARED_PREFS_RUN_ON_BOOT_KEY = "run_on_boot";
    public static final String SHARED_PREFS_LOG_FILTER_KEY = "log_filter";
    public static final String SHARED_PREFS_SPLIT_SIZE_KEY = "split_size";

    /**
     * The preferences.
     */
    public final SharedPreferences mSharedPreferences;

    private final Context mContext;

    /**
     * The active process-name filter.
     */
    private volatile Pattern mFilter;

    private volatile String mRawLogFilter;
    private volatile boolean mHasDefaultLogFilter;
    private volatile LogEntryFilter mLogFilter;

    private volatile long mPurgeFilesize;
    private volatile long mPurgeDuration;
    private volatile long mSplitSize;

    private static SharedPreferences getSharedPreferences(final Context context) {
        return context.getSharedPreferences("default", Context.MODE_PRIVATE);
    }

    public Preferences(final Context context) {
        mContext = context;
        mSharedPreferences = getSharedPreferences(context);
        final String filter = mSharedPreferences.getString(SHARED_PREFS_FILTER_KEY, "^(com\\.)?hihex\\.(?!cs$)");
        mFilter = Pattern.compile(filter);
        mPurgeFilesize = mSharedPreferences.getLong(SHARED_PREFS_PURGE_FILESIZE_KEY, -1);
        mPurgeDuration = mSharedPreferences.getLong(SHARED_PREFS_PURGE_DURATION_KEY, -1);
        mSplitSize = mSharedPreferences.getLong(SHARED_PREFS_SPLIT_SIZE_KEY, 32768);
        readLogFilter();
    }

    private String readDefaultLogFilter() {
        final InputStream stream = mContext.getResources().openRawResource(R.raw.default_filter_config);
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
        String logFilter = mSharedPreferences.getString(SHARED_PREFS_LOG_FILTER_KEY, null);
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

    public long getSplitSize() {
        return mSplitSize;
    }

    public long getPurgeDuration() {
        return mPurgeDuration;
    }

    public boolean shouldShowIndicator() {
        return mSharedPreferences.getBoolean(SHARED_PREFS_SHOW_INDICATOR_KEY, true);
    }

    public static boolean shouldRunOnBoot(final Context context) {
        final SharedPreferences sharedPreferences = getSharedPreferences(context);
        return sharedPreferences.getBoolean(SHARED_PREFS_RUN_ON_BOOT_KEY, true);
    }

    public boolean shouldRunOnBoot() {
        return mSharedPreferences.getBoolean(SHARED_PREFS_RUN_ON_BOOT_KEY, true);
    }

    public void updateSettings(final Pattern filter,
                               final long purgeFilesize,
                               final long purgeDuration,
                               final boolean shouldShowIndicator,
                               final boolean shouldRunOnBoot,
                               final long splitSize) {
        mFilter = filter;
        mPurgeFilesize = purgeFilesize;
        mSplitSize = splitSize;
        mPurgeDuration = purgeDuration;
        final SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(SHARED_PREFS_FILTER_KEY, filter.pattern());
        editor.putLong(SHARED_PREFS_PURGE_FILESIZE_KEY, purgeFilesize);
        editor.putLong(SHARED_PREFS_PURGE_DURATION_KEY, purgeDuration);
        editor.putBoolean(SHARED_PREFS_SHOW_INDICATOR_KEY, shouldShowIndicator);
        editor.putBoolean(SHARED_PREFS_RUN_ON_BOOT_KEY, shouldRunOnBoot);
        editor.putLong(SHARED_PREFS_SPLIT_SIZE_KEY, splitSize);
        editor.apply();

        Events.bus.post(new Events.PreferencesUpdated(this));

        //removeExpiredLogs();
        //eventBus.post(new Events.RecordIndicatorVisibility(shouldShowIndicator));
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
        final SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(SHARED_PREFS_FILTER_KEY, filter.pattern());
        editor.putString(SHARED_PREFS_LOG_FILTER_KEY, logFilter);
        editor.apply();

        Events.bus.post(new Events.PreferencesUpdated(this));
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
}

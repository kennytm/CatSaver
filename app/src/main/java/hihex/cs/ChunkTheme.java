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

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.text.format.Formatter;

import com.x5.template.Chunk;
import com.x5.template.ContentSource;
import com.x5.template.Snippet;
import com.x5.template.Theme;
import com.x5.template.filters.BasicFilter;
import com.x5.template.providers.AndroidTemplates;

import java.util.HashMap;

/**
 * Customized Chunk template theme.
 */
public final class ChunkTheme {
    private ChunkTheme() {}

    /** Chunk filter to convert number of bytes to readable file size. */
    private static final class FileSizeFilter extends BasicFilter {
        private final Context mContext;

        public FileSizeFilter(final Context context) {
            mContext = context;
        }

        @Override
        public String transformText(final Chunk chunk, final String text, final String[] args) {
            try {
                final long fileSize = Long.parseLong(text);
                if (fileSize < 1024) {
                    return mContext.getString(R.string.chunk_kibibytes_format, fileSize / 1024.0f);
                } else {
                    return Formatter.formatShortFileSize(mContext, fileSize);
                }
            } catch (final NumberFormatException e) {
                return text;
            }
        }

        @Override
        public String getFilterName() {
            return "file_size";
        }
    }

    /** Chunk filter to convert number of milliseconds since the epoch to a relative number to now. */
    private static final class RelativeTimeFilter extends BasicFilter {
        private final Context mContext;

        public RelativeTimeFilter(final Context context) {
            mContext = context;
        }

        @Override
        public String transformText(final Chunk chunk, final String text, final String[] args) {
            try {
                final long milliseconds = Long.parseLong(text);
                final long transition = 3 * DateUtils.DAY_IN_MILLIS;
                final CharSequence relTime = DateUtils.getRelativeDateTimeString(mContext, milliseconds, 0, transition, 0);
                return relTime.toString();
            } catch (final NumberFormatException e) {
                return text;
            }
        }

        @Override
        public String getFilterName() {
            return "relative_time";
        }
    }

    private static final class ResourceContentSource implements ContentSource {
        private final Resources mResources;
        private final String mPackageName;
        private final HashMap<String, Integer> mCachedIds = new HashMap<>();

        public ResourceContentSource(final Context context) {
            mResources = context.getResources();
            mPackageName = context.getPackageName();
        }

        @Override
        public String fetch(final String itemName) {
            final int id = getId(itemName);
            if (id != 0) {
                return mResources.getString(id);
            } else {
                return '«' + itemName + '»';
            }
        }

        @Override
        public boolean provides(String itemName) {
            return true;
        }

        @Override
        public String getProtocol() {
            return "r";
        }

        @Override
        public Snippet getSnippet(String snippetName) {
            return null;
        }

        private int getId(final String itemName) {
            final Integer cachedId = mCachedIds.get(itemName);
            if (cachedId != null) {
                return cachedId;
            }
            final int id = mResources.getIdentifier("chunk_" + itemName, "string", mPackageName);
            mCachedIds.put(itemName, id);
            return id;
        }
    }

    /** Creates a customized Chunk theme. */
    public static Theme create(final Context context) {
        final AndroidTemplates provider = new AndroidTemplates(context);
        final Theme theme = new Theme(provider);
        theme.registerFilter(new FileSizeFilter(context));
        theme.registerFilter(new RelativeTimeFilter(context));
        theme.addProtocol(new ResourceContentSource(context));
        return theme;
    }
}

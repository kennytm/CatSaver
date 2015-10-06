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

import android.util.Log;

import com.moandjiezana.toml.Toml;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class LogEntryFilter {
    public static final String LIVE_SOURCE = "\0$live";

    private static final Pattern UNIVERSAL_PATTERN = Pattern.compile("\\A");

    private final Entry[] ignores;

    private final Entry[] snatches;

    private LogEntryFilter(final Entry[] ignores, final Entry[] snatches) {
        this.ignores = ignores;
        this.snatches = snatches;
    }

    public static LogEntryFilter parse(final String source) throws IllegalStateException, PatternSyntaxException {
        final RawFilter filter = new Toml().parse(source).to(RawFilter.class);
        final Entry[] ignores = RawEntry.compile(filter.ignore);
        final Entry[] snatches = RawEntry.compile(filter.snatch);
        return new LogEntryFilter(ignores, snatches);
    }

    private static HashSet<String> match(final Entry[] filterEntries,
                                         final LogEntry entry,
                                         final String source,
                                         final Collection<String> targets) {
        final HashSet<String> matchingTargets = new HashSet<>();

        for (final Entry filterEntry : filterEntries) {
            if (filterEntry.matches(entry, source)) {
                for (final String target : targets) {
                    if (filterEntry.targetMatches(target)) {
                        matchingTargets.add(target);
                    }
                }
            }
        }
        return matchingTargets;
    }

    public HashSet<String> filter(final LogEntry entry, final String source, final Set<String> targets) {
        final HashSet<String> snatched = match(snatches, entry, source, targets);
        if (targets.contains(source)) {
            snatched.add(source);
        }
        snatched.add(LIVE_SOURCE);
        final HashSet<String> ignored = match(ignores, entry, source, snatched);
        snatched.removeAll(ignored);
        return snatched;
    }

    static final class Entry {
        /**
         * The bitmask of all filtered log levels. For instance, to filter the Debug and Verbose levels, this field should
         * be set to {@code 1 << 2 | 1 << 3}.
         */
        final int logLevelBitmask;

        final Set<String> tags;

        final Pattern messagePattern;

        final Pattern sourcePackage;

        final Pattern targetPackage;

        Entry(final int logLevelBitmask,
              final Set<String> tags,
              final Pattern messagePattern,
              final Pattern sourcePackage,
              final Pattern targetPackage) {
            this.logLevelBitmask = logLevelBitmask;
            this.tags = tags;
            this.messagePattern = messagePattern;
            this.sourcePackage = sourcePackage;
            this.targetPackage = targetPackage;
        }

        boolean matches(final LogEntry entry, final String source) {
            if ((logLevelBitmask & 1 << entry.logLevel()) == 0) {
                return false;
            }
            if (!tags.contains(entry.tag())) {
                return false;
            }
            if (!messagePattern.matcher(entry.message()).find()) {
                return false;
            }
            if (!sourcePackage.matcher(source).find()) {
                return false;
            }
            return true;
        }

        boolean targetMatches(final String target) {
            return targetPackage.matcher(target).find();
        }
    }

    private static final class RawEntry {
        public String level;
        public Set<String> tags;
        public String message;
        public String source;
        public String target;

        private Entry compile() throws PatternSyntaxException {
            final int bitmask = parseLogLevelBitmask();
            final Set<String> realTags = (tags == null) ? UniversalSet.<String>instance() : tags;
            final Pattern messagePattern = (message == null) ? UNIVERSAL_PATTERN : Pattern.compile(message);
            final Pattern sourcePattern = (source == null) ? UNIVERSAL_PATTERN : Pattern.compile(source);
            final Pattern targetPattern = (target == null) ? UNIVERSAL_PATTERN : Pattern.compile(target);
            return new Entry(bitmask, realTags, messagePattern, sourcePattern, targetPattern);
        }

        static Entry[] compile(final RawEntry[] rawEntries) {
            final Entry[] entries = new Entry[rawEntries.length];
            int i = 0;
            for (final RawEntry rawEntry : rawEntries) {
                entries[i] = rawEntry.compile();
                ++i;
            }
            return entries;
        }

        private int parseLogLevelBitmask() {
            if (level == null) {
                return -1;
            }
            int logLevelBitmask = 0;
            for (final byte c : level.getBytes()) {
                switch (c) {
                    case 'D':
                    case 'd':
                        logLevelBitmask |= 1 << Log.DEBUG;
                        break;
                    case 'V':
                    case 'v':
                        logLevelBitmask |= 1 << Log.VERBOSE;
                        break;
                    case 'I':
                    case 'i':
                        logLevelBitmask |= 1 << Log.INFO;
                        break;
                    case 'W':
                    case 'w':
                        logLevelBitmask |= 1 << Log.WARN;
                        break;
                    case 'E':
                    case 'e':
                        logLevelBitmask |= 1 << Log.ERROR;
                        break;
                    case 'F':
                    case 'f':
                    case 'A':
                    case 'a':
                        logLevelBitmask |= 1 << Log.ASSERT;
                        break;
                    case '*':
                        return -1;
                    default:
                        break;
                }
            }
            return logLevelBitmask;
        }
    }

    private static final class RawFilter {
        public RawEntry[] ignore;
        public RawEntry[] snatch;
    }
}

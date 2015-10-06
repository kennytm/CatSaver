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

import android.util.JsonWriter;
import android.util.Log;
import android.util.Pair;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An entry reported by logcat.
 */
public final class LogEntry {
    private static final byte[] SYSTEM_RESTART_PAYLOAD = "\4SystemServer\0Entered the Android system server!".getBytes(Charsets.ISO_8859_1);
    private static final byte[] DEBUGGERD_RESTART_PAYLOAD_PREFIX = "\4\0debuggerd: ".getBytes(Charsets.ISO_8859_1);
    private static final byte[] TOMBSTONE_PAYLOAD_PREFIX = "\4DEBUG\0\nTombstone written to: /data/tombstones/tombstone_".getBytes(Charsets.ISO_8859_1);
    private static final byte[] CODE_AROUND_PC_PREFIX = "\4DEBUG\0\ncode around pc:".getBytes(Charsets.ISO_8859_1);

    private static final byte[] START_PROCESS_PAYLOAD_PREFIX = "\4ActivityManager\0Start proc ".getBytes(Charsets.ISO_8859_1);
    private static final Pattern START_PROCESS_PATTERN = Pattern.compile("^Start proc (\\S+)[^:]*: pid=([0-9]+)");
    private static final Pattern START_PROCESS_PATTERN_LOLLIPOP_MR1 = Pattern.compile("^Start proc ([0-9]+):([^/]+)");

    private static final byte[] FORCE_STOP_PROCESS_PAYLOAD_PREFIX = "\4ActivityManager\0Killing ".getBytes(Charsets.ISO_8859_1);
    private static final Pattern FORCE_STOP_PROCESS_PATTERN = Pattern.compile("^Killing (?:proc )?([0-9]+):");

    private static final byte[] KILL_PROCESS_PAYLOAD_PREFIX = "\4ActivityManager\0Process ".getBytes(Charsets.ISO_8859_1);
    private static final Pattern KILL_PROCESS_PATTERN = Pattern.compile("^Process \\S+ \\(pid ([0-9]+)\\) has died\\.?$");

    private static final Pattern JNI_SIGNAL_PATTERN = Pattern.compile("^Fatal signal (?:[0-9]+) \\([0-9A-Z?]+\\)");

    private static final byte[] ANR_PAYLOAD_PREFIX_DALVIKVM = "\4dalvikvm\0Wrote stack traces to '/data/anr/traces.txt'".getBytes(Charsets.ISO_8859_1);
    private static final byte[] ANR_PAYLOAD_PREFIX_ZYGOTE = "\4zygote\0Wrote stack traces to '/data/anr/traces.txt'".getBytes(Charsets.ISO_8859_1);
    private static final byte[] ANR_PAYLOAD_PREFIX_ART = "\4art\0Wrote stack traces to '/data/anr/traces.txt'".getBytes(Charsets.ISO_8859_1);

    private final byte[] mSharedArray;
    private final ByteBuffer mSharedBuffer;

    private int mPid;
    private int mTid;
    private int mSec;
    private int mNSec;
    private int mPayloadLength;
    private int mTagSeparator;
    private Optional<String> mTag = Optional.absent();
    private Optional<String> mMessage = Optional.absent();

    private String mPackageName = "[unknown package]";
    private String mThreadName = "[unknown thread]";

    public LogEntry() {
        mSharedArray = new byte[5120];
        mSharedBuffer = ByteBuffer.wrap(mSharedArray).order(ByteOrder.LITTLE_ENDIAN);
    }

    public LogEntry(final LogEntry entry) {
        mSharedArray = new byte[] {entry.mSharedArray[0]};
        mSharedBuffer = null;
        mTag = Optional.of(entry.tag());
        mMessage = Optional.of(entry.message());

        mPid = entry.mPid;
        mTid = entry.mTid;
        mSec = entry.mSec;
        mNSec = entry.mNSec;
        mPayloadLength = entry.mPayloadLength;
        mTagSeparator = entry.mTagSeparator;
        mPackageName = entry.mPackageName;
        mThreadName = entry.mThreadName;
    }

    /**
     * Replace the current entry with the content of the input stream.
     */
    public void read(final InputStream stream) throws IOException {
        ByteStreams.readFully(stream, mSharedArray, 0, 4);
        mPayloadLength = mSharedBuffer.getShort(0);
        int headerLength = mSharedBuffer.getShort(2);
        if (headerLength != 24) {
            // FIXME In logger_entry(_v1) the __pad can be filled with garbage. We don't know if we are targeting v1 or
            //       not. Maybe do an actual ioctl() check in the future.
            headerLength = 20;
        }
        ByteStreams.readFully(stream, mSharedArray, 0, headerLength - 4);
        mPid = mSharedBuffer.getInt(0);
        mTid = mSharedBuffer.getInt(4);
        mSec = mSharedBuffer.getInt(8);
        mNSec = mSharedBuffer.getInt(12);
        ByteStreams.readFully(stream, mSharedArray, 0, mPayloadLength);
        mTagSeparator = Bytes.indexOf(mSharedArray, (byte) 0);
        mTag = Optional.absent();
        mMessage = Optional.absent();
    }

    public int pid() {
        return mPid;
    }

    public int tid() {
        return mTid;
    }

    public Date timestamp() {
        return new Date(mSec * 1000L + mNSec / 1_000_000L);
    }

    public int logLevel() {
        return mSharedArray[0];
    }

    public static char logLevelChar(final int logLevel) {
        switch (logLevel) {
            case Log.ASSERT:
                return 'F';
            case Log.ERROR:
                return 'E';
            case Log.WARN:
                return 'W';
            case Log.INFO:
                return 'I';
            case Log.DEBUG:
                return 'D';
            case Log.VERBOSE:
                return 'V';
            default:
                return '?';
        }
    }

    public char logLevelChar() {
        return logLevelChar(logLevel());
    }

    public String tag() {
        if (!mTag.isPresent()) {
            mTag = Optional.of(new String(mSharedArray, 1, mTagSeparator - 1, Charsets.UTF_8));
        }
        return mTag.get();
    }

    public String message() {
        if (!mMessage.isPresent()) {
            final int messageLength = mPayloadLength - mTagSeparator - 2;
            mMessage = Optional.of(new String(mSharedArray, mTagSeparator + 1, messageLength, Charsets.UTF_8));
        }
        return mMessage.get();
    }

    private boolean payloadStartsWith(final byte[] prefix) {
        final int length = prefix.length;
        if (mPayloadLength < length) {
            return false;
        }
        for (int i = 0; i < length; ++i) {
            if (mSharedArray[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check whether this log indicates the system_server has restarted.
     */
    public boolean isSystemRestart() {
        return (mPayloadLength == SYSTEM_RESTART_PAYLOAD.length && payloadStartsWith(SYSTEM_RESTART_PAYLOAD))
                || (payloadStartsWith(DEBUGGERD_RESTART_PAYLOAD_PREFIX));
    }

    /**
     * Checks whether this log indicates a process has started. If true, returns the information about this new process.
     *
     * @return The pair of the new PID and process name if it is a start-process entry, null otherwise.
     */
    public Pair<Integer, String> checkStartProcessInfo() {
        if (payloadStartsWith(START_PROCESS_PAYLOAD_PREFIX)) {
            final String message = message();
            final Matcher matcher = START_PROCESS_PATTERN.matcher(message);
            if (matcher.find()) {
                final String processName = matcher.group(1);
                final String pidString = matcher.group(2);
                final Integer pid = Integer.decode(pidString);
                return Pair.create(pid, processName);
            }
            final Matcher matcherLollipop = START_PROCESS_PATTERN_LOLLIPOP_MR1.matcher(message);
            if (matcherLollipop.find()) {
                final String pidString = matcherLollipop.group(1);
                final String processName = matcherLollipop.group(2);
                final Integer pid = Integer.decode(pidString);
                return Pair.create(pid, processName);
            }
        }
        return null;
    }

    /**
     * Checks whether this log indicates a process has ended. If true, returns the pid of the dying process.
     *
     * @return The PID of the process being killed if it is an end-process entry, -1 otherwise.
     */
    public int checkEndProcessInfo() {
        final Pattern pattern;
        if (payloadStartsWith(FORCE_STOP_PROCESS_PAYLOAD_PREFIX)) {
            pattern = FORCE_STOP_PROCESS_PATTERN;
        } else if (payloadStartsWith(KILL_PROCESS_PAYLOAD_PREFIX)) {
            pattern = KILL_PROCESS_PATTERN;
        } else {
            return -1;
        }

        final Matcher matcher = pattern.matcher(message());
        if (matcher.find()) {
            final String pidString = matcher.group(1);
            return Integer.parseInt(pidString);
        } else {
            return -1;
        }
    }

    public boolean isJniCrash() {
        if (logLevel() != Log.ASSERT || !"libc".equals(tag())) {
            return false;
        }

        final Matcher matcher = JNI_SIGNAL_PATTERN.matcher(message());
        return matcher.find();
    }

    public boolean isAnr() {
        return payloadStartsWith(ANR_PAYLOAD_PREFIX_DALVIKVM) ||
                payloadStartsWith(ANR_PAYLOAD_PREFIX_ZYGOTE) ||
                payloadStartsWith(ANR_PAYLOAD_PREFIX_ART);
    }

    public boolean isJniCrashLogEnded() {
        return payloadStartsWith(TOMBSTONE_PAYLOAD_PREFIX) || payloadStartsWith(CODE_AROUND_PC_PREFIX);
    }

    public void writeJSON(final Writer writer) throws IOException {
        final JsonWriter json = new JsonWriter(writer);
        json.beginObject();
        json.name("pid").value(mPid);
        json.name("tid").value(mTid);
        json.name("time").value(mSec * 1000L + mNSec / 1_000_000L);
        json.name("level").value(String.valueOf(logLevelChar()));
        json.name("tag").value(tag());
        json.name("msg").value(message());
        json.endObject();
    }

    public void populateProcessName(final PidDatabase database) {
        mPackageName = database.getProcessName(pid());
    }

    public String getProcessName() {
        return mPackageName;
    }

    public String getThreadName() {
        return mThreadName;
    }
}

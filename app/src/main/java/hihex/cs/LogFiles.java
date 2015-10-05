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

import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Iterator;

public final class LogFiles {
    /**
     * The folder to store all the logs.
     */
    private final File mLogFolder;

    public LogFiles(final Context context) {
        mLogFolder = new File(context.getFilesDir(), "logs");
        mLogFolder.mkdir();
    }

    public TreeMultimap<Long, File> list() {
        final File[] files = mLogFolder.listFiles();
        final TreeMultimap<Long, File> sortedFiles =
                TreeMultimap.create(Ordering.natural().reverse(), Ordering.natural());
        for (final File file : files) {
            sortedFiles.put(file.lastModified(), file);
        }
        return sortedFiles;
    }

    public void removeExpired(final Preferences preferences) {
        long purgeDuration = preferences.getPurgeDuration();
        long purgeFilesize = preferences.getPurgeFilesize();

        final TreeMultimap<Long, File> files = list();

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

    public File getNewPath(final String name, final String extension) {
        for (int i = 0;; ++ i) {
            final String suffix = (i != 0) ? (" (" + i + ')' + extension) : extension;
            final File path = new File(mLogFolder, name + suffix);
            if (!path.exists()) {
                return path;
            }
        }
    }

    public InputStream open(final String fileName) throws FileNotFoundException {
        final File path = new File(mLogFolder, fileName);
        return new FileInputStream(path);
    }

    public void delete(final String fileName) {
        final File path = new File(mLogFolder, fileName);
        path.delete();
    }
}

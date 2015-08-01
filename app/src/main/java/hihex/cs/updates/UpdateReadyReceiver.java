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

package hihex.cs.updates;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import com.google.common.base.Optional;

import java.io.File;

public class UpdateReadyReceiver extends BroadcastReceiver {
    public static Optional<Long> sLastDownloadId = Optional.absent();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (!sLastDownloadId.isPresent()) {
            return;
        }
        final long expectedDownloadId = sLastDownloadId.get();
        final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, expectedDownloadId - 1);

        if (downloadId != expectedDownloadId) {
            return;
        }

        sLastDownloadId = Optional.absent();

        final DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        final Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            return;
        }

        final int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
        if (cursor.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
            return;
        }

        final int localFilenameIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME);
        final String localFilename = cursor.getString(localFilenameIndex);

        final Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(Uri.fromFile(new File(localFilename)), "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(installIntent);
    }
}

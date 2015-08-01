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

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
import android.util.JsonReader;
import android.view.View;
import android.widget.TextView;

import com.github.villadora.semver.SemVer;
import com.google.common.base.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;

import hihex.cs.BuildConfig;
import hihex.cs.CsLog;
import hihex.cs.R;

/**
 * Obtains the latest version from GitHub, and compare with the current version.
 */
public final class UpdateChecker {
    private static final URL API_URL;
    private static final DateFormat STANDARD_DATE_FORMAT = DateFormat.getDateInstance(DateFormat.MEDIUM);

    static {
        try {
            API_URL = new URL("https://api.github.com/repos/kennytm/CatSaver/releases/latest");
        } catch (final MalformedURLException e) {
            // Impossible.
            throw new RuntimeException(e);
        }
    }

    private Optional<ReleaseInfo> mCachedReleaseInfo = Optional.absent();

    public void showCheckDialog(final Context context) {
        final Task task = new Task(context);
        task.execute((Void[]) null);
    }

    private final class Task extends AsyncTask<Void, Void, ReleaseInfo> {
        private Optional<ProgressDialog> mProgressDialog = Optional.absent();
        private final Context mContext;

        public Task(final Context context) {
            mContext = context;
            final String message = context.getString(R.string.contacting_update_server);
            final ProgressDialog dialog = ProgressDialog.show(context, null, message, true, true);
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(final DialogInterface dialog) {
                    mProgressDialog = Optional.absent();
                    cancel(true);
                }
            });
            mProgressDialog = Optional.of(dialog);
        }

        @Override
        protected ReleaseInfo doInBackground(final Void... params) {
            HttpURLConnection connection = null;
            ReleaseInfo info = mCachedReleaseInfo.orNull();

            try {
                connection = (HttpURLConnection) API_URL.openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (info != null) {
                    final String prevETag = info.eTag;
                    connection.setRequestProperty("If-None-Match", prevETag);
                }

                connection.connect();

                final int code = connection.getResponseCode();
                if (code == 304) {
                    return info;
                }

                info = new ReleaseInfo();
                info.eTag = connection.getHeaderField("ETag");

                final InputStream stream = connection.getInputStream();
                final JsonReader reader = new JsonReader(new InputStreamReader(stream));
                reader.setLenient(true);
                info.initialize(reader);
                return info;

            } catch (final IOException | ParseException e) {
                CsLog.e("Cannot fetch upgrade", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return info;
        }

        @Override
        protected void onPostExecute(final ReleaseInfo info) {
            mCachedReleaseInfo = Optional.of(info);
            if (mProgressDialog.isPresent()) {
                mProgressDialog.get().dismiss();
                mProgressDialog = Optional.absent();
            }

            if (SemVer.gt(info.version, BuildConfig.VERSION_NAME)) {
                // Release is Newer
                showDownloadDialog(mContext, info);
            } else {
                // Release is Older (i.e. we are up to date)
                showUpToDateDialog(mContext);
            }
        }
    }

    private void showUpToDateDialog(final Context context) {
        new AlertDialog.Builder(context)
                .setMessage(R.string.already_up_to_date)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showDownloadDialog(final Context context, final ReleaseInfo info) {
        final String title = context.getString(R.string.new_version_available_format, info.version);

        final View dialogView = View.inflate(context, R.layout.new_release_dialog, null);

        final TextView releaseDateView = (TextView) dialogView.findViewById(R.id.release_date);
        releaseDateView.setText(STANDARD_DATE_FORMAT.format(info.date));

        final TextView releaseSizeView = (TextView) dialogView.findViewById(R.id.release_size);
        releaseSizeView.setText(Formatter.formatFileSize(context, info.size));

        final TextView releaseInfoView = (TextView) dialogView.findViewById(R.id.release_info);
        releaseInfoView.setMovementMethod(new ScrollingMovementMethod());
        releaseInfoView.setText(info.info);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        doDownload(context, info.url, info.version);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doDownload(final Context context, final String url, final String version) {
        final DownloadManager downloader = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("CatSaver " + version);
        final long downloadId = downloader.enqueue(request);
        UpdateReadyReceiver.sLastDownloadId = Optional.of(downloadId);
    }
}

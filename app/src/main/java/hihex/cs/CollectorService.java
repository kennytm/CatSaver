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

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import java.io.IOException;

public final class CollectorService extends Service {
    private Thread mLogCollectorThread;
    private WebServer mWebServer;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Put the service in "foreground". This makes the system harder to kill it (?).
        final CharSequence title = getResources().getString(R.string.app_name);
        final CharSequence detail = getResources().getString(R.string.running_hint);
        final Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(title)
                .setContentText(detail)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
        startForeground(1, notification);

        // Prepare the configuration.
        final Config config = new Config(this);
        config.refreshPids();

        // Start the web server
        mWebServer = new WebServer(config);
        try {
            mWebServer.start();
        } catch (final IOException e) {
            CsLog.e("Cannot start web-server. " + e);
        }

        // Start collecting logs
        final LogRecorder runnable = new LogRecorder(config);
        mLogCollectorThread = new Thread(runnable, "Log collector");
        mLogCollectorThread.start();

        Toast.makeText(this, R.string.running_hint, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, R.string.stopping_hint, Toast.LENGTH_LONG).show();
        mLogCollectorThread = null;
        if (mWebServer != null) {
            mWebServer.stop();
            mWebServer = null;
        }
        super.onDestroy();
    }
}

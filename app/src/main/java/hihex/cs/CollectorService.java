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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.widget.Toast;

import java.io.IOException;

public final class CollectorService extends Service {
    private Thread mLogCollectorThread;
    private WebServer mWebServer;
    private RecIndicator mRecIndicator;
    private BroadcastReceiver mIpChangeReceiver;
    private Config mConfig;

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

        final Notification.Builder notificationBuilder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(title)
                .setContentText(detail);

        final Notification notification;
        if (Build.VERSION.SDK_INT >= 16) {
            notification = notificationBuilder.setPriority(Notification.PRIORITY_LOW).build();
        } else {
            notification = notificationBuilder.getNotification();
        }
        startForeground(1, notification);

        // Prepare the configuration.
        final Config config = new Config(this);
        config.refreshPids();
        mConfig = config;

        // Start the web server
        final Drawable icon = getResources().getDrawableForDensity(R.mipmap.ic_launcher, DisplayMetrics.DENSITY_LOW);
        mWebServer = new WebServer(config, icon);
        try {
            mWebServer.start();
        } catch (final IOException e) {
            CsLog.e("Cannot start web-server. " + e);
        }

        // Show the recording indicator
        mRecIndicator = new RecIndicator(config);
        monitorIpAddress();
        mConfig.eventBus.post(new Events.UpdateIpAddress());

        // Start collecting logs for existing processes.
        config.startRecordingExistingProcesses();

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
        mRecIndicator = null;
        if (mWebServer != null) {
            mWebServer.stop();
            mWebServer = null;
        }
        if (mIpChangeReceiver != null) {
            unregisterReceiver(mIpChangeReceiver);
            mIpChangeReceiver = null;
        }
        super.onDestroy();
    }

    private void monitorIpAddress() {
        mIpChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mConfig.eventBus.post(new Events.UpdateIpAddress());
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mIpChangeReceiver, filter);
    }
}

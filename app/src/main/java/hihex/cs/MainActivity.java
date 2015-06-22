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

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.os.Process;
import android.view.View;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int checkResult = checkPermission(Manifest.permission.READ_LOGS, Process.myPid(), Process.myUid());
        if (checkResult == PackageManager.PERMISSION_GRANTED) {
            final Intent serviceIntent = new Intent(this, CollectorService.class);
            startService(serviceIntent);

            setContentView(R.layout.server);
            final TextView serverView = (TextView) findViewById(R.id.server_address);
            serverView.setText("http://" + IpAddresses.getBestIpAddress() + ':' + WebServer.PORT + '/');
        } else {
            setContentView(R.layout.need_perm);
        }
    }

    public void tryGrantPermission(final View view) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                GrantPermission.tryGrantPermission();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        recreate();
                    }
                });
            }
        }).start();
    }
}

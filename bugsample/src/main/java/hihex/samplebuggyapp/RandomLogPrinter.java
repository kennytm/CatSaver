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

package hihex.samplebuggyapp;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.util.Random;

class RandomLogPrinter extends Handler {
    private final Random mRandom;

    public RandomLogPrinter() {
        mRandom = new Random(SystemClock.uptimeMillis());
    }

    @Override
    public void handleMessage(final Message msg) {
        switch (mRandom.nextInt(6)) {
            case 0:
                Log.v("sample", "verbose log");
                break;
            case 1:
                Log.d("sample", "debug log");
                break;
            case 2:
                Log.i("sample", "info log");
                break;
            case 3:
                Log.w("sample", "warn log");
                break;
            case 4:
                Log.e("sample", "error log");
                break;
            case 5:
                Log.wtf("sample", "fatal log");
                break;
        }
        sendEmptyMessageDelayed(1, 1000L);
    }
}

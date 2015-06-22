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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public final class MainActivity extends Activity {
    static {
        System.loadLibrary("bugsample");
    }

    private final RandomLogPrinter mRandomLogPrinter = new RandomLogPrinter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.i("sample", "We have started! <>&'\"\0\ud83d\udca9あ");
        mRandomLogPrinter.sendEmptyMessage(1);
    }

    public void exitBySystemExit0(final View view) {
        System.exit(0);
    }

    public void exitBySystemExit1(final View view) {
        System.exit(1);
    }

    public void exitByRuntimeException(View view) {
        throw new RuntimeException("test crash");
    }

    public void exitByNestedException(View view) {
        throw new RuntimeException("c", new RuntimeException("b", new RuntimeException("a")));
    }

    public void exitByInfiniteRecursion(View view) {
        exitByInfiniteRecursion(null);
    }

    public void exitByANR(View view) {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void exitByExceptionFromJavaThread(View view) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("d");
            }
        });
        thread.start();
    }

    public native void exitByJniSegFault(View view);

    public native void exitByExceptionFromJniThread(View view);

    public void throwNPE() {
        final Object o = null;
        o.getClass();
    }
}

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

package hihex.cs;

import android.util.Log;

/**
 * Log for CatSaver
 */
public final class CsLog {
    public static final String TAG = "CatSaver";

    private CsLog() {}

    public static void e(final String message) {
        Log.e(TAG, message);
    }

    public static void e(final String message, final Throwable e) {
        Log.e(TAG, message, e);
    }
}

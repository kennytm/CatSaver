/*
 * CatSaver Copyright (C) 2015 HiHex Ltd.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package hihex.cs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.common.eventbus.Subscribe;

public final class RecIndicator {
    private final TextView mTextView;
    private final TextView mIpView;
    private final View mRootView;
    private final View mBackgroundView;
    private final Handler mMainThreadHandler;

    @SuppressLint("InflateParams")
    public RecIndicator(final Config config) {
        final Context context = config.context.getApplicationContext();
        mRootView = LayoutInflater.from(context).inflate(R.layout.rec_indicator, null);
        mTextView = (TextView) mRootView.findViewById(R.id.rec_count);
        mIpView = (TextView) mRootView.findViewById(R.id.rec_ip);
        mBackgroundView = mRootView.findViewById(R.id.rec_bg);
        mTextView.setText("0");
        createFloatingWindow(context);
        mMainThreadHandler = new Handler();
        toggleVisibility(new Events.PreferencesUpdated(config.preferences));
        Events.bus.register(this);
    }

    public void createFloatingWindow(final Context context) {
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_TOAST,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        final Resources res = context.getResources();

        params.setTitle("CatSaver indicator");
        params.gravity = Gravity.START | Gravity.TOP;
        params.x = res.getDimensionPixelOffset(R.dimen.rec_indicator_offset_x);
        params.y = res.getDimensionPixelOffset(R.dimen.rec_indicator_offset_y);

        final WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        manager.addView(mRootView, params);
    }

    @Subscribe
    public void updateRecordCount(final Events.RecordCount countValue) {
        final int count = countValue.count;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mTextView.setText(String.valueOf(count));
                mBackgroundView.setBackgroundResource(count == 0 ? R.color.rec_off : R.color.rec_on);
            }
        });
    }

    @Subscribe
    public void toggleVisibility(final Events.PreferencesUpdated preferences) {
        final int visibility = preferences.preferences.shouldShowIndicator() ? View.VISIBLE : View.GONE;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mRootView.setVisibility(visibility);
            }
        });
    }

    @Subscribe
    public void updateIpAddress(final Events.UpdateIpAddress ipValue) {
        final String bestIpAddress = ipValue.ipAddress;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mIpView.setText(bestIpAddress);
            }
        });
    }
}

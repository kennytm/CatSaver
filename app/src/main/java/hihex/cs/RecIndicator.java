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
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.common.eventbus.Subscribe;

public final class RecIndicator {
    private final TextView mTextView;
    private final View mRootView;
    private final Handler mMainThreadHandler;

    @SuppressLint("InflateParams")
    public RecIndicator(final Config config) {
        final Context context = config.context.getApplicationContext();
        mRootView = LayoutInflater.from(context).inflate(R.layout.rec_indicator, null);
        mTextView = (TextView) mRootView.findViewById(R.id.rec_count);
        mTextView.setText("0");
        createFloatingWindow(context);
        mMainThreadHandler = new Handler();
        config.eventBus.register(this);
        toggleVisibility(new Events.RecordIndicatorVisibility(config.shouldShowIndicator()));
    }

    public void createFloatingWindow(final Context context) {
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_TOAST,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.setTitle("CatSaver indicator");

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
                mTextView.setBackgroundResource(count == 0 ? R.color.rec_off : R.color.rec_on);
            }
        });
    }

    @Subscribe
    public void toggleVisibility(final Events.RecordIndicatorVisibility visibleValue) {
        final int visibility = visibleValue.isVisible ? View.VISIBLE : View.GONE;
        mMainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mRootView.setVisibility(visibility);
            }
        });
    }
}

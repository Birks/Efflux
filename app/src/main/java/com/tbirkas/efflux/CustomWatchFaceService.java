package com.tbirkas.efflux;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class CustomWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            // Handling the message from recursive call
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {

                    // If the view is visible, onDraw() will be called at some point in the future.
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);

                        // Calling itself with delay (tick-tack)
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };


        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;

        // Feel free to change these values and see what happens to the watch face.
        private static final float STROKE_WIDTH = 2f;
        private static final int SHADOW_RADIUS = 6;

        private Time mTime;

        private Paint mBackgroundPaint;
        private Paint mGrowingCirclePaint;
        private Paint mGrayGrowingCirclePaint;
        private Paint mQuarterPaint;
        private Paint mHalfPaint;
        private Paint mGrayBackgroundPaint;

        private boolean mAmbient;

        private Bitmap mBackgroundBitmap;
        private Bitmap hourBitmap;
        private Bitmap hourBackground;
        private Bitmap grayHourBitmap;

        private int mWidth;
        private int mHeight;

        // Coordinates of the origin
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        // Custom color codes
        private static final String GOLD = "#FFC90E";
        private static final String GRAY = "#C7C7C7";

        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(CustomWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.NO_GRAVITY)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                    .build());


            // Setting the background colour
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundPaint.setAntiAlias(true);
            mBackgroundPaint.setFilterBitmap(true);


            // Gray background
            mGrayBackgroundPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            mGrayBackgroundPaint.setColorFilter(filter);


            // Decoding the png images to bitmap object
            hourBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.filledhour);
            hourBackground = BitmapFactory.decodeResource(getResources(), R.drawable.blackhour);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.custom_background);
            grayHourBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.filledhour_gray);

            // Properties of the growing filled circle
            mGrowingCirclePaint = new Paint();
            mGrowingCirclePaint.setColor(Color.parseColor(GOLD)); // Gold
            mGrowingCirclePaint.setStrokeWidth(0);
            mGrowingCirclePaint.setAntiAlias(true);
            mGrowingCirclePaint.setStrokeCap(Paint.Cap.ROUND);
            mGrowingCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            // Ambient background circle
            mGrayGrowingCirclePaint = new Paint();
            mGrayGrowingCirclePaint.setColor(Color.parseColor(GRAY)); // Gold
            mGrayGrowingCirclePaint.setStrokeWidth(0);
            mGrayGrowingCirclePaint.setAntiAlias(false);
            mGrayGrowingCirclePaint.setStrokeCap(Paint.Cap.ROUND);
            mGrayGrowingCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);

            // Properties of the dashed 15 and 45 min circles
            mQuarterPaint = new Paint();
            mQuarterPaint.setColor(Color.WHITE);
            mQuarterPaint.setStrokeWidth(STROKE_WIDTH);
            mQuarterPaint.setAntiAlias(true);
            mQuarterPaint.setStrokeCap(Paint.Cap.ROUND);
            mQuarterPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.BLACK);
            mQuarterPaint.setStyle(Paint.Style.STROKE);
            mQuarterPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 4));

            // Properties of the 30 min circle
            mHalfPaint = new Paint();
            mHalfPaint.setColor(Color.WHITE);
            mHalfPaint.setStrokeWidth(STROKE_WIDTH);
            mHalfPaint.setAntiAlias(true);
            mHalfPaint.setStrokeCap(Paint.Cap.ROUND);
            mHalfPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.BLACK);
            mHalfPaint.setStyle(Paint.Style.STROKE);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
        }

        // When a minute passes
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();

        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();


            // Scaling the images
            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);

            hourBitmap = Bitmap.createScaledBitmap(hourBitmap,
                    (int) (hourBitmap.getWidth() * mScale),
                    (int) (hourBitmap.getHeight() * mScale), true);

            grayHourBitmap = Bitmap.createScaledBitmap(grayHourBitmap,
                    (int) (grayHourBitmap.getWidth() * mScale),
                    (int) (grayHourBitmap.getHeight() * mScale), true);

            hourBackground = Bitmap.createScaledBitmap(hourBackground,
                    (int) (hourBackground.getWidth() * mScale),
                    (int) (hourBackground.getHeight() * mScale), true);

        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);


            // mWidth / 2 = Radius, the rest is converting the seconds in a 0-1 interval
            if (mAmbient || (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawCircle(mCenterX, mCenterY, (float) (((mWidth) / 2) * ((((mTime.minute * (60)) + mTime.second) * 0.027777778) / 100)), mGrayGrowingCirclePaint);
            } else {
                canvas.drawCircle(mCenterX, mCenterY, (float) (((mWidth) / 2) * ((((mTime.minute * (60)) + mTime.second) * 0.027777778) / 100)), mGrowingCirclePaint);
            }


            // Drawing the background bitmaps
            // Divide the current hour from 12
            // if statement is for the 12 hour bug
            for (int i = 1; i <= (12 - (mTime.hour % 12)); ++i) {
                canvas.save();
                int shift = i;

                if (i == 12) {
                    shift = 11;
                    canvas.rotate(((30) * (shift + 6)) + 21 + ((mTime.hour + 1) * 30), mCenterX, mCenterY); //

                } else {
                    canvas.rotate(((30) * (shift + 6)) + 7 + ((mTime.hour + 1) * 30), mCenterX, mCenterY); //

                }

                canvas.drawBitmap(hourBackground, mCenterX, mCenterY, mBackgroundPaint);
                canvas.restore();

            }


            // Drawing the bitmaps of the hours
            for (int i = 1; i <= (mTime.hour % 12); ++i) {
                canvas.save();
                // A hour is 30 degree, i+6 is because it starts at 6, 23 is the shift because it doesn't start at the middle
                canvas.rotate(((30) * (i + 6)) + 23, mCenterX, mCenterY);

                // Ambient mode real deal
                if (mAmbient || (mLowBitAmbient || mBurnInProtection)) {
                    canvas.drawBitmap(grayHourBitmap, mCenterX, mCenterY, mGrayBackgroundPaint);
                } else {
                    canvas.drawBitmap(hourBitmap, mCenterX, mCenterY, mBackgroundPaint);
                }

                canvas.restore();

            }

            // Draw the dashed circles
            canvas.save();
            canvas.rotate(-3, mCenterX, mCenterY);
            canvas.drawCircle(mCenterX, mCenterY, ((mWidth) / 4), mHalfPaint);
            canvas.drawCircle(mCenterX, mCenterY, ((mWidth) / 8), mQuarterPaint);
            canvas.rotate(8, mCenterX, mCenterY);
            canvas.drawCircle(mCenterX, mCenterY, ((mWidth) / 8 + mWidth / 4), mQuarterPaint);
            canvas.restore();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            /*
             * Whether the timer should be running depends on whether we're visible
             * (as well as whether we're in ambient mode),
             * so we may need to start or stop the timer.
             */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            CustomWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }


        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            CustomWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

    }
}

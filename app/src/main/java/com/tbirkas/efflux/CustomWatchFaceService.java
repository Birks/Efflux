package com.tbirkas.efflux;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.util.Calendar;
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
//                mTime.clear(intent.getStringExtra("time-zone"));
//                mTime.setToNow();
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;

        // Feel free to change these values and see what happens to the watch face.
        private static final float STROKE_WIDTH = 2f;
        private static final int SHADOW_RADIUS = 6;

        //private Time mTime;

        private Paint newTimePaint;
        private Paint newTimeAmbientPaint;
        private Paint newTimeBgrPaint;
        private Paint mBackgroundPaint;
        private Paint mGrowingCirclePaint;
        private Paint mGrayGrowingCirclePaint;
        private Paint mQuarterPaint;
        private Paint mHalfPaint;

        private boolean mAmbient;

        private Bitmap mBackgroundBitmap;

        private Point a;
        private Point b;
        private Point c;
        private Path path;

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

        private Calendar mCalendar;


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


            // New red time
            newTimePaint = new Paint();
            newTimePaint.setStrokeWidth(0);
            newTimePaint.setColor(Color.parseColor(GOLD));
            newTimePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            newTimePaint.setAntiAlias(true);

            // New gray time
            newTimeAmbientPaint = new Paint();
            newTimeAmbientPaint.setStrokeWidth(0);
            newTimeAmbientPaint.setColor(Color.parseColor(GRAY));
            newTimeAmbientPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            newTimeAmbientPaint.setAntiAlias(false);


            // New BLACK MIDs
            newTimeBgrPaint = new Paint();
            newTimeBgrPaint.setStrokeWidth(0);
            newTimeBgrPaint.setColor(Color.BLACK);
            newTimeBgrPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            newTimeBgrPaint.setAntiAlias(true);


            // Decoding the png images to bitmap object
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.custom_background);

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

            //mTime = new Time();
            mCalendar = Calendar.getInstance();

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

            // setting the Path

            a = new Point((int) mCenterX, (int) mCenterY);
            b = new Point((mWidth / 2) - 27, mHeight);
            c = new Point((mWidth / 2) + 27, mHeight);

            path = new Path();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.moveTo((int) mCenterX, (int) mCenterY);
            path.lineTo(a.x, a.y);
            path.lineTo(b.x, b.y);
            path.lineTo(c.x, c.y);
            path.close();


            // Scaling the images
            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);

        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //mTime.setToNow();
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            // Draw the background.
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);


            // Milliseconds drawing,  mWidth / 2 = Radius, the rest is converting the seconds in a 0-1 interval
            if (mAmbient || (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawCircle(mCenterX, mCenterY, (float) (((mWidth) / 2) * ((((mCalendar.get(Calendar.MINUTE) * (60)) + mCalendar.get(Calendar.SECOND)) * 0.027777778) / 100)), mGrayGrowingCirclePaint);
            } else {
                canvas.drawCircle(mCenterX, mCenterY, (float) (((mWidth) / 2) * ((((mCalendar.get(Calendar.MINUTE) * (60)) + mCalendar.get(Calendar.SECOND)) * 0.027777778) / 100)), mGrowingCirclePaint);
            }


            // Drawing the hour lines
            for (int i = 1; i <= (mCalendar.get(Calendar.HOUR) % 12); ++i) {
                canvas.save();
                // A hour is 30 degree, i+6 is because it starts at 6, 23 is the shift because it doesn't start at the middle
                canvas.rotate(((30) * (i + 6)), mCenterX, mCenterY);

                // Ambient mode real deal
                if (mAmbient || (mLowBitAmbient || mBurnInProtection)) {
                    canvas.drawPath(path, newTimeAmbientPaint);
                } else {
                    canvas.drawPath(path, newTimePaint);
                }

                canvas.restore();

            }

            // Drawing black lines between hours
            for (int i = 1; i <= 12; ++i) { // Was hour%12 originally
                canvas.save();
                // A hour is 30 degree, i+6 is because it starts at 6, 23 is the shift because it doesn't start at the middle
                canvas.rotate(((30) * (i + 6)) + 15, mCenterX, mCenterY);

                // Ambient mode real deal
                if (mAmbient || (mLowBitAmbient || mBurnInProtection)) {
                    canvas.drawPath(path, newTimeBgrPaint);
                } else {
                    canvas.drawPath(path, newTimeBgrPaint);
                }

                canvas.restore();

            }

            // Drawing the leftover black part after the current hour
            for (int i = 1; i <= ((11 - (mCalendar.get(Calendar.HOUR) % 12))); ++i) {
                canvas.save();

                canvas.rotate(((30) * ((mCalendar.get(Calendar.HOUR) % 12) + (i + 7))), mCenterX, mCenterY);

                canvas.drawPath(path, newTimeBgrPaint);
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
                mCalendar.setTimeZone(TimeZone.getDefault());
                //mTime.clear(TimeZone.getDefault().getID());
                //mTime.setToNow();
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

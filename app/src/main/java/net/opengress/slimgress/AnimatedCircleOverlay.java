package net.opengress.slimgress;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

public class AnimatedCircleOverlay extends Overlay {
    private final Paint mPaint = new Paint();
    private final float mMaxRadius;
    private final long mVelocity;
    private final long mInterval;
    private GeoPoint mCenter;
    private float mRadius = 0;
    private Handler mHandler = new Handler();
    private MapView mMap;
    private Runnable mRunnable = createRunnable();
    private boolean mRunOnce = true;
    private float mStrokeWidth = 10;
    private long mLastTime;

    public AnimatedCircleOverlay(MapView mapView, float maxRadius, long velocity) {
        this(mapView, maxRadius, velocity, 0);
    }

    public AnimatedCircleOverlay(MapView mapView, float maxRadius, long velocity, long interval) {
        mMap = mapView;
        mCenter = (GeoPoint) mapView.getMapCenter();
        mMaxRadius = maxRadius;
        mVelocity = velocity;
        mInterval = interval;
        mPaint.setColor(0xCCCC9900);
        mPaint.setStyle(Paint.Style.STROKE);
        mapView.getOverlays().add(this);
    }

    private Runnable createRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                float deltaTime = (currentTime - mLastTime) / 1000f;
                mLastTime = currentTime;

                mRadius += mVelocity * deltaTime;
                if (mRadius >= mMaxRadius) {
                    if (mInterval > 0) {
                        mRadius = 0;
                        mHandler.postDelayed(this, mInterval);
                    } else if (mRunOnce) {
                        stop();
                    } else {
                        mRadius = 0;
                        mHandler.removeCallbacks(mRunnable);
                    }
                } else {
                    mHandler.postDelayed(this, 16); // Redraw every 16ms (~60fps)
                }
                if (mMap != null) {
                    mMap.invalidate();
                }
            }
        };
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) {
            return;
        }

        Point screenPoint = new Point();
        mapView.getProjection().toPixels(mCenter, screenPoint);
        float pixels = mMap.getProjection().metersToPixels(mRadius, mCenter.getLatitude(), mapView.getZoomLevelDouble());
        mPaint.setStrokeWidth(mMap.getProjection().metersToPixels(mStrokeWidth, mCenter.getLatitude(), mapView.getZoomLevelDouble()));
        canvas.drawCircle(screenPoint.x, screenPoint.y, pixels, mPaint);
    }

    public void updateLocation(GeoPoint location) {
        mCenter = location;
    }

    public void start() {
        mLastTime = System.currentTimeMillis();
        mHandler.post(mRunnable);
    }

    public void stop() {
        mHandler.removeCallbacks(mRunnable);
        mMap.getOverlays().remove(this);
        mMap = null;
        mHandler = null;
        mRunnable = null;
    }

    public void setRunOnce(boolean runOnce) {
        mRunOnce = runOnce;
    }

    public void setColor(int color) {
        // color 0x33FFFF00
        mPaint.setColor(color);
    }

    public void setWidth(float width) {
        // width 10
        mStrokeWidth = width;
    }

    public void setBlendMode(BlendMode bm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mInterval > 0) {
            mPaint.setBlendMode(bm);
        }
    }

    public void trigger() {
        start();
    }


}

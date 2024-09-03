package net.opengress.slimgress;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.view.Choreographer;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

public class AnimatedCircleOverlay extends Overlay {
    private final Paint mPaint = new Paint();
    private final float mMaxRadius;
    private final long mVelocity;
    private final long mInterval;
    private final Point mScreenPoint = new Point();
    private GeoPoint mCenter;
    private float mRadius = 0;
    private Choreographer mChoreographer;
    private Choreographer.FrameCallback mFrameCallback;
    private boolean mRunOnce = true;
    private float mStrokeWidth = 10;
    private long mLastTime;
    private MapView mMap;

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
        mChoreographer = Choreographer.getInstance();
        mFrameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                long currentTime = System.currentTimeMillis();
                float deltaTime = (currentTime - mLastTime) / 1000f;
                mLastTime = currentTime;

                mRadius += mVelocity * deltaTime;
                if (mRadius >= mMaxRadius) {
                    if (mInterval > 0) {
                        mRadius = 0;
                        mChoreographer.postFrameCallbackDelayed(this, mInterval);
                    } else if (mRunOnce) {
                        stop();
                    } else {
                        mRadius = 0;
                    }
                } else {
                    mChoreographer.postFrameCallback(this);
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

        mapView.getProjection().toPixels(mCenter, mScreenPoint);
        float pixels = mMap.getProjection().metersToPixels(mRadius, mCenter.getLatitude(), mapView.getZoomLevelDouble());
        mPaint.setStrokeWidth(mMap.getProjection().metersToPixels(mStrokeWidth, mCenter.getLatitude(), mapView.getZoomLevelDouble()));
        canvas.drawCircle(mScreenPoint.x, mScreenPoint.y, pixels, mPaint);
    }

    public void updateLocation(GeoPoint location) {
        mCenter = location;
    }

    public void start() {
        mLastTime = System.currentTimeMillis();
        mChoreographer.postFrameCallback(mFrameCallback);
    }

    public void stop() {
        mChoreographer.removeFrameCallback(mFrameCallback);
        mMap.getOverlays().remove(this);
        mMap = null;
        mChoreographer = null;
        mFrameCallback = null;
    }

    public void setRunOnce(boolean runOnce) {
        mRunOnce = runOnce;
    }

    public void setColor(int color) {
        mPaint.setColor(color);
    }

    public void setWidth(float width) {
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

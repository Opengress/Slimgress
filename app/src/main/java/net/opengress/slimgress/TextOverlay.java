package net.opengress.slimgress;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

public class TextOverlay extends Overlay {
    private final GeoPoint position;
    private final String text;
    private final Paint paint;
    private MapView map;

    public TextOverlay(MapView mv, GeoPoint position, String text, int colour) {
        map = mv;
        this.position = position;
        this.text = text;
        this.paint = new Paint();
        paint.setColor(colour);
        paint.setTextSize(30);
        paint.setAntiAlias(true);
        map.getOverlays().add(this);
//        map.invalidate();
        Looper looper = Looper.myLooper();
        if (looper == null) {
            looper = Looper.getMainLooper();
        }
        new Handler(looper).postDelayed(() -> {
            map.getOverlays().remove(this);
//            map.invalidate();
            map = null;
        }, 2000); // 2 seconds
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (!shadow) {
            // if I don't rotate the canvas, obviously the text always is oriented for north reading
            //    and that is hilarious but undersirable
            Point screenPoint = new Point();
            mapView.getProjection().toPixels(position, screenPoint);
            canvas.save();
            canvas.rotate(-mapView.getMapOrientation(), screenPoint.x, screenPoint.y);
            canvas.drawText(text, screenPoint.x, screenPoint.y, paint);
            canvas.restore();
        }
    }

    @Keep
    public void setLatitude(float latitude) {
        this.position.setLatitude(latitude);
    }
}
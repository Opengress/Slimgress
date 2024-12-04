package net.opengress.slimgress.positioning;

import org.maplibre.android.geometry.LatLng;

import java.util.Stack;

public class LatLngPool {
    private final Stack<LatLng> pool = new Stack<>();

    public LatLng obtain() {
        if (!pool.isEmpty()) {
            return pool.pop();
        } else {
            return new LatLng(0, 0);
        }
    }

    // Return a LatLng to the pool
    public void recycle(LatLng latLng) {
        pool.push(latLng);
    }
}

package net.opengress.slimgress;

import org.maplibre.android.geometry.LatLng;

import java.util.Stack;

public class LatLngPool {
    private final Stack<LatLng> pool = new Stack<>();

    // Obtain a LatLng from the pool or create a new one
    public LatLng obtain() {
        if (!pool.isEmpty()) {
            return pool.pop();
        } else {
            // Assuming LatLng has a default constructor or one that accepts zeros
            return new LatLng(0, 0);
        }
    }

    // Return a LatLng to the pool
    public void recycle(LatLng latLng) {
        pool.push(latLng);
    }
}

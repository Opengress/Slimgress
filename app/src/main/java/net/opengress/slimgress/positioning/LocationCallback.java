package net.opengress.slimgress.positioning;

import android.location.Location;

public interface LocationCallback {
    void onLocationUpdated(Location location);

    void onUpdatesStarted();

    void onUpdatesStopped();
}

package net.opengress.slimgress.positioning;

public interface LocationProvider {
    boolean startLocationUpdates();

    void stopLocationUpdates();

    void setLocationCallback(LocationCallback callback);
}

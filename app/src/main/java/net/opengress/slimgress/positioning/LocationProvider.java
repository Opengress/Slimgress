package net.opengress.slimgress.positioning;

public interface LocationProvider {
    void startLocationUpdates();

    void stopLocationUpdates();

    void setLocationCallback(LocationCallback callback);
}

package net.opengress.slimgress.positioning;

public interface LocationProvider {

    boolean startLocationUpdates();

    void stopLocationUpdates();

    void addLocationCallback(LocationCallback callback);

    void removeLocationCallback(LocationCallback callback);

    <T> T getCurrentLocation();
}

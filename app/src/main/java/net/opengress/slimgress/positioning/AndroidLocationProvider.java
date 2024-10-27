package net.opengress.slimgress.positioning;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class AndroidLocationProvider implements LocationProvider, LocationListener {
    private final LocationManager locationManager;
    private LocationCallback callback;
    List<String> providers = new ArrayList<>();

    public AndroidLocationProvider(Context context) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean startLocationUpdates() {
        boolean initSuccess = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(LocationManager.FUSED_PROVIDER);
        }
        providers.add(LocationManager.GPS_PROVIDER);
//        providers.add(LocationManager.NETWORK_PROVIDER);

        for (String provider : providers) {

            // Request location updates
            if (locationManager.isProviderEnabled(provider)) {
                initSuccess = true;
            }
            locationManager.requestLocationUpdates(provider, 1, 0f, this);
            Log.d("LocationProvider", "Requested location updates from " + provider);
        }

        return initSuccess;
    }

    @Override
    public void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
            Log.d("LocationProvider", "Stopped location updates.");
        }
    }

    @Override
    public void setLocationCallback(LocationCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (callback != null) {
            callback.onLocationUpdated(location);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.e("LocationProvider", provider + " has status: " + status);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        if (providers.contains(provider)) {
            callback.onUpdatesStarted();
        }
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        if (providers.contains(provider)) {
            callback.onUpdatesStopped();
        }
    }
}


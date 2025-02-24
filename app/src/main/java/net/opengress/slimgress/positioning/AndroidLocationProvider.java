package net.opengress.slimgress.positioning;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class AndroidLocationProvider implements LocationProvider, LocationListener {
    List<String> mProviders = new ArrayList<>();
    private Location mCurrentLocation;
    private long mGpsTimestamp = System.currentTimeMillis();
    private boolean mIsRunning = false;
    private final ArrayList<LocationCallback> mCallbacks = new ArrayList<>();
    private final LocationManager mLocationManager;
    private static AndroidLocationProvider mInstance;

    private AndroidLocationProvider(@NonNull Context context) {
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public static synchronized AndroidLocationProvider getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AndroidLocationProvider(context.getApplicationContext());
        }
        return mInstance;
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean startLocationUpdates() {
        if (mIsRunning) {
            Log.d("LocationProvider", "Skipping redundant location update request");
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mProviders.add(LocationManager.FUSED_PROVIDER);
        }
        mProviders.add(LocationManager.GPS_PROVIDER);
//        providers.add(LocationManager.NETWORK_PROVIDER);

        for (String provider : mProviders) {

            // Request location updates
            if (mLocationManager.isProviderEnabled(provider)) {
                mIsRunning = true;
            }
            mLocationManager.requestLocationUpdates(provider, 1, 0f, this);
            Log.d("LocationProvider", "Requested location updates from " + provider);
        }

        return mIsRunning;
    }

    @Override
    public void stopLocationUpdates() {
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(this);
            Log.d("LocationProvider", "Stopped location updates.");
        }
        mIsRunning = false;
    }

    @Override
    public void addLocationCallback(LocationCallback callback) {
        this.mCallbacks.add(callback);
    }

    @Override
    public void removeLocationCallback(LocationCallback callback) {
        this.mCallbacks.remove(callback);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        boolean gotGPS = false;
        long time = System.currentTimeMillis();
        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            mGpsTimestamp = time;
            gotGPS = true;
        }
        if (gotGPS || time - 4000 > mGpsTimestamp) {
            for (LocationCallback callback : mCallbacks) {
                callback.onLocationUpdated(location);
            }
            mCurrentLocation = location;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // this method is only called for old androids anyway
        // DO NOT CALL SUPER - deprecated from R and abstract until Q
        if (mLocationManager.isProviderEnabled(provider)) {
            onProviderEnabled(provider);
        } else {
            onProviderDisabled(provider);
        }
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        for (LocationCallback callback : mCallbacks) {
            if (mProviders.contains(provider)) {
                callback.onUpdatesStarted();
            }
        }
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        for (LocationCallback callback : mCallbacks) {
            if (mProviders.contains(provider)) {
                callback.onUpdatesStopped();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Location getCurrentLocation() {
        return mCurrentLocation;
    }

    public void checkPermissionsAndRequestUpdates(Activity activity, Runnable onSuccessCallback) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Stupid Google made shouldShowPermissionRationale useless as it returns false on first run
            // TODO: maybe come back and do the job that the google morons pretended to do
            if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    doActualPermissionsRequest(activity);
                } else {
                    // User selected 'Don't Ask Again'
                    sendUserToSystemSettings(activity);
                }
            } else {
                doActualPermissionsRequest(activity);
            }
        } else {
            if (onSuccessCallback != null) {
                onSuccessCallback.run();
            }
        }
    }

    private static void sendUserToSystemSettings(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage("Permission to access the device location is required for this app to function correctly. Please enable it in the app settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                    intent.setData(uri);
                    activity.startActivity(intent);
                })
                .create()
                .show();
    }

    private static void doActualPermissionsRequest(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage("Permission to access the device location is required for this app to function correctly.").setTitle("Permission required");

        builder.setPositiveButton("OK", (dialog, id) -> ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101));

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}


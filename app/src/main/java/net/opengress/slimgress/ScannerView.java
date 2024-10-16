/*

 Slimgress: Opengress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>
 Copyright (C) 2024 Opengress Team <info@opengress.net>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package net.opengress.slimgress;

import static android.content.Context.SENSOR_SERVICE;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE_DEFAULT;
import static net.opengress.slimgress.Constants.PREFS_OSM_LATITUDE_STRING;
import static net.opengress.slimgress.Constants.PREFS_OSM_LONGITUDE_STRING;
import static net.opengress.slimgress.Constants.PREFS_OSM_ZOOM_LEVEL_DOUBLE;
import static net.opengress.slimgress.ViewHelpers.getBitmapFromAsset;
import static net.opengress.slimgress.ViewHelpers.getBitmapFromDrawable;
import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getImageForResoLevel;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
import static net.opengress.slimgress.ViewHelpers.getTintedImage;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;
import static net.opengress.slimgress.api.Common.Utils.notBouncing;
import static net.opengress.slimgress.api.Item.ItemBase.ItemType.PortalKey;
import static net.opengress.slimgress.api.Knobs.MapCompositionRootKnobs.MapProvider.CoordinateSystem.XYZ;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.opengress.slimgress.activity.ActivityMain;
import net.opengress.slimgress.activity.ActivityPortal;
import net.opengress.slimgress.activity.ActivitySplash;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.XMParticle;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityControlField;
import net.opengress.slimgress.api.GameEntity.GameEntityItem;
import net.opengress.slimgress.api.GameEntity.GameEntityLink;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemFlipCard;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.api.Knobs.MapCompositionRootKnobs.MapProvider;
import net.opengress.slimgress.api.Knobs.ScannerKnobs;
import net.opengress.slimgress.api.Knobs.TeamKnobs;
import net.opengress.slimgress.dialog.DialogHackResult;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class ScannerView extends Fragment implements SensorEventListener, LocationListener {
    // ===========================================================
    // Hardcore internal stuff
    // ===========================================================
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();

    // ===========================================================
    // Knobs quick reference
    // ===========================================================
    ScannerKnobs mScannerKnobs = mGame.getKnobs().getScannerKnobs();
    private int mActionRadiusM = mScannerKnobs.getActionRadiusM();
    private int mUpdateIntervalMS = mScannerKnobs.getUpdateIntervalMS();
    private int mMinUpdateIntervalMS = mScannerKnobs.getMinUpdateIntervalMS();
    private int mUpdateDistanceM = mScannerKnobs.getUpdateDistanceM();


    // ===========================================================
    // Map basics
    // ========================================bing===================
    final int TOP_LEFT_ANGLE = 315;
    final int BOTTOM_RIGHT_ANGLE = 135;
    private MapView mMap = null;
    private final HashMap<String, Bitmap> mIcons = new HashMap<>();
    private final HashMap<String, GroundOverlay> mPortalMarkers = new HashMap<>();
    /*
    do it by reso guid:
    - can receive guid in deletedentitiesguids (when?) and delete it directly
    - CAN'T delete it with portal deletion (unless we use another data structure)
    do it by portalguid/slot:
    - can do it from portal damages
    - can delete them all when portal is deleted
    - CAN'T find them by guid (but when do we want to?)
    if we do it the second way, the server can never delete a reso except when you attack it
    if we do it the first way, we need to ensure that the reso always gets deleted on upgrade or other-player-attack
     */
    // for getting rid of the resonators when we get rid of the portals
    private final HashMap<String, HashMap<Integer, Pair<GroundOverlay, Polyline>>> mResonatorMarkers = new HashMap<>();
    // for finding the portal marker when we delete it by Guid
    private final HashMap<String, Pair<String, Integer>> mResonatorToPortalSlotLookup = new HashMap<>();
    private final HashMap<Long, GroundOverlay> mXMMarkers = new HashMap<>();
    private final HashMap<String, Polyline> mLines = new HashMap<>();
    private final HashMap<String, Polygon> mPolygons = new HashMap<>();
    private final HashMap<String, GroundOverlay> mItemMarkers = new HashMap<>();
    private final Queue<GroundOverlay> mOverlayPool = new LinkedList<>();
    private final Queue<Polyline> mPolylinePool = new LinkedList<>();

    private ActivityResultLauncher<Intent> mPortalActivityResultLauncher;


    // ===========================================================
    // Fields and permissions
    // ===========================================================
    private SharedPreferences mPrefs;

    private GeoPoint mCurrentLocation = null;
    private static final int RECORD_REQUEST_CODE = 101;
    private long mLastScan = 0;
    // FIXME this should be used to set "location inaccurate" if updates mysteriously stop
    private Date mLastLocationAcquired = null;
    private GeoPoint mLastLocation = null;


    // ===========================================================
    // Other (location)
    // ===========================================================
    private final GroundOverlay mActionRadius = new GroundOverlay();
    private final GroundOverlay mPlayerCursor = new GroundOverlay();
    private LocationManager mLocationManager = null;
    private MyLocationNewOverlay mLocationOverlay = null;
    private AnimatedCircleOverlay mSonarOverlay;
    private final Map<Integer, Bitmap> mCachedPlayerCursorRotations = new HashMap<>();

    // device sensor manager
    private SensorManager mSensorManager;
    private float mBearing = 0;
    private Sensor mRotationVectorSensor;
    private boolean mHaveRotationSensor = false;

    private final int MAP_ROTATION_ARBITRARY = 2;
    private final int MAP_ROTATION_FLOATING = 3;
    private int CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;

    private GeoPoint mLastCursorLocation = null;
    private float mLastCursorBearing = -1;
    private static final double CURSOR_JITTER_THRESHOLD = 0.05;

    // ===========================================================
    // UX/UI Stuff - Events
    // ===========================================================
    private Marker mMarkerInfoCard;
    private boolean isRotating = false;
    private boolean isDoubleClick = false;
    MapEventsReceiver mReceive = new MapEventsReceiver() {
        @Override
        public boolean singleTapConfirmedHelper(GeoPoint p) {
            return false;
        }

        @Override
        public boolean longPressHelper(GeoPoint p) {
            if (!isRotating && !isDoubleClick) {
                ((ActivityMain) requireActivity()).showFireMenu(p);
                return true;
            }
            return false;
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        GeoPoint location;
        if (mCurrentLocation != null) {
            location = mCurrentLocation;
            // todo: check this - did we acquire a location? i think not.
            //  i don't think this makes sense.
            mLastLocationAcquired = new Date();
        } else {
            location = (GeoPoint) mMap.getMapCenter();
        }

        if (event.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);

            float azimuth = (float) Math.toDegrees(orientation[0]);
            mBearing = (azimuth + 360) % 360;

            mHaveRotationSensor = true;
        }
        if (CURRENT_MAP_ORIENTATION_SCHEME == MAP_ROTATION_FLOATING) {
            mMap.setMapOrientation(-mBearing);
        }
        drawPlayerCursor(location, mBearing);
    }

    private boolean isSignificantChange(GeoPoint newLocation, GeoPoint oldLocation) {
        return newLocation.distanceToAsDouble(oldLocation) > CURSOR_JITTER_THRESHOLD;
    }

    private boolean isSignificantChange(float newBearing, float oldBearing) {
        return Math.abs(newBearing - oldBearing) >= 2.5;
    }

    public void drawPlayerCursor(GeoPoint location, float bearing) {
        if (mLastCursorLocation != null && !isSignificantChange(location, mLastCursorLocation) && !isSignificantChange(bearing, mLastCursorBearing)) {
            return; // Skip jittery updates
        }

        mLastCursorLocation = location;
        mLastCursorBearing = bearing;

        mMap.getOverlayManager().remove(mPlayerCursor);
        mMap.getOverlayManager().remove(mActionRadius);

        /*
         we need to do a couple of things:
         1) find a better way to draw the cursor.
             i think you can paste the image on to a rotated canvas.
             that's probably the correct approach.
             It might be sensible to draw the cursor programmatically (like the compass rose)
             but it's crucial that the drawing method leaves the cursor on the map at the
             CORRECT SIZE for the world - like a GroundOverlay
         2) integrate the actionRadius. tying them together will remove that jumpy feeling.
             this may or may not be the correct solution,
             as basically it might mean more compositing work. we shall see later, maybe.
         */
        mPlayerCursor.setPosition(location.destinationPoint(15, TOP_LEFT_ANGLE), location.destinationPoint(15, BOTTOM_RIGHT_ANGLE));
        mSonarOverlay.updateLocation(location);

        // Normalize the bearing to the closest 3-degree step
        int normalizedBearing = (int) (Math.round(bearing / 3.0) * 3.0) % 360;
        Bitmap rotatedCursor = getRotatedBitmap(mIcons.get("playercursor"), normalizedBearing);
        if (rotatedCursor != null) {
            mPlayerCursor.setImage(rotatedCursor);
        }

        mActionRadius.setPosition(location.destinationPoint(56.57, TOP_LEFT_ANGLE), location.destinationPoint(56.57, BOTTOM_RIGHT_ANGLE));
        mActionRadius.setImage(mIcons.get("actionradius"));
        mMap.getOverlayManager().add(mActionRadius);

        mMap.getOverlayManager().add(mPlayerCursor);
    }

    /**
     * Called when the accuracy of the registered sensor has changed.  Unlike
     * onSensorChanged(), this is only called when this accuracy value changes.
     *
     * <p>See the SENSOR_STATUS_* constants in
     * {@link SensorManager SensorManager} for details.
     *
     * @param accuracy The new accuracy of this sensor, one of
     *                 {@code SensorManager.SENSOR_STATUS_*}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // unimplemented, don't care
    }


    public void onLocationChanged(@NonNull Location location) {
        slurp();
        mCurrentLocation = new GeoPoint(location);
        var loc = new net.opengress.slimgress.api.Common.Location(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        mApp.getLocationViewModel().setLocationData(loc);
        mGame.updateLocation(loc);
        mLastLocationAcquired = new Date();
        displayMyCurrentLocationOverlay(mCurrentLocation, location.getBearing());
    }

    public void onProviderDisabled(@NonNull String provider) {
        if (!Objects.equals(provider, "gps")) {
            return;
        }
        setLocationInaccurate(true);
    }

    public void onProviderEnabled(@NonNull String provider) {
        // don't register location as no longer inaccurate until new location info arrives
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        // probably useless, might not be called above android Q
        // could be interesting for checking that gps fix comes from satellites
    }


    private void displayMyCurrentLocationOverlay(GeoPoint currentLocation, float bearing) {
        drawPlayerCursor(currentLocation, mHaveRotationSensor ? mBearing : bearing);

        long now = System.currentTimeMillis();

        if (mLastScan == 0 ||
                mLastLocation == null ||
                (now - mLastScan >= mUpdateIntervalMS) ||
                (now - mLastScan >= mMinUpdateIntervalMS && mLastLocation.distanceToAsDouble(currentLocation) >= mUpdateDistanceM)
        ) {
            if (mGame.getLocation() != null) {
                final Handler uiHandler = new Handler();
                uiHandler.post(() -> {
                    // guard against scanning too fast if request fails
                    mLastScan = now + mMinUpdateIntervalMS;
                    updateWorld();
                });

            }
        }
        mLastLocationAcquired = new Date();
        mLastLocation = currentLocation;

        setLocationInaccurate(false);

    }

    private Bitmap getRotatedBitmap(Bitmap originalCursor, int angle) {
        // Check if the bitmap is already cached for this angle
        if (mCachedPlayerCursorRotations.containsKey(angle)) {
            return mCachedPlayerCursorRotations.get(angle);
        }

        int width = originalCursor.getWidth();
        int height = originalCursor.getHeight();

        // Calculate the new bounding box size to avoid stretching
        int newSize = (int) (Math.sqrt(2) * Math.max(width, height));

        // Create a new bitmap with the larger size to fit the rotation
        Bitmap newBitmap = Bitmap.createBitmap(newSize, newSize, originalCursor.getConfig());

        // Create a canvas to draw the rotated image
        Canvas canvas = new Canvas(newBitmap);

        // Compute the matrix to rotate around the center of the new bitmap
        Matrix matrix = new Matrix();
        int centerX = (newSize - width) / 2;
        int centerY = (newSize - height) / 2;
        matrix.postTranslate(centerX, centerY);  // Center the original image in the new bitmap
        matrix.postRotate(angle, newSize / 2f, newSize / 2f);  // Rotate around the center of the bitmap

        // Draw the original bitmap onto the new bitmap using the rotation matrix
        canvas.drawBitmap(originalCursor, matrix, null);

        // Cache the rotated bitmap
        mCachedPlayerCursorRotations.put(angle, newBitmap);

        return newBitmap;  // Return the newly created rotated bitmap
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mGame.getKnobs() == null) {
            Intent intent = new Intent(getActivity(), ActivitySplash.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }

        mSensorManager = (SensorManager) requireActivity().getSystemService(SENSOR_SERVICE);
        mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);

        mApp.getDeletedEntityGuidsModel().getDeletedEntityGuids().observe(this, this::onReceiveDeletedEntityGuids);
        mActionRadius.setTransparency(0.5f);

        mPortalActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onPortalActivityResult
        );

        mScannerKnobs = mGame.getKnobs().getScannerKnobs();
        mActionRadiusM = mScannerKnobs.getActionRadiusM();
        mUpdateIntervalMS = mScannerKnobs.getUpdateIntervalMS();
        mMinUpdateIntervalMS = mScannerKnobs.getMinUpdateIntervalMS();
        mUpdateDistanceM = mScannerKnobs.getUpdateDistanceM();

    }

    public void onReceiveDeletedEntityGuids(List<String> deletedEntityGuids) {
        for (String guid : deletedEntityGuids) {

            // for XM particles
            if (guid.contains(".")) {
                long particle = Long.parseLong(guid.substring(0, 16), 16);
                if (mXMMarkers.containsKey(particle)) {
                    mMap.getOverlays().remove(mXMMarkers.get(particle));
                    recycleOverlay(mXMMarkers.get(particle));
                    mXMMarkers.remove(particle);
                }
                continue;
            }

            // for portals
            if (mPortalMarkers.containsKey(guid)) {
                mMap.getOverlays().remove(mPortalMarkers.get(guid));
                mPortalMarkers.remove(guid);
                continue;
            }

            // for resonators
            if (mResonatorToPortalSlotLookup.containsKey(guid)) {
                var portal = Objects.requireNonNull(mResonatorToPortalSlotLookup.get(guid)).first;
                var slot = Objects.requireNonNull(mResonatorToPortalSlotLookup.get(guid)).second;
                var resoParts = Objects.requireNonNull(mResonatorMarkers.get(portal)).get(slot);
                if (resoParts != null) {
                    mMap.getOverlays().remove(resoParts.first);
                    mMap.getOverlays().remove(resoParts.second);
                    recycleOverlay(Objects.requireNonNull(resoParts.first));
                    recyclePolyline(Objects.requireNonNull(resoParts.second));
                }
                mResonatorToPortalSlotLookup.remove(guid);
                Objects.requireNonNull(mResonatorMarkers.get(portal)).remove(slot);
                continue;
            }

            // for links
            if (mLines.containsKey(guid)) {
                mMap.getOverlays().remove(mLines.get(guid));
                recyclePolyline(mLines.get(guid));
                mLines.remove(guid);
                continue;
            }

            // for fields
            if (mPolygons.containsKey(guid)) {
                mMap.getOverlays().remove(mPolygons.get(guid));
                mPolygons.remove(guid);
            }

            // for dropped items
            if (mItemMarkers.containsKey(guid)) {
                mMap.getOverlays().remove(mItemMarkers.get(guid));
                recycleOverlay(mItemMarkers.get(guid));
                mItemMarkers.remove(guid);
            }

        }
    }

    private MapTileProviderBasic createTileProvider(MapProvider provider) {
        ITileSource tileSource;
        if (provider == null) {
            tileSource = new BlankTileSource();
        } else if (provider.getCoordinateSystem() == XYZ) {
            tileSource = new XYTileSource(provider.getName(), provider.getMinZoom(), provider.getMaxZoom(), provider.getTileSize(), provider.getFilenameEnding(),
                    provider.getBaseUrls());
        } else {
            throw new RuntimeException("Unknown slippy map coordinate system!");
        }
        var tileProvider = new MapTileProviderBasic(mApp.getApplicationContext(), tileSource);
        if (provider == null) {
            tileProvider.detach();
        }
        return tileProvider;
    }

    private MapTileProviderBasic getMapTileProvider(String name, String def) {
        MapProvider mapProvider = mGame.getKnobs().getMapCompositionRootKnobs().fromString(mPrefs.getString(name, def));
        return createTileProvider(mapProvider);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mPrefs = mApp.getApplicationContext().getSharedPreferences(requireActivity().getApplicationInfo().packageName, Context.MODE_PRIVATE);
        // allows map tiles to be cached so map draws properly
        Configuration.getInstance().load(requireContext(), mPrefs);
        Configuration.getInstance().setOsmdroidTileCache(new File(requireActivity().getCacheDir(), "osmdroid")); // Set the cache directory

        // suggestion... didn't do anything i liked
//        Configuration.getInstance().setCacheMapTileCount((short) 27);
//        Configuration.getInstance().setCacheMapTileOvershoot((short) 27);
//        Configuration.getInstance().setExpirationExtendedDuration(Long.MAX_VALUE);
//        Configuration.getInstance().setTileFileSystemCacheMaxBytes(50L * 1024L * 1024L);
//        Configuration.getInstance().setExpirationExtendedDuration(60L * 60L * 24L * 7L * 1000L);



        mPrefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            Log.d("Scanner", String.format("PREFERENCE CHANGE IN ANOTHER THING: %s", key));
            if (Objects.requireNonNull(key).equals(PREFS_DEVICE_TILE_SOURCE)) {
                mMap.setTileProvider(getMapTileProvider(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT));
            }
        });

        // set up map tile source before creating map, so we don't download wrong tiles wastefully
        final MapTileProviderBasic tileProvider = getMapTileProvider(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT);
        mMap = new MapView(inflater.getContext(), tileProvider) {
            private double mCurrAngle = 0;
            private double mPrevAngle = 0;
            private long lastClickTime = 0;
            private float startY = 0;
            private static final float ZOOM_SENSITIVITY = 0.1f;

            @Override
            public boolean onTouchEvent(MotionEvent event) {

                if (event.getPointerCount() == 1) {
                    final float xc = (float) getWidth() / 2;
                    final float yc = (float) getHeight() / 2;
                    final float x = event.getX();
                    final float y = event.getY();
                    double angrad = Math.atan2(x - xc, yc - y);

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN -> {
                            // by definition, not rotating. remember for menu
                            isRotating = false;
                            // get ready in case we rotate
                            mCurrAngle = Math.toDegrees(angrad);
                            long clickTime = System.currentTimeMillis();
                            if (clickTime - lastClickTime < 300) { // Double-click detected
                                isDoubleClick = true;
                                startY = event.getY();
                                return true;
                            } else {
                                isDoubleClick = false;
                            }
                            lastClickTime = clickTime;
                        }
                        case MotionEvent.ACTION_MOVE -> {
                            if (isDoubleClick) {
                                float currentY = event.getY();
                                // jitter filter
                                if (Math.abs(currentY - startY) >= 0.06) {
                                    if (currentY > startY) {
                                        getController().zoomTo(getZoomLevelDouble() - ZOOM_SENSITIVITY);
                                    } else {
                                        getController().zoomTo(getZoomLevelDouble() + ZOOM_SENSITIVITY);
                                    }
                                }
                                startY = currentY;
                                return true;
                            }
                            CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;
                            mPrevAngle = mCurrAngle;
                            mCurrAngle = Math.toDegrees(angrad);
                            float rotating = (float) (mPrevAngle - mCurrAngle);
                            setMapOrientation(getMapOrientation() - rotating);
                            // guard against long-presses for context menu with jitter
                            if (Math.abs(rotating) >= 0.09 || isRotating) {
                                isRotating = true;
                                return true;
                            }
                        }
                        case MotionEvent.ACTION_UP -> {
                            isRotating = false;
                            isDoubleClick = false;
                            mPrevAngle = mCurrAngle = 0;
                        }
                    }
                } else {
                    // i assume you're doing a pinch zoom/rotate
                    CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;
//                    if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
//                        // Detect rotation gesture and prevent it
//                        return true; // Return true to consume the event and prevent rotation
//                    }
                }
                return super.onTouchEvent(event);
            }
        };

        mMap.getMapOverlay().setLoadingBackgroundColor(Color.BLACK);
        mMap.setDestroyMode(false);
        mMap.setMinZoomLevel(16d);
        mMap.setMaxZoomLevel(22d);
        mMap.setFlingEnabled(false);
        //needed for pinch zooms
        mMap.setMultiTouchControls(true);

        loadAssets();

        return mMap;
    }

    protected void makeRequest() {
        ActivityCompat.requestPermissions(requireActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                RECORD_REQUEST_CODE);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = this.requireActivity();


        Configuration.getInstance().setUserAgentValue("Slimgress/Openflux (OSMDroid)");

        mMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        //On screen compass
        CompassOverlay mCompassOverlay = getCompassOverlay(context);
        mMap.getOverlays().add(mCompassOverlay);
        MapEventsOverlay overlayEvents = new MapEventsOverlay(mReceive);
        mMap.getOverlays().add(overlayEvents);

        //scales tiles to the current screen's DPI, helps with readability of labels
        mMap.setTilesScaledToDpi(true);

        //the rest of this is restoring the last map location the user looked at
        final float zoomLevel = mPrefs.getFloat(PREFS_OSM_ZOOM_LEVEL_DOUBLE, 18);
        mMap.getController().setZoom(zoomLevel);
        mMap.setMapOrientation(0, false);
        final String latitudeString = mPrefs.getString(PREFS_OSM_LATITUDE_STRING, "1.0");
        final String longitudeString = mPrefs.getString(PREFS_OSM_LONGITUDE_STRING, "1.0");
        final double latitude = Double.parseDouble(latitudeString);
        final double longitude = Double.parseDouble(longitudeString);
        mMap.setExpectedCenter(new GeoPoint(latitude, longitude));
        mSonarOverlay = new AnimatedCircleOverlay(mMap, 500, 60);
        mSonarOverlay.setColor(0x33FFFF00);
        mSonarOverlay.setWidth(1);
        mSonarOverlay.setRunOnce(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mSonarOverlay.setBlendMode(BlendMode.DIFFERENCE);
        }
        mSonarOverlay.start();
    }

    private @NonNull CompassOverlay getCompassOverlay(Context context) {

        CompassOverlay mCompassOverlay = new CompassOverlay(context, new InternalCompassOrientationProvider(context),
                mMap) {
            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                // FIXME set auto/manual rotation (may need to reset to north)
                Point reuse = new Point();
                mapView.getProjection().rotateAndScalePoint((int) e.getX(), (int) e.getY(), reuse);
                if (reuse.x < mCompassFrameBitmap.getWidth() && reuse.y < mCompassFrameCenterY + mCompassFrameBitmap.getHeight()) {
                    if (CURRENT_MAP_ORIENTATION_SCHEME == MAP_ROTATION_FLOATING) {
                        CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;
                        mapView.setMapOrientation(0);
                    } else {
                        CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_FLOATING;
                    }
                    return true;
                }

                return super.onSingleTapConfirmed(e, mapView);
            }

            @Override
            public void draw(Canvas canvas, MapView mapView, boolean shadow) {
                // Adjust the compass orientation to always point north
                drawCompass(canvas, -mapView.getMapOrientation(), mapView.getProjection().getScreenRect());
            }
        };
        mCompassOverlay.enableCompass();
        mCompassOverlay.setCompassCenter(30, 60);
        return mCompassOverlay;
    }

    @Override
    public void onPause() {
        //save the current location
        final SharedPreferences.Editor edit = mPrefs.edit();
        edit.putString(PREFS_OSM_LATITUDE_STRING, String.valueOf(mMap.getMapCenter().getLatitude()));
        edit.putString(PREFS_OSM_LONGITUDE_STRING, String.valueOf(mMap.getMapCenter().getLongitude()));
        edit.putFloat(PREFS_OSM_ZOOM_LEVEL_DOUBLE, (float) mMap.getZoomLevelDouble());
        edit.apply();

        mMap.onPause();
        super.onPause();

        // to stop the listener and save battery
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //this part terminates all of the overlays and background threads for osmdroid
        //only needed when you programmatically create the map
        mMap.onDetach();

    }

    @Override
    public void onResume() {
        super.onResume();
        mMap.onResume();

        // technically this probably means we're setting this up twice.
        // probably mostly harmless, but maybe try to fix later.
        mMap.setTileProvider(getMapTileProvider(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT));

        if (mLastLocationAcquired == null || mLastLocationAcquired.before(new Date(System.currentTimeMillis() - mUpdateIntervalMS))) {
            setLocationInaccurate(true);
        } else if (mLastLocationAcquired.before(new Date(System.currentTimeMillis() - mMinUpdateIntervalMS))) {
            // might be pointing the wrong way til next location update but that's ok
            displayMyCurrentLocationOverlay(mCurrentLocation, 0);
        }

        if (mRotationVectorSensor != null) {
            // FIXME put this in a pref
            mSensorManager.registerListener(this, mRotationVectorSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }


        // ===========================================================
        // Other (location)
        // ===========================================================

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setMessage("Permission to access the device location is required for this app to function correctly.")
                    .setTitle("Permission required");

            builder.setPositiveButton("OK", (dialog, id) -> makeRequest());

            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            requestLocationUpdates();
        }

        if (getActivity() != null) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    @SuppressLint("MissingPermission")
    public void requestLocationUpdates() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) mApp.getSystemService(Context.LOCATION_SERVICE);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            Location location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                mCurrentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
            }
        }
        if (mLocationOverlay == null) {
            mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mMap);
            mLocationOverlay.enableMyLocation();
            mLocationOverlay.enableFollowLocation();
            mLocationOverlay.setEnableAutoStop(false);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setMapEnabled(boolean bool) {
        // there's probably a better way
        if (bool) {
            mMap.setOnTouchListener(null);
        } else {
            mMap.setOnTouchListener((v, event) -> true);
        }
    }

    private void setLocationInaccurate(boolean isInaccurate) {
        // unresearched workaround for crash on exit
        if (getActivity() == null || getActivity().findViewById(R.id.quickMessage) == null) {
            return;
        }
        // FIXME this is fine, but really the game state needs to know about it.
        //  for example, if i'm about to hack a portal and i switch my GPS off, that shouldn't work!

        // FIXME this MIGHT all be able to fire before view exists, need to maybe wrap it up
        getActivity().findViewById(R.id.buttonComm).setEnabled(!isInaccurate);
        getActivity().findViewById(R.id.buttonOps).setEnabled(!isInaccurate);
        setMapEnabled(!isInaccurate);
        // FIXME this also needs to consider XM levels
        getActivity().findViewById(R.id.scannerDisabledOverlay).setVisibility(isInaccurate ? View.VISIBLE : View.GONE);
        if (isInaccurate) {
            displayQuickMessage(getStringSafely(R.string.location_inaccurate));
            mMap.getOverlayManager().remove(mActionRadius);
        } else {
            if (Objects.equals(getQuickMessage(), getStringSafely(R.string.location_inaccurate))) {
                hideQuickMessage();
            }
        }
    }

    private void loadAssets() {
        AssetManager assetManager = requireActivity().getAssets();
        Map<String, TeamKnobs.TeamType> teams = mGame.getKnobs().getTeamKnobs().getTeams();
        for (String team : teams.keySet()) {
            mIcons.put(team, getTintedImage("portalTexture_NEUTRAL.png", 0xff000000 + Objects.requireNonNull(teams.get(team)).getColour(), assetManager));
        }

        mIcons.put("particle", getBitmapFromAsset("particle.png", assetManager));
        mIcons.put("actionradius", getBitmapFromAsset("actionradius.png", assetManager));
        mIcons.put("playercursor", getTintedImage("playercursor.png", 0xff000000 + Objects.requireNonNull(mGame.getAgent().getTeam()).getColour(), assetManager));
        mIcons.put("ada", getBitmapFromDrawable(getContext(), R.drawable.ada));
        mIcons.put("c1", getBitmapFromDrawable(getContext(), R.drawable.c1));
        mIcons.put("c2", getBitmapFromDrawable(getContext(), R.drawable.c2));
        mIcons.put("c3", getBitmapFromDrawable(getContext(), R.drawable.c3));
        mIcons.put("c4", getBitmapFromDrawable(getContext(), R.drawable.c4));
        mIcons.put("c5", getBitmapFromDrawable(getContext(), R.drawable.c5));
        mIcons.put("c6", getBitmapFromDrawable(getContext(), R.drawable.c6));
        mIcons.put("c7", getBitmapFromDrawable(getContext(), R.drawable.c7));
        mIcons.put("c8", getBitmapFromDrawable(getContext(), R.drawable.c8));
        mIcons.put("capsule", getBitmapFromDrawable(getContext(), R.drawable.capsule));
        mIcons.put("dap", getBitmapFromDrawable(getContext(), R.drawable.dap));
        mIcons.put("force_amp", getBitmapFromDrawable(getContext(), R.drawable.force_amp));
        mIcons.put("heatsink_common", getBitmapFromDrawable(getContext(), R.drawable.heatsink_common));
        mIcons.put("heatsink_rare", getBitmapFromDrawable(getContext(), R.drawable.heatsink_rare));
        mIcons.put("heatsink_very_rare", getBitmapFromDrawable(getContext(), R.drawable.heatsink_very_rare));
        mIcons.put("jarvis", getBitmapFromDrawable(getContext(), R.drawable.jarvis));
        mIcons.put("linkamp_rare", getBitmapFromDrawable(getContext(), R.drawable.linkamp_rare));
        mIcons.put("linkamp_very_rare", getBitmapFromDrawable(getContext(), R.drawable.linkamp_very_rare));
        mIcons.put("multihack_common", getBitmapFromDrawable(getContext(), R.drawable.multihack_common));
        mIcons.put("multihack_rare", getBitmapFromDrawable(getContext(), R.drawable.multihack_rare));
        mIcons.put("multihack_very_rare", getBitmapFromDrawable(getContext(), R.drawable.multihack_very_rare));
        mIcons.put("portalkey", getBitmapFromDrawable(getContext(), R.drawable.portalkey));
        mIcons.put("r1", getBitmapFromDrawable(getContext(), R.drawable.r1));
        mIcons.put("r2", getBitmapFromDrawable(getContext(), R.drawable.r2));
        mIcons.put("r3", getBitmapFromDrawable(getContext(), R.drawable.r3));
        mIcons.put("r4", getBitmapFromDrawable(getContext(), R.drawable.r4));
        mIcons.put("r5", getBitmapFromDrawable(getContext(), R.drawable.r5));
        mIcons.put("r6", getBitmapFromDrawable(getContext(), R.drawable.r6));
        mIcons.put("r7", getBitmapFromDrawable(getContext(), R.drawable.r7));
        mIcons.put("r8", getBitmapFromDrawable(getContext(), R.drawable.r8));
        mIcons.put("shield_common", getBitmapFromDrawable(getContext(), R.drawable.shield_common));
        mIcons.put("shield_rare", getBitmapFromDrawable(getContext(), R.drawable.shield_rare));
        mIcons.put("shield_very_rare", getBitmapFromDrawable(getContext(), R.drawable.shield_very_rare));
        mIcons.put("turret", getBitmapFromDrawable(getContext(), R.drawable.turret));
        mIcons.put("u1", getBitmapFromDrawable(getContext(), R.drawable.u1));
        mIcons.put("u2", getBitmapFromDrawable(getContext(), R.drawable.u2));
        mIcons.put("u3", getBitmapFromDrawable(getContext(), R.drawable.u3));
        mIcons.put("u4", getBitmapFromDrawable(getContext(), R.drawable.u4));
        mIcons.put("u5", getBitmapFromDrawable(getContext(), R.drawable.u5));
        mIcons.put("u6", getBitmapFromDrawable(getContext(), R.drawable.u6));
        mIcons.put("u7", getBitmapFromDrawable(getContext(), R.drawable.u7));
        mIcons.put("u8", getBitmapFromDrawable(getContext(), R.drawable.u8));
        mIcons.put("x1", getBitmapFromDrawable(getContext(), R.drawable.x1));
        mIcons.put("x2", getBitmapFromDrawable(getContext(), R.drawable.x2));
        mIcons.put("x3", getBitmapFromDrawable(getContext(), R.drawable.x3));
        mIcons.put("x4", getBitmapFromDrawable(getContext(), R.drawable.x4));
        mIcons.put("x5", getBitmapFromDrawable(getContext(), R.drawable.x5));
        mIcons.put("x6", getBitmapFromDrawable(getContext(), R.drawable.x6));
        mIcons.put("x7", getBitmapFromDrawable(getContext(), R.drawable.x7));
        mIcons.put("x8", getBitmapFromDrawable(getContext(), R.drawable.x8));
    }

    @SuppressLint("DefaultLocale")
    public synchronized void updateWorld() {
        if (!notBouncing("updateWorld", mMinUpdateIntervalMS)) {
            return;
        }
        mSonarOverlay.trigger();
        displayQuickMessage(getStringSafely(R.string.scanning_local_area));

        // handle interface result (on timer thread)
        final Handler resultHandler = new Handler(msg -> {

            displayQuickMessage(getStringSafely(R.string.preparing_the_inventory));
            mGame.intGetInventory(new Handler(ms -> {
                // Since we just updated the inventory, let's also update the status of all the portal keys
                var keys = mGame.getInventory().getItems(PortalKey);
                Set<String> portalGUIDs = new HashSet<>();
                for (var item : keys) {
                    String portalGuid = ((ItemPortalKey) item).getPortalGuid();
                    if (!mGame.getWorld().getGameEntities().containsKey(portalGuid)) {
                        portalGUIDs.add(portalGuid);
                    }
                }
                if (!portalGUIDs.isEmpty()) {
                    mGame.intGetModifiedEntitiesByGuid(portalGUIDs.toArray(new String[0]), new Handler(m -> true));
                }
                return true;
            }));

            // protect against crash from unclean exit
            if (getActivity() == null) {
                return true;
            }

            if (msg.getData().keySet().contains("Error")) {
                displayQuickMessage(getStringSafely(R.string.scan_failed));
                return true;
            }

            if (Objects.equals(getQuickMessage(), getStringSafely(R.string.scan_failed))) {
                hideQuickMessage();
            }

// now comes from elsewhere but might end up coming through a viewmodel
//            updateScreen(uiHandler);

            displayQuickMessage(getStringSafely(R.string.scan_complete));
            setQuickMessageTimeout();
            if (getActivity() != null) {
                ((ActivityMain) getActivity()).updateAgent();
            }

            return true;
        });

        slurp();

        // get objects (on new thread)
        new Thread(() -> mGame.intGetObjectsInCells(mGame.getLocation(), resultHandler)).start();
        final Handler commsHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> mGame.intLoadCommunication(false, 50, false, commsHandler)).start();
    }

    private void slurp() {

        final net.opengress.slimgress.api.Common.Location playerLoc = mGame.getLocation();
        if (mGame.getAgent() == null || playerLoc == null) {
            return;
        }

        int oldXM = mGame.getAgent().getEnergy();
        int maxXM = mGame.getAgent().getEnergyMax();
        int newXM = 0;

        if (oldXM >= maxXM) {
            return;
        }

        // FIXME maybe don't try to slurp particles that aren't needed to fill the tank
        //  -- note that we may need to sort the particles and pick out the optimal configuration
        //  -- also note that if we're really cheeky we may want/be able to do that serverside
        Map<Long, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        ArrayList<String> slurpableParticles = new ArrayList<>();

        final GeoPoint playerLatLng = playerLoc.getLatLng();

        for (Map.Entry<Long, XMParticle> entry : xmParticles.entrySet()) {
            if (oldXM + newXM >= maxXM) {
                continue;
            }
            Long key = entry.getKey();
            XMParticle particle = entry.getValue();

            // FIXME this is honestly the worst imaginable solution, but for now it's what i have...
            assert particle != null;
            final net.opengress.slimgress.api.Common.Location location = particle.getCellLocation();
            if (location.getLatLng().distanceToAsDouble(playerLatLng) < mActionRadiusM) {

                slurpableParticles.add(particle.getGuid());
                newXM += particle.getAmount();
                var marker = mXMMarkers.remove(key);
                mMap.getOverlays().remove(marker);
            }
        }

        mGame.addSlurpableXMParticles(slurpableParticles);
        mGame.getAgent().addEnergy(newXM);

        if (getActivity() != null && newXM > 0) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    public void updateScreen(Handler uiHandler) {
        new Thread(() -> {
            // draw xm particles
            drawXMParticles();

            // draw game entities
            Map<String, GameEntityBase> entities = mGame.getWorld().getGameEntities();
            Set<String> keys = entities.keySet();
            for (String key : keys) {
                final GameEntityBase entity = entities.get(key);
                assert entity != null;

                uiHandler.post(() -> {
                    if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal) {
                        drawPortal((GameEntityPortal) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Link) {
                        drawLink((GameEntityLink) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField) {
                        drawField((GameEntityControlField) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Item) {
                        drawItem((GameEntityItem) entity);
                    }
                });
            }

            mLastScan = System.currentTimeMillis() + mMinUpdateIntervalMS;
        }).start();
    }

    private void drawXMParticles() {
        // draw xm particles (as groundoverlays)
        Map<Long, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        Set<Long> keys = xmParticles.keySet();

        for (Long key : keys) {
            XMParticle particle = xmParticles.get(key);
            assert particle != null;

            // only draw if not already in list
            if (!mXMMarkers.containsKey(particle.getCellId())) {

                final net.opengress.slimgress.api.Common.Location location = particle.getCellLocation();

                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(() -> {
                    Bitmap particleIcon = mIcons.get("particle");

                    GroundOverlay marker = getOverlay();
                    marker.setPosition(location.getLatLng().destinationPoint(5, TOP_LEFT_ANGLE), location.getLatLng().destinationPoint(5, BOTTOM_RIGHT_ANGLE));
                    marker.setImage(particleIcon);

                    mMap.getOverlays().add(marker);
                    mXMMarkers.put(particle.getCellId(), marker);
                });
            }
        }
    }

    private GroundOverlay getOverlay() {
        GroundOverlay overlay = mOverlayPool.poll();
        if (overlay == null) {
            overlay = new GroundOverlay();
        }
        return overlay;
    }

    private void recycleOverlay(GroundOverlay overlay) {
        mOverlayPool.add(overlay);
    }

    private Polyline getPolyline(MapView map) {
        Polyline overlay = mPolylinePool.poll();
        if (overlay == null) {
            overlay = new Polyline(map);
        }
        return overlay;
    }

    private void recyclePolyline(Polyline overlay) {
        mPolylinePool.add(overlay);
    }

    private void drawPortal(@NonNull final GameEntityPortal portal) {
        final Team team = portal.getPortalTeam();
        if (mMap != null) {
            // if marker already exists, remove it so it can be updated
            String guid = portal.getEntityGuid();
            if (mPortalMarkers.containsKey(guid)) {
                GroundOverlay o = mPortalMarkers.get(guid);
                mMap.getOverlays().remove(o);
                mPortalMarkers.remove(guid);
                assert o != null;
                recycleOverlay(o);
            }
            final net.opengress.slimgress.api.Common.Location location = portal.getPortalLocation();

            ActivityMain activity = (ActivityMain) getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                // TODO: make portal marker display portal health/deployment info (opacity x white, use shield image etc)
                // i would also like to draw the resonators around it, but i'm not sure that that would be practical with osmdroid
                // ... maybe i can at least write the portal level on the portal, like in iitc
                // it's quite possible that resonators can live in a separate Hash of markers,
                //   as long as the guids are stored with the portal info
                Bitmap portalIcon = mIcons.get(team.toString());

                GroundOverlay marker = new GroundOverlay() {
                    public boolean touchedBy(@NonNull final MotionEvent event) {
                        GeoPoint tappedGeoPoint = (GeoPoint) mMap.getProjection().fromPixels((int) event.getX(), (int) event.getY());
                        return getBounds().contains(tappedGeoPoint);
                    }

                    @Override
                    public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                        if (activity.isSelectingTargetPortal() && touchedBy(e)) {
                            activity.setTargetPortal(portal.getEntityGuid());
                            showInfoCard(portal);
                            return true;
                        }
                        if (touchedBy(e)) {
                            Intent myIntent = new Intent(getContext(), ActivityPortal.class);
                            mGame.setCurrentPortal(portal);
                            mPortalActivityResultLauncher.launch(myIntent);
                            return true;
                        }
                        return false;
                    }
                };
                marker.setPosition(location.getLatLng().destinationPoint(20, TOP_LEFT_ANGLE), location.getLatLng().destinationPoint(20, BOTTOM_RIGHT_ANGLE));
                marker.setImage(portalIcon);

                mMap.getOverlays().add(marker);
                mPortalMarkers.put(guid, marker);

                for (var reso : portal.getPortalResonators()) {
                    if (reso != null) {
                        drawResonatorForPortal(portal, reso);
                    }
                }
            });

        }
    }

    public void removeInfoCard() {
        if (mMarkerInfoCard != null) {
            mMap.getOverlays().remove(mMarkerInfoCard);
            mMarkerInfoCard = null;
        }
    }

    @SuppressLint("InflateParams")
    private void showInfoCard(GameEntityPortal portal) {
        // TODO theoretically I can update this as the user moves, but for now I do not.
        removeInfoCard();
        View markerView = LayoutInflater.from(getContext()).inflate(R.layout.marker_info_card, null);
        TextView textView1 = markerView.findViewById(R.id.marker_info_card_portal_level);
        TextView textView2 = markerView.findViewById(R.id.marker_info_card_portal_team);
        TextView textView3 = markerView.findViewById(R.id.marker_info_card_portal_title);
        TextView textview4 = markerView.findViewById(R.id.marker_info_card_portal_distance);

        textView1.setText(String.format(Locale.getDefault(), "L%d ", portal.getPortalLevel()));
        textView1.setTextColor(0xFF000000 + getColorFromResources(getResources(), getLevelColor(portal.getPortalLevel())));
        textView2.setText(R.string.portal);
        textView2.setTextColor(0xFF000000 + Objects.requireNonNull(mGame.getKnobs().getTeamKnobs().getTeams().get(portal.getPortalTeam().toString())).getColour());
        textView3.setText(portal.getPortalTitle());
        int dist = (int) mGame.getLocation().getLatLng().distanceToAsDouble(portal.getPortalLocation().getLatLng());
        textview4.setText(String.format(Locale.getDefault(), "Distance: %dm", dist));


        Marker marker = new Marker(mMap);
        marker.setOnMarkerClickListener((marker1, mapView) -> false);
        marker.setPosition(portal.getPortalLocation().getLatLng());
        marker.setIcon(new BitmapDrawable(getResources(), createDrawableFromView(requireContext(), markerView)));
        mMarkerInfoCard = marker;
        mMap.getOverlays().add(marker);
//        mMap.invalidate();
    }

    private void drawResonatorForPortal(GameEntityPortal portal, GameEntityPortal.LinkedResonator reso) {
        if (portal == null || reso == null) {
            Log.e("SCANNER", "Invalid input parameters");
            return;
        }

        final double LINKED_RESO_SCALE = 1.75;

        // Remove existing marker if present
        var m = mResonatorMarkers.get(portal.getEntityGuid());
        if (m != null && m.containsKey(reso.slot)) {
            mMap.getOverlays().remove(Objects.requireNonNull(m.get(reso.slot)).first);
            recycleOverlay(Objects.requireNonNull(m.get(reso.slot)).first);
            mMap.getOverlays().remove(Objects.requireNonNull(m.get(reso.slot)).second);
            recyclePolyline(Objects.requireNonNull(m.get(reso.slot)).second);
            m.remove(reso.slot);
            mResonatorToPortalSlotLookup.remove(reso.id);
        }

        // Update markers map
        HashMap<Integer, Pair<GroundOverlay, Polyline>> markers;
        if (mResonatorMarkers.containsKey(portal.getEntityGuid())) {
            markers = mResonatorMarkers.get(portal.getEntityGuid());
        } else {
            markers = new HashMap<>();
            mResonatorMarkers.put(portal.getEntityGuid(), markers);
        }
        assert markers != null;

        // Calculate positions
        GeoPoint resoPos = reso.getResoCoordinates();
        GeoPoint topLeft = resoPos.destinationPoint(LINKED_RESO_SCALE, TOP_LEFT_ANGLE);
        GeoPoint bottomRight = resoPos.destinationPoint(LINKED_RESO_SCALE, BOTTOM_RIGHT_ANGLE);

        // Create actual reso marker
        GroundOverlay marker = getOverlay();
        marker.setPosition(topLeft, bottomRight);
        marker.setImage(BitmapFactory.decodeResource(getResources(), getImageForResoLevel(reso.level)));

        // Connect reso to portal with line
        Polyline line = getPolyline(mMap);
        line.setPoints(Arrays.asList(portal.getPortalLocation().getLatLng(), resoPos));
        Paint paint = new Paint();
        paint.setColor(getColorFromResources(getResources(), getLevelColor(reso.level)));
        paint.setStrokeWidth(0.33f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.setBlendMode(BlendMode.HARD_LIGHT);
        }
        line.getOutlinePaint().set(paint);

        mMap.getOverlays().add(line);
        mMap.getOverlays().add(marker);
        markers.put(reso.slot, new Pair<>(marker, line));
        mResonatorToPortalSlotLookup.put(reso.id, new Pair<>(portal.getEntityGuid(), reso.slot));
    }


    private void drawLink(final GameEntityLink link) {
        if (mMap != null) {
            // only update if line has not yet been added
            if (!mLines.containsKey(link.getEntityGuid())) {
                final net.opengress.slimgress.api.Common.Location origin = link.getLinkOriginLocation();
                final net.opengress.slimgress.api.Common.Location dest = link.getLinkDestinationLocation();

                // TODO: decay link per portal health
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(() -> {
                    Team team = link.getLinkControllingTeam();
                    int color = 0xff000000 + team.getColour(); // adding opacity

                    Polyline line = new Polyline(mMap);
                    line.addPoint(origin.getLatLng());
                    line.addPoint(dest.getLatLng());
                    Paint paint = new Paint();
                    paint.setColor(color);
                    paint.setStrokeWidth(2);
                    line.getOutlinePaint().set(paint);
//                        line.zIndex(2);
                    line.setOnClickListener((poly, mapView, eventPos) -> false);

                    mMap.getOverlays().add(line);
                    mLines.put(link.getEntityGuid(), line);
                });
            }
        }
    }

    private void drawField(final GameEntityControlField field) {
        if (mMap != null) {
            // only update if line has not yet been added
            if (!mPolygons.containsKey(field.getEntityGuid())) {
                final net.opengress.slimgress.api.Common.Location vA = field.getFieldVertexA().getPortalLocation();
                final net.opengress.slimgress.api.Common.Location vB = field.getFieldVertexB().getPortalLocation();
                final net.opengress.slimgress.api.Common.Location vC = field.getFieldVertexC().getPortalLocation();

                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(() -> {

                    // todo: decay field per portal health
                    Team team = field.getFieldControllingTeam();
                    int color = 0x32000000 + team.getColour(); // adding alpha

                    Polygon polygon = new Polygon(mMap);
                    polygon.addPoint(new GeoPoint(vA.getLatLng()));
                    polygon.addPoint(new GeoPoint(vB.getLatLng()));
                    polygon.addPoint(new GeoPoint(vC.getLatLng()));
                    Paint paint = new Paint();
                    paint.setColor(color);
                    paint.setStrokeWidth(0);
                    polygon.getOutlinePaint().set(paint);
//                        polygon.zIndex(1);
                    polygon.setOnClickListener((poly, mapView, eventPos) -> false);

                    mMap.getOverlays().add(polygon);
                    mPolygons.put(field.getEntityGuid(), polygon);
                });
            }
        }
    }

    private void drawItem(GameEntityItem entity) {
        if (mMap != null) {
            // if marker already exists, remove it so it can be updated
            String guid = entity.getEntityGuid();
            if (mItemMarkers.containsKey(guid)) {
                mMap.getOverlays().remove(mItemMarkers.get(guid));
                mItemMarkers.remove(guid);
            }
            final net.opengress.slimgress.api.Common.Location location = entity.getItem().getItemLocation();

            ActivityMain activity = (ActivityMain) getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                Bitmap portalIcon = mIcons.get("actionradius");
                switch (entity.getItem().getItemType()) {
                    case ModForceAmp -> portalIcon = mIcons.get("force_amp");
                    case ModHeatsink -> {
                        switch (entity.getItem().getItemRarity()) {
                            case Common -> portalIcon = mIcons.get("heatsink_common");
                            case Rare -> portalIcon = mIcons.get("heatsink_rare");
                            case VeryRare -> portalIcon = mIcons.get("heatsink_very_rare");
                        }
                    }
                    case ModLinkAmp -> {
                        switch (entity.getItem().getItemRarity()) {
                            case Rare -> portalIcon = mIcons.get("linkamp_rare");
                            case VeryRare -> portalIcon = mIcons.get("linkamp_very_rare");
                        }
                    }
                    case ModMultihack -> {
                        switch (entity.getItem().getItemRarity()) {
                            case Common -> portalIcon = mIcons.get("multihack_common");
                            case Rare -> portalIcon = mIcons.get("multihack_rare");
                            case VeryRare -> portalIcon = mIcons.get("multihack_very_rare");
                        }
                    }
                    case ModShield -> {
                        switch (entity.getItem().getItemRarity()) {
                            case Common -> portalIcon = mIcons.get("shield_common");
                            case Rare -> portalIcon = mIcons.get("shield_rare");
                            case VeryRare -> portalIcon = mIcons.get("shield_very_rare");
                        }
                    }
                    case ModTurret -> portalIcon = mIcons.get("turret");
                    case PortalKey -> portalIcon = mIcons.get("portalkey");
                    case PowerCube ->
                            portalIcon = mIcons.get("c" + entity.getItem().getItemLevel());
                    case Resonator ->
                            portalIcon = mIcons.get("r" + entity.getItem().getItemLevel());
                    case FlipCard -> {
                        switch (((ItemFlipCard) entity.getItem()).getFlipCardType()) {
                            case Jarvis -> portalIcon = mIcons.get("jarvis");
                            case Ada -> portalIcon = mIcons.get("ada");
                        }
                    }
                    case WeaponXMP ->
                            portalIcon = mIcons.get("x" + entity.getItem().getItemLevel());
                    case WeaponUltraStrike ->
                            portalIcon = mIcons.get("u" + entity.getItem().getItemLevel());
                    case Capsule -> portalIcon = mIcons.get("capsule");
                    case PlayerPowerup -> portalIcon = mIcons.get("dap");
                }

                GroundOverlay marker = new GroundOverlay() {
                    public boolean touchedBy(@NonNull final MotionEvent event) {
                        GeoPoint tappedGeoPoint = (GeoPoint) mMap.getProjection().fromPixels((int) event.getX(), (int) event.getY());
                        return getBounds().contains(tappedGeoPoint);
                    }

                    @Override
                    public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                        if (touchedBy(e)) {
                            mGame.intPickupItem(entity.getEntityGuid(), new Handler(msg -> {
                                var data = msg.getData();
                                String error = getErrorStringFromAPI(data);
                                if (error != null && !error.isEmpty()) {
                                    SlimgressApplication.postPlainCommsMessage(error);
                                } else {
                                    SlimgressApplication.postPlainCommsMessage("Picked up a " + msg.getData().getString("description"));
                                }
                                return false;
                            }));
                            return true;
                        }
                        return false;
                    }
                };
                marker.setPosition(location.getLatLng().destinationPoint(5, TOP_LEFT_ANGLE), location.getLatLng().destinationPoint(5, BOTTOM_RIGHT_ANGLE));
                marker.setImage(portalIcon);

                mMap.getOverlays().add(marker);
                mItemMarkers.put(guid, marker);
            });

        }
    }

    public String getStringSafely(int id) {
        if (getContext() == null) {
            return "";
        }
        return getString(id);
    }

    public String getQuickMessage() {
        // not essential so not guaranteed to do anything useful
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }
        return ((TextView) activity.findViewById(R.id.quickMessage)).getText().toString();
    }

    public void displayQuickMessage(String message) {
        // not essential so not guaranteed to do anything useful
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        TextView quickMessageView = activity.findViewById(R.id.quickMessage);
        quickMessageView.setText(message);
        quickMessageView.setVisibility(View.VISIBLE);
    }

    public void hideQuickMessage() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.findViewById(R.id.quickMessage).setVisibility(View.GONE);
    }

    public void setQuickMessageTimeout() {
        // not essential so not guaranteed to do anything useful
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> activity.runOnUiThread(() -> activity.findViewById(R.id.quickMessage).setVisibility(View.GONE)), 3000);
    }

    public MapView getMap() {
        return mMap;
    }

    public void fireBurster(int radius) {
        new AnimatedCircleOverlay(mMap, radius, 100).start();
        if (getActivity() != null) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    private void onPortalActivityResult(ActivityResult result) {

        if (result.getResultCode() == Activity.RESULT_OK) {
            var data = result.getData();
            if (mGame.getLocation() != null) {
                final Handler uiHandler = new Handler();
                uiHandler.post(() -> {
                    // guard against scanning too fast if request fails
                    mLastScan = System.currentTimeMillis() + mMinUpdateIntervalMS;
                    updateWorld();
                });

            }

            if (data == null) {
                return;
            }

            Bundle hackResultBundle = data.getBundleExtra("result");
            assert hackResultBundle != null;
            @SuppressWarnings("unchecked")
            HashMap<String, Integer> items = (HashMap<String, Integer>) hackResultBundle.getSerializable("items");
            @SuppressWarnings("unchecked")
            HashMap<String, Integer> bonusItems = (HashMap<String, Integer>) hackResultBundle.getSerializable("bonusItems");
            String error = hackResultBundle.getString("error");

            if (error != null) {
                int portalLevel = mGame.getCurrentPortal().getPortalLevel();
                Team portalTeam = mGame.getCurrentPortal().getPortalTeam();
                if (portalTeam.toString().equalsIgnoreCase(mGame.getAgent().getTeam().toString())) {
                    mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getPortalHackFriendlyCostByLevel().get(portalLevel));
                } else if (portalTeam.toString().equalsIgnoreCase("neutral")) {
                    mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getPortalHackNeutralCostByLevel().get(portalLevel));
                } else {
                    mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getPortalHackEnemyCostByLevel().get(portalLevel));
                }

                var main = ((ActivityMain) getActivity());
                if (main != null) {
                    main.updateAgent();
                }

                DialogHackResult newDialog = new DialogHackResult(getContext());
//                newDialog.setTitle("");
                newDialog.setMessage(error);
                newDialog.show();
            } else {
                if (items != null) {
                    DialogHackResult newDialog = new DialogHackResult(getContext());
                    newDialog.setTitle("Acquired items");
                    newDialog.setItems(items);
                    newDialog.show();

                    if (bonusItems != null) {
                        newDialog.setOnDismissListener(dialog -> {
                            DialogHackResult newDialog1 = new DialogHackResult(getContext());
                            newDialog1.setTitle("Bonus items");
                            newDialog1.setItems(bonusItems);
                            newDialog1.show();
                        });
                    }

                } else if (bonusItems != null) {
                    DialogHackResult newDialog = new DialogHackResult(getContext());
                    newDialog.setTitle("Bonus items");
                    newDialog.setItems(bonusItems);
                    newDialog.show();
                }

            }
        }
        if (getActivity() != null) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    @SuppressLint("DefaultLocale")
    public void displayDamage(int damageAmount, String targetGuid, int targetSlot, boolean criticalHit) {
        var marker = mPortalMarkers.get(targetGuid);
        if (marker == null) {
            return;
        }

        var actualPortal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(targetGuid);
        assert actualPortal != null;
        var actualReso = actualPortal.getPortalResonator(targetSlot);
        // FIXME reso is deleted from gameWorld before damage is displayed, so we can't display damage
        if (actualReso != null) {
            // note that it is allowed to be more than 100%
            int percentage = (int) ((float) damageAmount / (float) actualReso.getMaxEnergy() * 100);

            var location = actualReso.getResoCoordinates();
            new TextOverlay(mMap, location, String.format("%d%%", percentage) + (criticalHit ? "!" : ""), 0xCCF8C03E);
        }
    }

    @SuppressLint("DefaultLocale")
    public void displayPlayerDamage(int amount, String attackerGuid) {

        // set up
        int colour = 0xCCF83030;

        GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(attackerGuid);
        assert portal != null;

        GeoPoint playerLocation = mGame.getLocation().getLatLng();

        // create the zap line for player damage
        Polyline line = new Polyline();
        line.setPoints(Arrays.asList(portal.getPortalLocation().getLatLng(), playerLocation));
        Paint paint = new Paint();
        paint.setColor(colour);
        paint.setStrokeWidth(5f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.setBlendMode(BlendMode.SCREEN);
        }
        line.getOutlinePaint().set(paint);

        mMap.getOverlays().add(line);
        // let it delete itself
        new Handler(Looper.getMainLooper()).postDelayed(() -> mMap.getOverlays().remove(line), 2000);

        // now write up the text, but only if the damage was significant

        // max energy or just regular energy?
        // it is once again allowed to be ore than 100%
        int percentage = (int) ((float) amount / (float) mGame.getAgent().getEnergyMax() * 100);
        if (percentage < 1) {
            return;
        }

        new TextOverlay(mMap, playerLocation.destinationPoint(10, 0), String.format("%d%%", percentage), colour);
        if (getActivity() != null) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    private Bitmap createDrawableFromView(Context context, View view) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
        view.setLayoutParams(new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT));
        view.measure(displayMetrics.widthPixels, displayMetrics.heightPixels);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.buildDrawingCache();
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight() + 30, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        return bitmap;
    }
}

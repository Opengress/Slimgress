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
import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getImageForResoLevel;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
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
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.XMParticle;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityControlField;
import net.opengress.slimgress.api.GameEntity.GameEntityLink;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    final ScannerKnobs mScannerKnobs = mGame.getKnobs().getScannerKnobs();
    private final int mActionRadiusM = mScannerKnobs.getActionRadiusM();
    private final int mUpdateIntervalMS = mScannerKnobs.getUpdateIntervalMS();
    private final int mMinUpdateIntervalMS = mScannerKnobs.getMinUpdateIntervalMS();
    private final int mUpdateDistanceM = mScannerKnobs.getUpdateDistanceM();


    // ===========================================================
    // Map basics
    // ===========================================================
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

    private ActivityResultLauncher<Intent> mPortalActivityResultLauncher;


    // ===========================================================
    // Fields and permissions
    // ===========================================================
    private SharedPreferences mPrefs;

    private GeoPoint mCurrentLocation = null;
    private static final int RECORD_REQUEST_CODE = 101;
    private Date mLastScan = null;
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

    // device sensor manager
    private SensorManager mSensorManager;
    private float mBearing = 0;
    private Sensor mRotationVectorSensor;

    private final int MAP_ROTATION_ARBITRARY = 2;
    private final int MAP_ROTATION_FLOATING = 3;
    private int CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;

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

        }
        if (CURRENT_MAP_ORIENTATION_SCHEME == MAP_ROTATION_FLOATING) {
            mMap.setMapOrientation(-mBearing);
        }
        drawPlayerCursor(location, mBearing);
    }

    public void drawPlayerCursor(GeoPoint location, float bearing) {
        mMap.getOverlayManager().remove(mPlayerCursor);

        /*
         because we retain the original image size, the rotated image gets scaled.
         this means that rotating the cursor changes its size.
         we need to do a couple of things when we fix this:
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
        Bitmap cursor = mIcons.get("playercursor");
        Matrix matrix = new Matrix();
        matrix.postRotate(Math.round(bearing));
        assert cursor != null;
        mPlayerCursor.setImage(Bitmap.createBitmap(cursor, 0, 0, cursor.getWidth(), cursor.getHeight(), matrix, true));

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

        mMap.getOverlayManager().remove(mActionRadius);
        if (mRotationVectorSensor == null) {
            drawPlayerCursor(currentLocation, bearing);
        }
        mActionRadius.setPosition(currentLocation.destinationPoint(56.57, TOP_LEFT_ANGLE), currentLocation.destinationPoint(56.57, BOTTOM_RIGHT_ANGLE));
        mActionRadius.setImage(mIcons.get("actionradius"));
        mMap.getOverlayManager().add(mActionRadius);

        long now = new Date().getTime();

        if (mLastScan == null ||
                mLastLocation == null ||
                (now - mLastScan.getTime() >= mUpdateIntervalMS) ||
                (now - mLastScan.getTime() >= mMinUpdateIntervalMS && mLastLocation.distanceToAsDouble(currentLocation) >= mUpdateDistanceM)
        ) {
            if (mGame.getLocation() != null) {
                final Handler uiHandler = new Handler();
                uiHandler.post(() -> {
                    // guard against scanning too fast if request fails
                    mLastScan = new Date(System.currentTimeMillis() + mMinUpdateIntervalMS);
                    updateWorld();
                });

            }
        }
        mLastLocationAcquired = new Date();
        mLastLocation = currentLocation;

        setLocationInaccurate(false);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) requireActivity().getSystemService(SENSOR_SERVICE);
        mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);

        mApp.getDeletedEntityGuidsModel().getDeletedEntityGuids().observe(this, this::onReceiveDeletedEntityGuids);
        mActionRadius.setTransparency(0.5f);

        mPortalActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onPortalActivityResult
        );

    }

    public void onReceiveDeletedEntityGuids(List<String> deletedEntityGuids) {
        for (String guid : deletedEntityGuids) {

            // for XM particles
            if (guid.contains(".")) {
                long particle = Long.parseLong(guid.substring(0, 16), 16);
                if (mXMMarkers.containsKey(particle)) {
                    mMap.getOverlays().remove(mXMMarkers.get(particle));
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
                }
                mResonatorToPortalSlotLookup.remove(guid);
                Objects.requireNonNull(mResonatorMarkers.get(portal)).remove(slot);
                continue;
            }

            // for links
            if (mLines.containsKey(guid)) {
                mMap.getOverlays().remove(mLines.get(guid));
                mLines.remove(guid);
                continue;
            }

            // for fields
            if (mPolygons.containsKey(guid)) {
                mMap.getOverlays().remove(mPolygons.get(guid));
                mPolygons.remove(guid);
            }

            // for dropped items .... not done yet...

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
            mIcons.put(team, ViewHelpers.getTintedImage("portalTexture_NEUTRAL.png", 0xff000000 + Objects.requireNonNull(teams.get(team)).getColour(), assetManager));
        }

        mIcons.put("particle", getBitmapFromAsset("particle.png", assetManager));
        mIcons.put("actionradius", getBitmapFromAsset("actionradius.png", assetManager));
        mIcons.put("playercursor", ViewHelpers.getTintedImage("playercursor.png", 0xff000000 + Objects.requireNonNull(mGame.getAgent().getTeam()).getColour(), assetManager));
    }

    @SuppressLint("DefaultLocale")
    private synchronized void updateWorld() {
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

            return true;
        });

        // FIXME: they're probably being drawn and slurped at the same time,
        //  in which case user will see ghost XM particles they've just slurped
        //  ... also, can we get particles that we passed over between scans? probably not.
        //  maybe with slurp?
        setSlurpableXMParticles();
        if (getActivity() != null) {
            ((ActivityMain) getActivity()).updateAgent();
        }

        // get objects (on new thread)
        new Thread(() -> mGame.intGetObjectsInCells(mGame.getLocation(), resultHandler)).start();
        final Handler commsHandler = new Handler();
        new Thread(() -> mGame.intLoadCommunication(50, false, commsHandler)).start();
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

                uiHandler.post(() -> {
                    assert entity != null;
                    if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal) {
                        drawPortal((GameEntityPortal) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Link) {
                        drawLink((GameEntityLink) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField) {
                        drawField((GameEntityControlField) entity);
                    }
                });
            }

            mLastScan = new Date(System.currentTimeMillis() + mMinUpdateIntervalMS);
        }).start();
    }

    private void setSlurpableXMParticles() {
        if (mGame.getAgent() == null) {
            return;
        }
        // FIXME maybe don't try to slurp particles that aren't needed to fill the tank
        //  -- note that we may need to sort the particles and pick out the optimal configuration
        //  -- also note that if we're really cheeky we may want/be able to do that serverside
        Map<Long, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        Set<Long> keys = xmParticles.keySet();
        ArrayList<String> slurpableParticles = new ArrayList<>();

        int oldXM = mGame.getAgent().getEnergy();
        int maxXM = mGame.getAgent().getEnergyMax();
        int newXM = 0;

        for (Long key : keys) {
            XMParticle particle = xmParticles.get(key);

            // FIXME this is honestly the worst imaginable solution, but for now it's what i have...
            assert particle != null;
            final net.opengress.slimgress.api.Common.Location location = particle.getCellLocation();
            if (location.getLatLng().distanceToAsDouble(mGame.getLocation().getLatLng()) < mActionRadiusM) {
                if (oldXM + newXM >= maxXM) {
                    break;
                }
                slurpableParticles.add(particle.getGuid());
                newXM += particle.getAmount();
                var marker = mXMMarkers.remove(key);
                mMap.getOverlays().remove(marker);
            }
        }

        mGame.setSlurpableXMParticles(slurpableParticles);
        mGame.getAgent().addEnergy(newXM);

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

                    GroundOverlay marker = new GroundOverlay();
                    marker.setPosition(location.getLatLng().destinationPoint(10, TOP_LEFT_ANGLE), location.getLatLng().destinationPoint(10, BOTTOM_RIGHT_ANGLE));
                    marker.setImage(particleIcon);

                    mMap.getOverlays().add(marker);
                    mXMMarkers.put(particle.getCellId(), marker);
                });
            }
        }
    }

    private void drawPortal(@NonNull final GameEntityPortal portal) {
        final Team team = portal.getPortalTeam();
        if (mMap != null) {
            // if marker already exists, remove it so it can be updated
            String guid = portal.getEntityGuid();
            if (mPortalMarkers.containsKey(guid)) {
                mMap.getOverlays().remove(mPortalMarkers.get(guid));
                mPortalMarkers.remove(guid);
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
        mMap.invalidate();
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
            mMap.getOverlays().remove(Objects.requireNonNull(m.get(reso.slot)).second);
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
        GroundOverlay marker = new GroundOverlay();
        marker.setPosition(topLeft, bottomRight);
        marker.setImage(BitmapFactory.decodeResource(getResources(), getImageForResoLevel(reso.level)));

        // Connect reso to portal with line
        Polyline line = new Polyline();
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
    }

    private void onPortalActivityResult(ActivityResult result) {

        if (result.getResultCode() == Activity.RESULT_OK) {
            var data = result.getData();
            if (mGame.getLocation() != null) {
                final Handler uiHandler = new Handler();
                uiHandler.post(() -> {
                    // guard against scanning too fast if request fails
                    mLastScan = new Date(System.currentTimeMillis() + mMinUpdateIntervalMS);
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

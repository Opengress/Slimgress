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
import static net.opengress.slimgress.API.Item.ItemBase.ItemType.PortalKey;
import static net.opengress.slimgress.ViewHelpers.getBitmapFromAsset;

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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.opengress.slimgress.API.Common.Team;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.XMParticle;
import net.opengress.slimgress.API.GameEntity.GameEntityBase;
import net.opengress.slimgress.API.GameEntity.GameEntityControlField;
import net.opengress.slimgress.API.GameEntity.GameEntityLink;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemPortalKey;
import net.opengress.slimgress.API.Knobs.ScannerKnobs;
import net.opengress.slimgress.API.Knobs.TeamKnobs;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ScannerView extends Fragment implements SensorEventListener, LocationListener {
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private MapView mMap = null;

    // ===========================================================
    // Knobs quick reference
    // ===========================================================
    final ScannerKnobs mScannerKnobs = mGame.getKnobs().getScannerKnobs();
    private final int mActionRadiusM = mScannerKnobs.getActionRadiusM();
    private final int mUpdateIntervalMS = mScannerKnobs.getUpdateIntervalMS();
    private final int mMinUpdateIntervalMS = mScannerKnobs.getMinUpdateIntervalMS();
    private final int mUpdateDistanceM = mScannerKnobs.getUpdateDistanceM();


    private final HashMap<String, Bitmap> mIcons = new HashMap<>();
    private final HashMap<String, GroundOverlay> mPortalMarkers = new HashMap<>();
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
    private final GroundOverlay mActionRadius = new GroundOverlay();
    private final GroundOverlay mPlayerCursor = new GroundOverlay();


    // ===========================================================
    // Other (location)
    // ===========================================================
    private LocationManager mLocationManager = null;
    private MyLocationNewOverlay mLocationOverlay = null;

    // device sensor manager
    private SensorManager mSensorManager;
    private float mBearing = 0;
    private Sensor mRotationVectorSensor;

    private final int MAP_ROTATION_ARBITRARY = 2;
    private final int MAP_ROTATION_FLOATING = 3;
    private int CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;

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
        mPlayerCursor.setPosition(location.destinationPoint(15, 315), location.destinationPoint(15, 135));
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
     * {@link android.hardware.SensorManager SensorManager} for details.
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
        var loc = new net.opengress.slimgress.API.Common.Location(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
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
        mActionRadius.setPosition(currentLocation.destinationPoint(56.57, 315), currentLocation.destinationPoint(56.57, 135));
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
                    updateWorld(uiHandler);
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

        mPortalActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        var data = result.getData();
                        if (mGame.getLocation() != null) {
                            final Handler uiHandler = new Handler();
                            uiHandler.post(() -> {
                                // guard against scanning too fast if request fails
                                mLastScan = new Date(System.currentTimeMillis() + mMinUpdateIntervalMS);
                                updateWorld(uiHandler);
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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // allows map tiles to be cached in SQLite so map draws properly
        Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences(requireActivity().getApplicationInfo().packageName, Context.MODE_PRIVATE));

        // set up map tile source before creating map, so we don't download wrong tiles wastefully
        final ITileSource tileSource = new XYTileSource("CartoDB Dark Matter", 3, 18, 256, ".png",
                new String[]{"https://c.basemaps.cartocdn.com/dark_nolabels/"});
        final MapTileProviderBasic tileProvider = new MapTileProviderBasic(mApp.getApplicationContext(), tileSource);

        // Note! we are programmatically construction the map view
        // be sure to handle application lifecycle correct (see onPause)
        mMap = new MapView(inflater.getContext(), tileProvider) {
            private double mCurrAngle = 0;
            private double mPrevAngle = 0;

            @Override
            public boolean onTouchEvent(MotionEvent event) {

                if (event.getPointerCount() == 1) {
                    final float xc = (float) getWidth() / 2;
                    final float yc = (float) getHeight() / 2;
                    final float x = event.getX();
                    final float y = event.getY();
                    double angrad = Math.atan2(x - xc, yc - y);

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN -> mCurrAngle = Math.toDegrees(angrad);
                        case MotionEvent.ACTION_MOVE -> {
                            CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;
                            mPrevAngle = mCurrAngle;
                            mCurrAngle = Math.toDegrees(angrad);
                            setMapOrientation(getMapOrientation() - (float) (mPrevAngle - mCurrAngle));
                            return true;
                        }
                        case MotionEvent.ACTION_UP -> mPrevAngle = mCurrAngle = 0;
                    }
                } else {
                    // i assume you're doing a pinch zoom/rotate
                    CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;
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

        mPrefs = context.getSharedPreferences(Constants.PREFS_OSM_NAME, Context.MODE_PRIVATE);


        Configuration.getInstance().setUserAgentValue("Slimgress/Openflux (OSMDroid)");

        mMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        //On screen compass
        CompassOverlay mCompassOverlay = getCompassOverlay(context);
        mMap.getOverlays().add(mCompassOverlay);

        //scales tiles to the current screen's DPI, helps with readability of labels
        mMap.setTilesScaledToDpi(true);

        //the rest of this is restoring the last map location the user looked at
        final float zoomLevel = mPrefs.getFloat(Constants.PREFS_OSM_ZOOM_LEVEL_DOUBLE, 18);
        mMap.getController().setZoom(zoomLevel);
        mMap.setMapOrientation(0, false);
        final String latitudeString = mPrefs.getString(Constants.PREFS_OSM_LATITUDE_STRING, "1.0");
        final String longitudeString = mPrefs.getString(Constants.PREFS_OSM_LONGITUDE_STRING, "1.0");
        final double latitude = Double.parseDouble(latitudeString);
        final double longitude = Double.parseDouble(longitudeString);
        mMap.setExpectedCenter(new GeoPoint(latitude, longitude));

//        setHasOptionsMenu(false);

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
                    Log.d("Scanner", "Got tap!");
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
        edit.putString(Constants.PREFS_OSM_LATITUDE_STRING, String.valueOf(mMap.getMapCenter().getLatitude()));
        edit.putString(Constants.PREFS_OSM_LONGITUDE_STRING, String.valueOf(mMap.getMapCenter().getLongitude()));
        edit.putFloat(Constants.PREFS_OSM_ZOOM_LEVEL_DOUBLE, (float) mMap.getZoomLevelDouble());
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

        if (mLastLocationAcquired == null || mLastLocationAcquired.before(new Date(System.currentTimeMillis() - mUpdateIntervalMS))) {
            setLocationInaccurate(true);
        } else if (mLastLocationAcquired.before(new Date(System.currentTimeMillis() - mMinUpdateIntervalMS))) {
            // might be pointing the wrong way til next location update but that's ok
            displayMyCurrentLocationOverlay(mCurrentLocation, 0);
        }

        if (mRotationVectorSensor != null) {
            mSensorManager.registerListener(this, mRotationVectorSensor,
                    SensorManager.SENSOR_DELAY_GAME);
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
    private synchronized void updateWorld(final Handler uiHandler) {
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
                mGame.intGetModifiedEntitiesByGuid(portalGUIDs.toArray(new String[0]), new Handler(m -> true));
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

            // draw xm particles
            drawXMParticles();

            new Thread(() -> {
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
                Log.d("ScannerView", "world updated");
                displayQuickMessage(getStringSafely(R.string.scan_complete));
                setQuickMessageTimeout();
            }).start();

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
            final net.opengress.slimgress.API.Common.Location location = particle.getCellLocation();
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

                final net.opengress.slimgress.API.Common.Location location = particle.getCellLocation();

                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(() -> {
                    Bitmap portalIcon;
                    // TODO: make portal marker display portal health/deployment info (opacity x white, use shield image etc)
                    // i would also like to draw the resonators around it, but i'm not sure that that would be practical with osmdroid
                    // ... maybe i can at least write the portal level on the portal, like in iitc
                    // it's quite possible that resonators can live in a separate Hash of markers,
                    //   as long as the guids are stored with the portal info
                    portalIcon = mIcons.get("particle");

                    GroundOverlay marker = new GroundOverlay();
                    marker.setPosition(location.getLatLng().destinationPoint(25, 315), location.getLatLng().destinationPoint(25, 135));
                    marker.setImage(portalIcon);

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
            final net.opengress.slimgress.API.Common.Location location = portal.getPortalLocation();

            Activity activity = getActivity();
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
                        if (touchedBy(e)) {
                            Intent myIntent = new Intent(getContext(), ActivityPortal.class);
                            mGame.setCurrentPortal(portal);
                            mPortalActivityResultLauncher.launch(myIntent);
                            return true;
                        }
                        return false;
                    }
                };
                marker.setPosition(location.getLatLng().destinationPoint(20, 315), location.getLatLng().destinationPoint(20, 135));
                marker.setImage(portalIcon);

                mMap.getOverlays().add(marker);
                mPortalMarkers.put(guid, marker);
            });

        }
    }

    private void drawLink(final GameEntityLink link) {
        if (mMap != null) {
            // only update if line has not yet been added
            if (!mLines.containsKey(link.getEntityGuid())) {
                final net.opengress.slimgress.API.Common.Location origin = link.getLinkOriginLocation();
                final net.opengress.slimgress.API.Common.Location dest = link.getLinkDestinationLocation();

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
                final net.opengress.slimgress.API.Common.Location vA = field.getFieldVertexA().getPortalLocation();
                final net.opengress.slimgress.API.Common.Location vB = field.getFieldVertexB().getPortalLocation();
                final net.opengress.slimgress.API.Common.Location vC = field.getFieldVertexC().getPortalLocation();

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

}

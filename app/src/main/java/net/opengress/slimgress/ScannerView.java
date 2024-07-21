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

import static net.opengress.slimgress.ViewHelpers.getBitmapFromAsset;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Matrix;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import net.opengress.slimgress.API.Common.Team;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.XMParticle;
import net.opengress.slimgress.API.GameEntity.GameEntityBase;
import net.opengress.slimgress.API.GameEntity.GameEntityControlField;
import net.opengress.slimgress.API.GameEntity.GameEntityLink;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
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
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class ScannerView extends Fragment implements SensorEventListener {
    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final ScannerKnobs mScannerKnobs = mGame.getKnobs().getScannerKnobs();
    private MapView mMap = null;

    private final HashMap<String, Bitmap> mIcons = new HashMap<>();
    private HashMap<String, GroundOverlay> mMarkers = null;
    private HashMap<Long, GroundOverlay> mXMMarkers = null;
    private HashMap<String, Polyline> mLines = null;
    private HashMap<String, Polygon> mPolygons = null;

    private ActivityResultLauncher<Intent> mPortalActivityResultLauncher;

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String PREFS_NAME = "org.andnav.osm.prefs";
    private static final String PREFS_LATITUDE_STRING = "latitudeString";
    private static final String PREFS_LONGITUDE_STRING = "longitudeString";
    private static final String PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble";

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
    // Knobs quick reference
    // ===========================================================
    private final int mActionRadiusM = mScannerKnobs.getActionRadiusM();
    private final int mUpdateIntervalMS = mScannerKnobs.getUpdateIntervalMS();
    private final int mMinUpdateIntervalMS = mScannerKnobs.getMinUpdateIntervalMS();
    private final int mUpdateDistanceM = mScannerKnobs.getUpdateDistanceM();

    // ===========================================================
    // Other (location)
    // ===========================================================
    private MyLocationListener mLocationListener = null;
    private LocationManager mLocationManager = null;
    private MyLocationNewOverlay mLocationOverlay = null;

    // device sensor manager
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;
    private float[] mGravity;
    private float[] mGeomagnetic;
    private float mBearing = 0;

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

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;

        if (mGravity != null && mGeomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                // FIXME somewhere in this screen we must make an adjustment from OPS
                float z = (float) (Math.toDegrees(orientation[0]) + 360) % 360;
                float x = (float) (Math.toDegrees(orientation[1]) + 360) % 360;
                /*
                If x is 0, y should give correct info. but i can't get it from [2] so idk.
                Then again, who holds their phone perfectly vertically?
                 */
                if (Math.abs(x) > 0) {
                    z = (z + 180) % 360;
                }
                mBearing = z;
            }
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


    public class MyLocationListener implements LocationListener {

        public void onLocationChanged(@NonNull Location location) {
            mCurrentLocation = new GeoPoint(location);
            mGame.updateLocation(new net.opengress.slimgress.API.Common.Location(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
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
    }

    private void displayMyCurrentLocationOverlay(GeoPoint currentLocation, float bearing) {

        mMap.getOverlayManager().remove(mActionRadius);
        if (mAccelerometer == null && mMagnetometer == null) {
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

        // TODO test this: lock scroll/pan so that user can't pan/zoom away
//        mMap.setScrollableAreaLimitDouble(mMap.getBoundingBox());

        setLocationInaccurate(false);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSensorManager = (SensorManager) requireActivity().getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mGame.getWorld().connectSignalDeletedEntities(this::onReceiveDeletedEntityGuids);
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
                            // FIXME magic number and possibly (hopefully) handled by server
                            mGame.getAgent().setEnergy(mGame.getAgent().getEnergy() - 50);
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
            }

            // for portals
            if (mMarkers.containsKey(guid)) {
                mMap.getOverlays().remove(mMarkers.get(guid));
                mMarkers.remove(guid);
            }

            // for links
            if (mLines.containsKey(guid)) {
                mMap.getOverlays().remove(mLines.get(guid));
                mLines.remove(guid);
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
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitDiskReads().permitDiskWrites().build();
        StrictMode.setThreadPolicy(policy);
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()));

        // set up map tile source before creating map, so we don't download wrong tiles wastefully
        final ITileSource tileSource = new XYTileSource("CartoDB Dark Matter", 3, 18, 256, ".png",
                new String[]{"https://c.basemaps.cartocdn.com/dark_nolabels/"});
        final MapTileProviderBasic tileProvider = new MapTileProviderBasic(mApp.getApplicationContext(), tileSource);

        // Note! we are programmatically construction the map view
        // be sure to handle application lifecycle correct (see onPause)
        mMap = new MapView(inflater.getContext(), tileProvider);
        mMap.getMapOverlay().setLoadingBackgroundColor(Color.BLACK);
        mMap.setDestroyMode(false);
        mMap.setMinZoomLevel(16d);
        mMap.setMaxZoomLevel(22d);
        mMap.setFlingEnabled(false);
        // TODO: rewrite MultiTouchController to NOT change map position on pinch
        //  (if that's the right way)
        mMap.setMultiTouchControls(true);

        loadAssets();

        mMarkers = new HashMap<>();
        mXMMarkers = new HashMap<>();
        mLines = new HashMap<>();
        mPolygons = new HashMap<>();

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

        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);


        Configuration.getInstance().setUserAgentValue("Slimgress/Openflux (OSMDroid)");

        mMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        //On screen compass
        CompassOverlay mCompassOverlay = getCompassOverlay(context);
        mMap.getOverlays().add(mCompassOverlay);


        //support for map rotation
        RotationGestureOverlay mRotationGestureOverlay = new RotationGestureOverlay(mMap);
        mRotationGestureOverlay.setEnabled(true);
        mMap.getOverlays().add(mRotationGestureOverlay);


        //needed for pinch zooms
        mMap.setMultiTouchControls(true);

        //scales tiles to the current screen's DPI, helps with readability of labels
        mMap.setTilesScaledToDpi(true);

        //the rest of this is restoring the last map location the user looked at
        final float zoomLevel = mPrefs.getFloat(PREFS_ZOOM_LEVEL_DOUBLE, 18);
        mMap.getController().setZoom(zoomLevel);
        mMap.setMapOrientation(0, false);
        final String latitudeString = mPrefs.getString(PREFS_LATITUDE_STRING, "1.0");
        final String longitudeString = mPrefs.getString(PREFS_LONGITUDE_STRING, "1.0");
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
                return true;
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
        edit.putString(PREFS_LATITUDE_STRING, String.valueOf(mMap.getMapCenter().getLatitude()));
        edit.putString(PREFS_LONGITUDE_STRING, String.valueOf(mMap.getMapCenter().getLongitude()));
        edit.putFloat(PREFS_ZOOM_LEVEL_DOUBLE, (float) mMap.getZoomLevelDouble());
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

        if (mAccelerometer != null) {
            mSensorManager.registerListener(this, mAccelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
        }
        if (mMagnetometer != null) {
            mSensorManager.registerListener(this, mMagnetometer,
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
        if (mLocationListener == null) {
            mLocationListener = new MyLocationListener();
        }
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) mApp.getSystemService(Context.LOCATION_SERVICE);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
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

    private void setLocationInaccurate(boolean bool) {
        // unresearched workaround for crash on exit
        if (getActivity() == null || getActivity().findViewById(R.id.quickMessage) == null) {
            return;
        }
        // FIXME this is fine, but really the game state needs to know about it.
        //  for example, if i'm about to hack a portal and i switch my GPS off, that shouldn't work!

        // FIXME this MIGHT be able to fire before activity/view exists, need to maybe wrap it up
        if (bool) {
            setMapEnabled(false);
            ((TextView) getActivity().findViewById(R.id.quickMessage)).setText(R.string.location_inaccurate);
            getActivity().findViewById(R.id.quickMessage).setVisibility(View.VISIBLE);
            mMap.getOverlayManager().remove(mActionRadius);
        } else {
            setMapEnabled(true);
            if (((TextView) getActivity().findViewById(R.id.quickMessage)).getText() == requireContext().getResources().getText(R.string.location_inaccurate)) {
                getActivity().findViewById(R.id.quickMessage).setVisibility(View.INVISIBLE);
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

    private synchronized void updateWorld(final Handler uiHandler) {
        // handle interface result (on timer thread)
        final Handler resultHandler = new Handler(msg -> {

            // protect against crash from unclean exit
            if (getActivity() == null) {
                return true;
            }

            if (msg.getData().keySet().contains("Error")) {
                ((TextView) getActivity().findViewById(R.id.quickMessage)).setText(R.string.scan_failed);
                getActivity().findViewById(R.id.quickMessage).setVisibility(View.VISIBLE);
                return true;
            }

            if (((TextView) getActivity().findViewById(R.id.quickMessage)).getText() == requireContext().getResources().getText(R.string.scan_failed)) {
                getActivity().findViewById(R.id.quickMessage).setVisibility(View.INVISIBLE);
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
                        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal)
                            drawPortal((GameEntityPortal) entity);
                        else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Link)
                            drawLink((GameEntityLink) entity);
                        else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField)
                            drawField((GameEntityControlField) entity);
                    });
                }

                mLastScan = new Date();
                Log.d("ScannerView", "world updated");
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
        // FIXME maybe don't try to slurp particles that aren't needed to fill the tank
        //  -- note that we may need to sort the particles and pick out the optimal configuration
        //  -- also note that if we're really cheeky we may want/be able to do that serverside
        Map<Long, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        Set<Long> keys = xmParticles.keySet();
        ArrayList<String> slurpableParticles = new ArrayList<>();

        int newXM = 0;

        for (Long key : keys) {
            XMParticle particle = xmParticles.get(key);

            assert particle != null;
            final net.opengress.slimgress.API.Common.Location location = particle.getCellLocation();
            if (location.getLatLng().distanceToAsDouble(mGame.getLocation().getLatLng()) < mActionRadiusM) {
                slurpableParticles.add(particle.getGuid());
                newXM += particle.getAmount();
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

                requireActivity().runOnUiThread(() -> {
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
            if (mMarkers.containsKey(guid)) {
                mMap.getOverlays().remove(mMarkers.get(guid));
                mMarkers.remove(guid);
            }
            final net.opengress.slimgress.API.Common.Location location = portal.getPortalLocation();

            requireActivity().runOnUiThread(() -> {
                Bitmap portalIcon;
                // TODO: make portal marker display portal health/deployment info (opacity x white, use shield image etc)
                // i would also like to draw the resonators around it, but i'm not sure that that would be practical with osmdroid
                // ... maybe i can at least write the portal level on the portal, like in iitc
                // it's quite possible that resonators can live in a separate Hash of markers,
                //   as long as the guids are stored with the portal info
                portalIcon = mIcons.get(team.toString());

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
                marker.setPosition(location.getLatLng().destinationPoint(25, 315), location.getLatLng().destinationPoint(25, 135));
                marker.setImage(portalIcon);

                mMap.getOverlays().add(marker);
                mMarkers.put(guid, marker);
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
                requireActivity().runOnUiThread(() -> {
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

                requireActivity().runOnUiThread(() -> {

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

}

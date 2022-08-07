/*

 Slimgress: Ingress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>

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
import static android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Matrix;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
import org.osmdroid.views.overlay.CopyrightOverlay;
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
    private HashMap<String, GroundOverlay> mXMMarkers = null;
    private HashMap<String, Polyline> mLines = null;
    private HashMap<String, Polygon> mPolygons = null;

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String PREFS_NAME = "org.andnav.osm.prefs";
    private static final String PREFS_LATITUDE_STRING = "latitudeString";
    private static final String PREFS_LONGITUDE_STRING = "longitudeString";
    private static final String PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble";

    private final int PORTAL_INTENT_CODE = 1;

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
    MyLocationListener mLocationListener = null;
    LocationManager mLocationManager = null;
    MyLocationNewOverlay mLocationOverlay = null;

    // device sensor manager
    private SensorManager mSensorManager;
    private Sensor mOrientationSensor;

    @Override
    public void onSensorChanged(SensorEvent event) {
        float bearing = event.accuracy >= SENSOR_STATUS_ACCURACY_LOW ? event.values[0] : 0;
        var location = mCurrentLocation != null ? mCurrentLocation : (GeoPoint) mMap.getMapCenter();
        mLastLocationAcquired = new Date();
        drawPlayerCursor(location, bearing);
    }

    public void drawPlayerCursor(GeoPoint location, float bearing) {
        mMap.getOverlayManager().remove(mPlayerCursor);

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
     *         {@code SensorManager.SENSOR_STATUS_*}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // unimplemented, don't care
    }


    public class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location location) {
            mCurrentLocation = new GeoPoint(location);
            mGame.updateLocation(new net.opengress.slimgress.API.Common.Location(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
            mLastLocationAcquired = new Date();
            displayMyCurrentLocationOverlay(mCurrentLocation, location.getBearing());
        }

        public void onProviderDisabled(String provider) {
            if (!Objects.equals(provider, "gps")) {
                return;
            }
            setLocationInaccurate(true);
        }

        public void onProviderEnabled(String provider) {
            // don't register location as no longer inaccurate until new location info arrives
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            // probably useless, might not be called above android Q
            // could be interesting for checking that gps fix comes from satellites
        }
    }

    private void displayMyCurrentLocationOverlay(GeoPoint currentLocation, float bearing) {

        mMap.getOverlayManager().remove(mActionRadius);
        if (mOrientationSensor == null) {
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
        mSensorManager = (SensorManager) getActivity().getSystemService(SENSOR_SERVICE);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // allows map tiles to be cached in SQLite so map draws properly
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitDiskReads().permitDiskWrites().build();
        StrictMode.setThreadPolicy(policy);
        Configuration.getInstance().load(getContext(), PreferenceManager.getDefaultSharedPreferences(getContext()));

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
        // (if that's the right way)
        mMap.setMultiTouchControls(true);


        loadAssets();

        mMarkers = new HashMap<>();
        mXMMarkers = new HashMap<>();
        mLines = new HashMap<>();
        mPolygons = new HashMap<>();

        return mMap;
    }

    protected void makeRequest() {
        ActivityCompat.requestPermissions(getActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                RECORD_REQUEST_CODE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Context context = this.getActivity();

        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);


        Configuration.getInstance().setUserAgentValue("Slimgress/Openflux (OSMDroid)");

        mMap.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        //Copyright overlay
        CopyrightOverlay mCopyrightOverlay = new CopyrightOverlay(context);
        //i hate this very much, but it seems as if certain versions of android and/or
        //device types handle screen offsets differently
        mMap.getOverlays().add(mCopyrightOverlay);


        //On screen compass
        CompassOverlay mCompassOverlay = new CompassOverlay(context, new InternalCompassOrientationProvider(context),
                mMap) {
            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                // FIXME set auto/manual rotation (may need to reset to north)
                return true;
            }
        };
        mCompassOverlay.enableCompass();
        mCompassOverlay.setCompassCenter(30, 85);
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

        setHasOptionsMenu(false);

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

        // TODO: 1. use not-deprecated stuff, 2. handle devices with no compass
        // for the system's orientation sensor registered listeners
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        if (mOrientationSensor != null) {
            mSensorManager.registerListener(this, mOrientationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }


        // ===========================================================
        // Other (location)
        // ===========================================================

        if (ContextCompat.checkSelfPermission(getContext(),
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
            mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(getContext()), mMap);
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
            if (((TextView) getActivity().findViewById(R.id.quickMessage)).getText() == getContext().getResources().getText(R.string.location_inaccurate)) {
                getActivity().findViewById(R.id.quickMessage).setVisibility(View.INVISIBLE);
            }
        }
    }

    private void loadAssets() {
        Map<String, TeamKnobs.TeamType> teams = mGame.getKnobs().getTeamKnobs().getTeams();
        for (String team : teams.keySet()) {
            mIcons.put(team, getTintedImage("portalTexture_NEUTRAL.png", 0xff000000 + Objects.requireNonNull(teams.get(team)).getColour()));
        }
        mIcons.put("particle", getBitmapFromAsset("particle.png"));
        mIcons.put("actionradius", getBitmapFromAsset("actionradius.png"));
        mIcons.put("playercursor", getTintedImage("playercursor.png", 0xff000000 + Objects.requireNonNull(mGame.getAgent().getTeam()).getColour()));
    }

    public Bitmap getTintedImage(String image, int color) {
        Bitmap bitmap = getBitmapFromAsset(image);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        assert bitmap != null;
        Bitmap bitmapResult = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapResult);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmapResult;
    }

    // FIXME duplicated in ActivityPortal
    private Bitmap getBitmapFromAsset(String name) {
        AssetManager assetManager = getActivity().getAssets();

        InputStream istr;
        Bitmap bitmap;
        try {
            istr = assetManager.open(name);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            return null;
        }

        return bitmap;
    }

    private synchronized void updateWorld(final Handler uiHandler) {
        // handle interface result (on timer thread)
        final Handler resultHandler = new Handler(msg -> {

            if (msg.getData().keySet().contains("Error")) {
                ((TextView) getActivity().findViewById(R.id.quickMessage)).setText(R.string.scan_failed);
                getActivity().findViewById(R.id.quickMessage).setVisibility(View.VISIBLE);
                return true;
            }

            if (((TextView) getActivity().findViewById(R.id.quickMessage)).getText() == getContext().getResources().getText(R.string.scan_failed)) {
                getActivity().findViewById(R.id.quickMessage).setVisibility(View.INVISIBLE);
            }

            // draw xm particles
            drawXMParticles();
            // FIXME: they're probably being drawn and slurped at the same time,
            //  in which case user will see ghost XM particles they've just slurped
            //  ... also, can we get particles that we passed over between scans? probably not.
            //  maybe with slurp?
            setSlurpableXMParticles();
            ((ActivityMain) getActivity()).updateAgent();

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

        // get objects (on new thread)
        new Thread(() -> mGame.intGetObjectsInCells(mGame.getLocation(), resultHandler)).start();
    }

    private void setSlurpableXMParticles() {
        // FIXME maybe don't try to slurp particles that aren't needed to fill the tank
        //  -- note that we may need to sort the particles and pick out the optimal configuration
        //  -- also note that if we're really cheeky we may want/be able to do that serverside
        Map<String, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        Set<String> keys = xmParticles.keySet();
        ArrayList<String> slurpableParticles = new ArrayList<>();
        for (String key : keys) {
            XMParticle particle = xmParticles.get(key);

            assert particle != null;
            final net.opengress.slimgress.API.Common.Location location = particle.getCellLocation();
            if (location.getLatLng().distanceToAsDouble(mGame.getLocation().getLatLng()) < mActionRadiusM) {
                slurpableParticles.add(key);
            }
        }
        mGame.setSlurpableXMParticles(slurpableParticles);
    }

    private void drawXMParticles() {
        // draw xm particles (as groundoverlays)
        Map<String, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        Set<String> keys = xmParticles.keySet();
        for (String key : keys) {
            XMParticle particle = xmParticles.get(key);

            assert particle != null;
            final net.opengress.slimgress.API.Common.Location location = particle.getCellLocation();

            getActivity().runOnUiThread(() -> getActivity().runOnUiThread(() -> {
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
                mXMMarkers.put(particle.getGuid(), marker);
            }));
        }
    }

    private void drawPortal(@NonNull final GameEntityPortal portal) {
        final Team team = portal.getPortalTeam();
        if (mMap != null) {
            // only update if marker has not yet been added
            if (!mMarkers.containsKey(portal.getEntityGuid())) {
                final net.opengress.slimgress.API.Common.Location location = portal.getPortalLocation();

                getActivity().runOnUiThread(() -> {
                    Bitmap portalIcon;
                    // TODO: make portal marker display portal health/deployment info (opacity x white, use shield image etc)
                    // i would also like to draw the resonators around it, but i'm not sure that that would be practical with osmdroid
                    // ... maybe i can at least write the portal level on the portal, like in iitc
                    // it's quite possible that resonators can live in a separate Hash of markers,
                    //   as long as the guids are stored with the portal info
                    portalIcon = mIcons.get(team.toString());

                    GroundOverlay marker = new GroundOverlay() {
                        public boolean touchedBy(final MotionEvent event) {
                            GeoPoint tappedGeoPoint = (GeoPoint) mMap.getProjection().fromPixels((int) event.getX(), (int) event.getY());
                            return getBounds().contains(tappedGeoPoint);
                        }

                        @Override
                        public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                            if (touchedBy(e)) {
                                Intent myIntent = new Intent(getContext(), ActivityPortal.class);
                                mGame.setCurrentPortal(portal);
                                startActivityForResult(myIntent, PORTAL_INTENT_CODE);
                                return true;
                            }
                            return false;
                        }
                    };
                    marker.setPosition(location.getLatLng().destinationPoint(25, 315), location.getLatLng().destinationPoint(25, 135));
                    marker.setImage(portalIcon);

                    mMap.getOverlays().add(marker);
                    mMarkers.put(portal.getEntityGuid(), marker);
                });
            }
        }
    }

    private void drawLink(final GameEntityLink link) {
        if (mMap != null) {
            // only update if line has not yet been added
            if (!mLines.containsKey(link.getEntityGuid())) {
                final net.opengress.slimgress.API.Common.Location origin = link.getLinkOriginLocation();
                final net.opengress.slimgress.API.Common.Location dest = link.getLinkDestinationLocation();

                // TODO: decay link per portal health
                getActivity().runOnUiThread(() -> {
                    Team team = link.getLinkControllingTeam();
                    int color = 0xff000000 + team.getColour(); // adding opacity

                    Polyline line = new Polyline(mMap);
                    line.addPoint(origin.getLatLng());
                    line.addPoint(dest.getLatLng());
                    line.setColor(color);
                    line.setWidth(2);
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

                getActivity().runOnUiThread(() -> {

                    // todo: decay field per portal health
                    Team team = field.getFieldControllingTeam();
                    int color = 0x32000000 + team.getColour(); // adding alpha

                    Polygon polygon = new Polygon(mMap);
                    polygon.addPoint(new GeoPoint(vA.getLatLng()));
                    polygon.addPoint(new GeoPoint(vB.getLatLng()));
                    polygon.addPoint(new GeoPoint(vC.getLatLng()));
                    polygon.setFillColor(color);
                    polygon.setStrokeWidth(0);
//                        polygon.zIndex(1);
                    polygon.setOnClickListener((poly, mapView, eventPos) -> false);

                    mMap.getOverlays().add(polygon);
                    mPolygons.put(field.getEntityGuid(), polygon);
                });
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PORTAL_INTENT_CODE) {
            Log.e("ScannerView", "Unknown intent code, I don't understand: " + requestCode);
            return;
        }
        if (data == null) {
            return;
        }

        Bundle hackResultBundle = data.getBundleExtra("result");
        HashMap<String, Integer> items = (HashMap<String, Integer>) hackResultBundle.getSerializable("items");
        HashMap<String, Integer> bonusItems = (HashMap<String, Integer>) hackResultBundle.getSerializable("bonusItems");
        String error = hackResultBundle.getString("error");

        if (error != null) {
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

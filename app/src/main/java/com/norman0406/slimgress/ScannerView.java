/***********************************************************************
 *
 * Slimgress: Ingress API for Android
 * Copyright (C) 2013 Norman Link <norman.link@gmx.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ***********************************************************************/

package com.norman0406.slimgress;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;
import com.norman0406.slimgress.API.Common.Team;
import com.norman0406.slimgress.API.Game.GameState;
import com.norman0406.slimgress.API.GameEntity.GameEntityBase;
import com.norman0406.slimgress.API.GameEntity.GameEntityControlField;
import com.norman0406.slimgress.API.GameEntity.GameEntityLink;
import com.norman0406.slimgress.API.GameEntity.GameEntityPortal;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MinimapOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class ScannerView extends Fragment {
    private IngressApplication mApp = IngressApplication.getInstance();
    private GameState mGame = mApp.getGame();
    private MapView mMap = null;

    private Bitmap mXMParticleIcon = null;
    private Bitmap mPortalIconResistance = null;
    private Bitmap mPortalIconEnlightened = null;
    private Bitmap mPortalIconNeutral = null;

    private HashMap<String, Marker> mMarkers = null;
    private HashMap<String, Polyline> mLines = null;
    private HashMap<String, Polygon> mPolygons = null;

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String PREFS_NAME = "org.andnav.osm.prefs";
    private static final String PREFS_TILE_SOURCE = "tilesource";
    private static final String PREFS_LATITUDE_STRING = "latitudeString";
    private static final String PREFS_LONGITUDE_STRING = "longitudeString";
    private static final String PREFS_ORIENTATION = "orientation";
    private static final String PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble";

    private static final int MENU_ABOUT = Menu.FIRST + 1;
    private static final int MENU_LAST_ID = MENU_ABOUT + 1; // Always set to last unused id

    // ===========================================================
    // Fields
    // ===========================================================
    private SharedPreferences mPrefs;
    private MinimapOverlay mMinimapOverlay;
    private ScaleBarOverlay mScaleBarOverlay;

    // ===========================================================
    // Other (location)
    // ===========================================================
    private GeoPoint currentLocation = null;
    private static final int RECORD_REQUEST_CODE = 101;

    public class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location location) {
            currentLocation = new GeoPoint(location);
            mGame.updateLocation(new com.norman0406.slimgress.API.Common.Location(currentLocation.getLatitude(), currentLocation.getLongitude()));
            displayMyCurrentLocationOverlay();
        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    }

    private void displayMyCurrentLocationOverlay() {
        System.err.println("Got location!");
        System.err.println(currentLocation);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        //Note! we are programmatically construction the map view
        //be sure to handle application lifecycle correct (see note in on pause)
        mMap = new MapView(inflater.getContext());
        mMap.setDestroyMode(false);
        mMap.setTag("mapView"); // needed for OpenStreetMapViewTest

        int permission = ContextCompat.checkSelfPermission(inflater.getContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION);

        if (permission != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(inflater.getContext());
                builder.setMessage("Permission to access the device location is required for this app to function correctly.")
                        .setTitle("Permission required");

                builder.setPositiveButton("OK", (dialog, id) -> makeRequest());

                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                makeRequest();
            }
        }

        MyLocationListener locationListener = new MyLocationListener();
        LocationManager locationManager = (LocationManager) mApp.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if( location != null ) {
            currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
        }

        mMap.setOnTouchListener((v, event) -> true);


        loadAssets();

        mMarkers = new HashMap<String, Marker>();
        mLines = new HashMap<String, Polyline>();
        mPolygons = new HashMap<String, Polygon>();

        // disable most ui elements // FIXME
//        UiSettings ui = mMap.getUiSettings();
//        ui.setAllGesturesEnabled(false);
//        ui.setCompassEnabled(false);
//        ui.setScrollGesturesEnabled(true);
//        ui.setRotateGesturesEnabled(true);
//        ui.setZoomControlsEnabled(false);
//        ui.setMyLocationButtonEnabled(false);
//
//        mMap.setMyLocationEnabled(true);

        mMap.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            /**
             * mouse wheel zooming ftw
             * http://stackoverflow.com/questions/11024809/how-can-my-view-respond-to-a-mousewheel
             * @param v
             * @param event
             * @return
             */
            @Override
            public boolean onGenericMotion(View v, MotionEvent event) {
                if (0 != (event.getSource() & InputDevice.SOURCE_CLASS_POINTER)) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_SCROLL:
                            if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f)
                                mMap.getController().zoomOut();
                            else {
                                //this part just centers the map on the current mouse location before the zoom action occurs
                                IGeoPoint iGeoPoint = mMap.getProjection().fromPixels((int) event.getX(), (int) event.getY());
                                mMap.getController().animateTo(iGeoPoint);
                                mMap.getController().zoomIn();
                            }
                            return true;
                    }
                }
                return false;
            }
        });

// FIXME
        //
//        mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
//            boolean firstLocation = true;
//
//            @Override
//            public void onMyLocationChange(Location myLocation)
//            {
//                // update camera position
//                CameraPosition pos = mMap.getCameraPosition();
//                CameraPosition newPos = new CameraPosition.Builder(pos)
//                        .target(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()))
//                        .zoom(16)
//                        .tilt(40)
//                        .build();
//                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(newPos));
//
//                // update game position
//                mGame.updateLocation(new com.norman0406.slimgress.API.Common.Location(myLocation.getLatitude(), myLocation.getLongitude()));
//
//                if (firstLocation) {
//                    firstLocation = false;
//                }
//            }
//        });

//        startWorldUpdate();

        // deactivate standard map
//        mMap.setMapType(GoogleMap.MAP_TYPE_NONE); // FIXME

        // add custom map tiles
        addIngressTiles();

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
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);



        Configuration.getInstance().setUserAgentValue("Slimgress/Openflux (OSMDroid)");

        //My Location
        //note you have handle the permissions yourself, the overlay did not do it for you
        MyLocationNewOverlay mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), mMap);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
        mMap.getOverlays().add(mLocationOverlay);


        //Mini map
//        mMinimapOverlay = new MinimapOverlay(context, mMap.getTileRequestCompleteHandler());
//        mMinimapOverlay.setWidth(dm.widthPixels / 5);
//        mMinimapOverlay.setHeight(dm.heightPixels / 5);
//        mMap.getOverlays().add(this.mMinimapOverlay);


        //Copyright overlay
        CopyrightOverlay mCopyrightOverlay = new CopyrightOverlay(context);
        //i hate this very much, but it seems as if certain versions of android and/or
        //device types handle screen offsets differently
        mMap.getOverlays().add(mCopyrightOverlay);


        //On screen compass
        CompassOverlay mCompassOverlay = new CompassOverlay(context, new InternalCompassOrientationProvider(context),
                mMap);
        mCompassOverlay.enableCompass();
        mMap.getOverlays().add(mCompassOverlay);


        //map scale
//        mScaleBarOverlay = new ScaleBarOverlay(mMap);
//        mScaleBarOverlay.setCentred(true);
//        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);
//        mMap.getOverlays().add(this.mScaleBarOverlay);


        //support for map rotation
        RotationGestureOverlay mRotationGestureOverlay = new RotationGestureOverlay(mMap);
        mRotationGestureOverlay.setEnabled(true);
        mMap.getOverlays().add(mRotationGestureOverlay);


        //needed for pinch zooms
        mMap.setMultiTouchControls(true);

        //scales tiles to the current screen's DPI, helps with readability of labels
        mMap.setTilesScaledToDpi(true);

        //the rest of this is restoring the last map location the user looked at
        final float zoomLevel = mPrefs.getFloat(PREFS_ZOOM_LEVEL_DOUBLE, 1);
        mMap.getController().setZoom(zoomLevel);
        final float orientation = mPrefs.getFloat(PREFS_ORIENTATION, 0);
        mMap.setMapOrientation(orientation, false);
        final String latitudeString = mPrefs.getString(PREFS_LATITUDE_STRING, "1.0");
        final String longitudeString = mPrefs.getString(PREFS_LONGITUDE_STRING, "1.0");
        final double latitude = Double.parseDouble(latitudeString);
        final double longitude = Double.parseDouble(longitudeString);
        mMap.setExpectedCenter(new GeoPoint(latitude, longitude));

        setHasOptionsMenu(true);
    }

    @Override
    public void onPause() {
        //save the current location
        final SharedPreferences.Editor edit = mPrefs.edit();
        edit.putString(PREFS_TILE_SOURCE, mMap.getTileProvider().getTileSource().name());
        edit.putFloat(PREFS_ORIENTATION, mMap.getMapOrientation());
        edit.putString(PREFS_LATITUDE_STRING, String.valueOf(mMap.getMapCenter().getLatitude()));
        edit.putString(PREFS_LONGITUDE_STRING, String.valueOf(mMap.getMapCenter().getLongitude()));
        edit.putFloat(PREFS_ZOOM_LEVEL_DOUBLE, (float) mMap.getZoomLevelDouble());
        edit.apply();

        mMap.onPause();
        super.onPause();
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
        final String tileSourceName = mPrefs.getString(PREFS_TILE_SOURCE,
                TileSourceFactory.DEFAULT_TILE_SOURCE.name());
        try {
            final ITileSource tileSource = TileSourceFactory.getTileSource(tileSourceName);
            mMap.setTileSource(tileSource);
        } catch (final IllegalArgumentException e) {
            mMap.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        }

        mMap.onResume();
        final Handler uiHandler = new Handler();
//        final Handler timerHandler = new Handler();
        uiHandler.post(() -> {
            // get map boundaries (on ui thread)
            double east = mMap.getProjection().getBoundingBox().getLonEast();
            double west = mMap.getProjection().getBoundingBox().getLonWest();
            double north = mMap.getProjection().getBoundingBox().getLatNorth();
            double south = mMap.getProjection().getBoundingBox().getLatSouth();
            final S2LatLngRect region = S2LatLngRect.fromPointPair(S2LatLng.fromDegrees(north, west),
                    S2LatLng.fromDegrees(south, east));

            // update world (on timer thread)
//            timerHandler.post(() -> {
            if (mGame.getLocation() != null)
                updateWorld(region, uiHandler);
//            });
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Put overlay items first
        mMap.getOverlayManager().onCreateOptionsMenu(menu, MENU_LAST_ID, mMap);

        // Put "About" menu item last
//        menu.add(0, MENU_ABOUT, Menu.CATEGORY_SECONDARY, org.osmdroid.R.string.about).setIcon(
//                android.R.drawable.ic_menu_info_details);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu pMenu) {
        mMap.getOverlayManager().onPrepareOptionsMenu(pMenu, MENU_LAST_ID, mMap);
        super.onPrepareOptionsMenu(pMenu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mMap.getOverlayManager().onOptionsItemSelected(item, MENU_LAST_ID, mMap)) {
            return true;
        }

//        switch (item.getItemId()) {
//            case MENU_ABOUT:
//                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
//                        .setTitle(org.osmdroid.R.string.app_name).setMessage(org.osmdroid.R.string.about_message)
//                        .setIcon(org.osmdroid.R.drawable.icon)
//                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//                                    public void onClick(DialogInterface dialog, int whichButton) {
//                                        //
//                                    }
//                                }
//                        );
//                builder.create().show();
//                return true;
//        }
        return super.onOptionsItemSelected(item);
    }

    public void zoomIn() {
//        mMap.getController().zoomIn();
    }

    public void zoomOut() {
//        mMap.getController().zoomOut();
    }

    // @Override
    // public boolean onTrackballEvent(final MotionEvent event) {
    // return this.mMap.onTrackballEvent(event);
    // }
    public void invalidateMapView() {
        mMap.invalidate();
    }

    private void loadAssets()
    {
        int portalSize = 80;
        mPortalIconResistance = Bitmap.createScaledBitmap(getBitmapFromAsset("portalTexture_RESISTANCE.png"), portalSize, portalSize, true);
        mPortalIconEnlightened = Bitmap.createScaledBitmap(getBitmapFromAsset("portalTexture_ALIENS.png"), portalSize, portalSize, true);
        mPortalIconNeutral = Bitmap.createScaledBitmap(getBitmapFromAsset("portalTexture_NEUTRAL.png"), portalSize, portalSize, true);
        mXMParticleIcon = Bitmap.createScaledBitmap(getBitmapFromAsset("particle.png"), 10, 10, true);
    }

    private Bitmap getBitmapFromAsset(String name)
    {
        AssetManager assetManager = getActivity().getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(name);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            return null;
        }

        return bitmap;
    }

    private void addIngressTiles()
    {
        // FIXME
//        // create a custom tile provider with an ingress-like map
//        TileProvider tiles = new UrlTileProvider(256, 256) {
//            @Override
//            public synchronized URL getTileUrl(int x, int y, int zoom) {
//                final String apistyle = "s.e%3Al%7Cp.v%3Aoff%2Cs.e%3Ag%7Cp.c%3A%23ff000000%2Cs.t%3A3%7Cs.e%3Ag%7Cp.c%3A%23ff5e9391";
//                final String style = "59,37%7Csmartmaps";
//
//                final String format = "http://mt1.googleapis.com/vt?lyrs=m&src=apiv3&hl=de-DE&x=%d&s=&y=%d&z=%d&s=Galileo";
//                String mapUrl = String.format(Locale.US, format, x, y, zoom);
//
//                mapUrl += "&apistyle=" + apistyle + "&style=" + style;
//
//                URL url = null;
//                try {
//                    url = new URL(mapUrl);
//                } catch (MalformedURLException e) {
//                    throw new AssertionError(e);
//                }
//                return url;
//            }
//        };
//
//        TileOverlayOptions tileOverlay = new TileOverlayOptions();
//        tileOverlay.tileProvider(tiles);
//
//        mMap.addTileOverlay(new TileOverlayOptions().tileProvider(tiles));
    }

    private void startWorldUpdate()
    {
        // TODO: problems blocking execution and causing out-of-memory exception

        final Handler uiHandler = new Handler();

        long updateInterval = mGame.getKnobs().getScannerKnobs().getUpdateIntervalMS();

        Timer updateTimer = new Timer();
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            final Handler timerHandler = new Handler();

            @Override
            public void run()
            {
                uiHandler.post(() -> {
                    // get map boundaries (on ui thread)
                    double northeast = mMap.getProjection().getBoundingBox().getLonEast();
                    double southwest = mMap.getProjection().getBoundingBox().getLonWest();
                    final S2LatLngRect region = S2LatLngRect.fromPointPair(S2LatLng.fromDegrees(southwest, southwest),
                            S2LatLng.fromDegrees(northeast, northeast));

                    // update world (on timer thread)
                    timerHandler.post(() -> {
                        if (mGame.getLocation() != null)
                            updateWorld(region, uiHandler);
                    });
                });
            }
        }, 0, updateInterval);
    }

    private synchronized void updateWorld(final S2LatLngRect region, final Handler uiHandler)
    {
        // handle interface result (on timer thread)
        final Handler resultHandler = new Handler(msg -> {
            // draw xm particles
            drawXMParticles();

            new Thread(() -> {
                // draw game entities
                Map<String, GameEntityBase> entities = mGame.getWorld().getGameEntities();
                Set<String> keys = entities.keySet();
                System.err.println(keys);
                for (String key : keys) {
                    System.err.println(key);
                    final GameEntityBase entity = entities.get(key);

                    uiHandler.post(() -> {
                        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal)
                            drawPortal((GameEntityPortal)entity);
                        else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Link)
                            drawLink((GameEntityLink)entity);
                        else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField)
                            drawField((GameEntityControlField)entity);
                    });
                }

                Log.d("ScannerView", "world updated");
            }).start();

            return true;
        });

        // get objects (on new thread)
        new Thread(() -> mGame.intGetObjectsInCells(region, resultHandler)).start();
    }

    private void drawXMParticles()
    {
        // draw xm particles
        /*World world = mGame.getWorld();
        Map<String, XMParticle> xmParticles = world.getXMParticles();
        Set<String> keys = xmParticles.keySet();
        for (String key : keys) {
            XMParticle particle = xmParticles.get(key);

            final Utils.LocationE6 location = particle.getCellLocation();

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(mXMParticleIcon);
                    mMap.addMarker(new MarkerOptions()
                    .position(location.getLatLng())
                    .icon(icon));
                }
            });
        }*/
    }

    private void drawPortal(final GameEntityPortal portal)
    {
        final Team team = portal.getPortalTeam();
        if (mMap != null) {
            // only update if marker has not yet been added
            if (!mMarkers.containsKey(portal.getEntityGuid())) {
                final com.norman0406.slimgress.API.Common.Location location = portal.getPortalLocation();

                getActivity().runOnUiThread(() -> {
                    Bitmap portalIcon;
                    if (team.getTeamType() == Team.TeamType.Resistance)
                        portalIcon = mPortalIconResistance;
                    else if (team.getTeamType() == Team.TeamType.Enlightened)
                        portalIcon = mPortalIconEnlightened;
                    else
                        portalIcon = mPortalIconNeutral;

                    Drawable icon = new BitmapDrawable(getResources(), portalIcon);

                    Marker marker = new Marker(mMap);
                    marker.setPosition(location.getLatLng());
                    marker.setTitle(portal.getPortalTitle());
                    marker.setIcon(icon);
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

                    mMap.getOverlays().add(marker);
                    mMarkers.put(portal.getEntityGuid(), marker);
                });
            }
        }
    }

    private void drawLink(final GameEntityLink link)
    {
        if (mMap != null) {
            // only update if line has not yet been added
            if (!mLines.containsKey(link.getEntityGuid())) {
                final com.norman0406.slimgress.API.Common.Location origin = link.getLinkOriginLocation();
                final com.norman0406.slimgress.API.Common.Location dest = link.getLinkDestinationLocation();

                getActivity().runOnUiThread(() -> {
                    int color = 0xff0000ff; // blue without alpha
                    Team team = link.getLinkControllingTeam();
                    if (team.getTeamType() == Team.TeamType.Enlightened)
                        color = 0xff00ff00; // green without alpha

                    Polyline line = new Polyline(mMap);
                    line.addPoint(origin.getLatLng());
                    line.addPoint(dest.getLatLng());
                    line.setColor(color);
                    line.setWidth(2);
//                        line.zIndex(2);

                    mMap.getOverlays().add(line);
                    mLines.put(link.getEntityGuid(), line);
                });
            }
        }
    }

    private void drawField(final GameEntityControlField field)
    {
        if (mMap != null) {
            // only update if line has not yet been added
            if (!mPolygons.containsKey(field.getEntityGuid())) {
                final com.norman0406.slimgress.API.Common.Location vA = field.getFieldVertexA().getPortalLocation();
                final com.norman0406.slimgress.API.Common.Location vB = field.getFieldVertexB().getPortalLocation();
                final com.norman0406.slimgress.API.Common.Location vC = field.getFieldVertexC().getPortalLocation();

                getActivity().runOnUiThread(() -> {

                    int color = 0x320000ff; // blue with alpha
                    Team team = field.getFieldControllingTeam();
                    if (team.getTeamType() == Team.TeamType.Enlightened)
                        color = 0x3200ff00; // green with alpha

                    Polygon polygon = new Polygon(mMap);
                    polygon.addPoint(new GeoPoint(vA.getLatLng()));
                    polygon.addPoint(new GeoPoint(vB.getLatLng()));
                    polygon.addPoint(new GeoPoint(vC.getLatLng()));
                    polygon.setFillColor(color);
                    polygon.setStrokeWidth(0);
//                        polygon.zIndex(1);

                    mMap.getOverlays().add(polygon);
                    mPolygons.put(field.getEntityGuid(), polygon);
                });
            }
        }
    }
}

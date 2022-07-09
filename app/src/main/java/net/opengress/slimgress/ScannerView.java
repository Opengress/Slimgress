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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;
import net.opengress.slimgress.API.Common.Team;
import net.opengress.slimgress.API.Game.GameState;
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
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class ScannerView extends Fragment {
    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private MapView mMap = null;

//    private Bitmap mXMParticleIcon = null;

    private final HashMap<String, Bitmap> mIcons = new HashMap<>();
    private HashMap<String, Marker> mMarkers = null;
    private HashMap<String, Polyline> mLines = null;
    private HashMap<String, Polygon> mPolygons = null;

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String PREFS_NAME = "org.andnav.osm.prefs";
    private static final String PREFS_LATITUDE_STRING = "latitudeString";
    private static final String PREFS_LONGITUDE_STRING = "longitudeString";
    private static final String PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble";

    private static final int MENU_ABOUT = Menu.FIRST + 1;
    private static final int MENU_LAST_ID = MENU_ABOUT + 1; // Always set to last unused id

    // ===========================================================
    // Fields and permissions
    // ===========================================================
    private SharedPreferences mPrefs;

    // ===========================================================
    // Other (location)
    // ===========================================================
    private MyLocationListener locationListener = null;
    private LocationManager locationManager = null;
    private MyLocationNewOverlay mLocationOverlay = null;
    private GeoPoint mCurrentLocation = null;
    private static final int RECORD_REQUEST_CODE = 101;
    private Date mLastScan = null;
    private Date mLastLocationAcquired = null;
    private GeoPoint mLastLocation = null;
    private Polygon mActionRadius = null;

    public class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location location) {
            mCurrentLocation = new GeoPoint(location);
            mGame.updateLocation(new net.opengress.slimgress.API.Common.Location(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()));
            mLastLocationAcquired = new Date();
            displayMyCurrentLocationOverlay(mCurrentLocation);
        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    }

    private void displayMyCurrentLocationOverlay(GeoPoint currentLocation) {
        // TODO: draw player (manually)
        List<GeoPoint> circle = Polygon.pointsAsCircle(currentLocation, 40);
        mMap.getOverlayManager().remove(mActionRadius);
        mActionRadius.setPoints(circle);
        mMap.getOverlayManager().add(mActionRadius);
        mMap.invalidate();

        long now = new Date().getTime();
        ScannerKnobs knobs = mGame.getKnobs().getScannerKnobs();

        if (mLastScan == null ||
                mLastLocation == null ||
                (now - mLastScan.getTime() >= knobs.getUpdateIntervalMS()) ||
                (now - mLastScan.getTime() >= knobs.getMinUpdateIntervalMS() && mLastLocation.distanceToAsDouble(currentLocation) >= knobs.getUpdateDistanceM())
        ) {
            if (mGame.getLocation() != null) {
                final Handler uiHandler = new Handler();
                uiHandler.post(() -> {
                    // get map boundaries (on ui thread)
                    double east = mMap.getProjection().getBoundingBox().getLonEast();
                    double west = mMap.getProjection().getBoundingBox().getLonWest();
                    double north = mMap.getProjection().getBoundingBox().getLatNorth();
                    double south = mMap.getProjection().getBoundingBox().getLatSouth();
                    final S2LatLngRect region = S2LatLngRect.fromPointPair(S2LatLng.fromDegrees(north, west),
                            S2LatLng.fromDegrees(south, east));

                    updateWorld(region, uiHandler);
                });

            }
        }
        mLastLocationAcquired = new Date();
        mLastLocation = currentLocation;

        // TODO test this: lock scroll/pan so that user can't pan/zoom away
//        mMap.setScrollableAreaLimitDouble(mMap.getBoundingBox());

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
        mMap = new MapView(inflater.getContext()) {
        };
        mMap.setDestroyMode(false);
        mMap.setTag("mapView"); // needed for OpenStreetMapViewTest
        mMap.setMinZoomLevel(16d);
        mMap.setMaxZoomLevel(22d);
        mMap.setFlingEnabled(false);
        mMap.setMultiTouchControls(true);

        mActionRadius = new Polygon(mMap);
        mActionRadius.getOutlinePaint().setColor(0x32ffff00);
        mActionRadius.getOutlinePaint().setStrokeWidth(10);
        mActionRadius.setOnClickListener((polygon, mapView, eventPos) -> false);

        // deactivate standard map
//        mMap.setMapType(GoogleMap.MAP_TYPE_NONE); // FIXME

        // add custom map tiles
        addIngressTiles();

        loadAssets();

        mMarkers = new HashMap<>();
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
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);


        Configuration.getInstance().setUserAgentValue("Slimgress/Openflux (OSMDroid)");

        //My Location
        //note you have handle the permissions yourself, the overlay did not do it for you
        mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), mMap);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.enableFollowLocation();
        mLocationOverlay.setEnableAutoStop(false);
        mMap.getOverlays().add(mLocationOverlay);

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

        locationListener = new MyLocationListener();
        locationManager = (LocationManager) mApp.getSystemService(Context.LOCATION_SERVICE);
        int permission = ContextCompat.checkSelfPermission(getContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION);

        if (permission != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setMessage("Permission to access the device location is required for this app to function correctly.")
                        .setTitle("Permission required");

                builder.setPositiveButton("OK", (dialog, id) -> makeRequest());

                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                makeRequest();
            }
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if( location != null ) {
                mCurrentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
            }
        }


    }

    private void loadAssets()
    {
        int portalSize = 80;
        Map<String, TeamKnobs.TeamType> teams = mGame.getKnobs().getTeamKnobs().getTeams();
        for (String team: teams.keySet()) {
            mIcons.put(team, getTintedImage("portalTexture_NEUTRAL.png", 0xff000000 + Objects.requireNonNull(teams.get(team)).getColour(), portalSize));
        }
//        mXMParticleIcon = Bitmap.createScaledBitmap(getBitmapFromAsset("particle.png"), 10, 10, true);
    }

    public Bitmap getTintedImage(String image, int color, int size) {
        Bitmap bitmap = Bitmap.createScaledBitmap(getBitmapFromAsset(image), size, size, true);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        Bitmap bitmapResult = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapResult);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmapResult;
    }

    // FIXME duplicated in ActivityPortal
    private Bitmap getBitmapFromAsset(String name)
    {
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

    private void addIngressTiles()
    {
        // Add tiles layer with custom tile source
        final MapTileProviderBasic tileProvider = new MapTileProviderBasic(mApp.getApplicationContext());
        final ITileSource tileSource = new XYTileSource("CartoDB Dark Matter", 3, 18, 256, ".png",
                new String[]{"https://c.basemaps.cartocdn.com/dark_all/"});
        tileProvider.setTileSource(tileSource);
        tileProvider.getTileRequestCompleteHandlers().add(mMap.getTileRequestCompleteHandler());
        final TilesOverlay tilesOverlay = new TilesOverlay(tileProvider, this.getContext());
        tilesOverlay.setLoadingBackgroundColor(Color.BLACK);
        mMap.getOverlays().add(tilesOverlay);
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
                for (String key : keys) {
                    final GameEntityBase entity = entities.get(key);

                    uiHandler.post(() -> {
                        assert entity != null;
                        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal)
                            drawPortal((GameEntityPortal)entity);
                        else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Link)
                            drawLink((GameEntityLink)entity);
                        else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField)
                            drawField((GameEntityControlField)entity);
                    });
                }

                mLastScan = new Date();
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

    private void drawPortal(@NonNull final GameEntityPortal portal)
    {
        final Team team = portal.getPortalTeam();
        if (mMap != null) {
            // only update if marker has not yet been added
            if (!mMarkers.containsKey(portal.getEntityGuid())) {
                final net.opengress.slimgress.API.Common.Location location = portal.getPortalLocation();

                getActivity().runOnUiThread(() -> {
                    Bitmap portalIcon;
                    portalIcon = mIcons.get(team.toString());

                    // TODO: make portal marker display portal health/deployment info (opacity x white, use shield image etc)
                    // i would also like to draw the resonators around it, but i'm not sure that that would be practical with osmdroid
                    // ... maybe i can at least write the portal level on the portal, like in iitc
                    Drawable icon = new BitmapDrawable(getResources(), portalIcon);

                    Marker marker = new Marker(mMap);
                    marker.setPosition(location.getLatLng());
                    marker.setTitle(portal.getPortalTitle());
                    marker.setIcon(icon);
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    marker.setOnMarkerClickListener((marker12, mapView) -> {
                        Intent myIntent = new Intent(getContext(), ActivityPortal.class);
                        mGame.setCurrentPortal(portal);
                        startActivity(myIntent);
                        return true;
                    });

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

    private void drawField(final GameEntityControlField field)
    {
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

}

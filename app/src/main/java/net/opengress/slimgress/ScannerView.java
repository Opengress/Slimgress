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

import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE;
import static net.opengress.slimgress.ViewHelpers.getBitmapFromAsset;
import static net.opengress.slimgress.ViewHelpers.getBitmapFromDrawable;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getRgbaStringFromColour;
import static net.opengress.slimgress.ViewHelpers.getTintedImage;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;
import static net.opengress.slimgress.api.Common.Utils.notBouncing;
import static net.opengress.slimgress.api.Item.ItemBase.ItemType.PortalKey;
import static org.maplibre.android.style.layers.PropertyFactory.circleColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleRadius;
import static org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth;

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
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.geometry.S2LatLng;

import net.opengress.slimgress.activity.ActivityMain;
import net.opengress.slimgress.activity.ActivityPortal;
import net.opengress.slimgress.activity.ActivitySplash;
import net.opengress.slimgress.api.Common.Location;
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
import net.opengress.slimgress.api.Knobs.ScannerKnobs;
import net.opengress.slimgress.api.Knobs.TeamKnobs;
import net.opengress.slimgress.dialog.DialogHackResult;
import net.opengress.slimgress.positioning.AndroidBearingProvider;
import net.opengress.slimgress.positioning.AndroidLocationProvider;
import net.opengress.slimgress.positioning.LocationCallback;

import org.maplibre.android.MapLibre;
import org.maplibre.android.WellKnownTileServer;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngQuad;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.maps.UiSettings;
import org.maplibre.android.plugins.annotation.FillManager;
import org.maplibre.android.plugins.annotation.FillOptions;
import org.maplibre.android.plugins.annotation.Line;
import org.maplibre.android.plugins.annotation.LineManager;
import org.maplibre.android.plugins.annotation.LineOptions;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.ImageSource;
import org.maplibre.android.style.sources.RasterSource;
import org.maplibre.android.style.sources.TileSet;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ScannerView extends Fragment {
    // ===========================================================
    // Hardcore internal stuff
    // ===========================================================
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();

    // ===========================================================
    // Knobs quick reference
    // ===========================================================
    ScannerKnobs mScannerKnobs;
    private int mActionRadiusM = 40;
    private int mUpdateIntervalMS;
    private int mMinUpdateIntervalMS;
    private int mUpdateDistanceM;


    // ===========================================================
    // Map basics
    // ===========================================================
    final int PORTAL_DIAMETER_METRES = 40;
    private MapView mMapView = null;
    private MapLibreMap mMapLibreMap;
    private SymbolManager mSymbolManager;
    private LineManager mLineManager;
    private FillManager mFillManager;
    private final HashMap<String, Bitmap> mIcons = new HashMap<>();
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
    private final HashMap<String, HashMap<Integer, Line>> mResonatorThreads = new HashMap<>();
    // for finding the portal marker when we delete it by Guid
    private final HashMap<String, Pair<String, Integer>> mResonatorToPortalSlotLookup = new HashMap<>();
    private final ArrayList<String> mLines = new ArrayList<>();
    private final ArrayList<String> mPolygons = new ArrayList<>();

    private ActivityResultLauncher<Intent> mPortalActivityResultLauncher;

    // ===========================================================
    // Fields and permissions
    // ===========================================================
    private SharedPreferences mPrefs;

    private net.opengress.slimgress.api.Common.Location mCurrentLocation = null;
    private static final int RECORD_REQUEST_CODE = 101;
    private long mLastScan = 0;
    // FIXME this should be used to set "location inaccurate" if updates mysteriously stop
    private Date mLastLocationAcquired = null;
    private net.opengress.slimgress.api.Common.Location mLastLocation = null;


    // ===========================================================
    // Other (location)
    // ===========================================================
    private GeoJsonSource mPlayerCursorSource;
    private ImageSource mPlayerCursorImageSource;

    // device sensor manager
    private int mBearing = 0;
    private AndroidBearingProvider mBearingProvider;
    private AndroidLocationProvider mLocationProvider;
    private boolean mHaveRotationSensor = false;


    private final int MAP_ROTATION_ARBITRARY = 1;
    private final int MAP_ROTATION_FLOATING = 2;
    private int CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;

    // ===========================================================
    // UX/UI Stuff - Events
    // ===========================================================
    private Symbol mMarkerInfoCard;
    private boolean mIsRotating = false;
    private boolean mIsZooming = false;
    private GestureDetector mGestureDetector;
    private double mPrevAngle = 0;
    private float mStartY = 0;
    private final float ZOOM_SENSITIVITY = 0.1f;
    private boolean mIsClickingCompass = false;
    GeoJsonSource mPortalGeoJsonSource;
    private long mCircleId = 1;

    // ===========================================================
    // Misc
    // ===========================================================
    private ArrayList<String> mSlurpableParticles = new ArrayList<>();

    private void updateBearing(int bearing) {
        if (mMapLibreMap == null) {
            return;
        }
        mHaveRotationSensor = true;
        if (bearing != mBearing) {
            mBearing = bearing;
//            updateBearingInData(mBearing);
            drawPlayerCursor();
        }
    }

    private void drawPlayerCursor() {
        // hardcoded and possibly incorrect, also not enough teams

        if (mMapLibreMap == null || mPlayerCursorImageSource == null || mCurrentLocation == null) {
            return;
        }
        LatLngQuad newImagePosition = getRotatedLatLngQuad(mCurrentLocation, 25, 25, mBearing);
        mPlayerCursorImageSource.setCoordinates(newImagePosition);

        if (CURRENT_MAP_ORIENTATION_SCHEME == MAP_ROTATION_ARBITRARY) {
            mMapLibreMap.easeCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation.getLatLng(), mMapLibreMap.getCameraPosition().zoom));
        } else {
            mMapLibreMap.easeCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                    .target(mCurrentLocation.getLatLng())
                    .zoom(mMapLibreMap.getCameraPosition().zoom)
                    .bearing((360 - mBearing + 360) % 360)
                    .build()));
        }

        updateActionRadiusLocation(mCurrentLocation);
    }

    public void setupPlayerCursor(Location initialLocation, int bearing) {
        if (mMapLibreMap.getStyle() == null) {
            return;
        }

        mPlayerCursorSource = new GeoJsonSource("player-cursor-source", Feature.fromGeometry(Point.fromLngLat(initialLocation.getLongitude(), initialLocation.getLatitude())));

        LatLngQuad rotatedQuad = getRotatedLatLngQuad(initialLocation, 25, 25, bearing);
        mPlayerCursorImageSource = new ImageSource("bearing-image-source", rotatedQuad, Objects.requireNonNull(mIcons.get("playercursor")));

        mMapLibreMap.getStyle(style -> {
            style.addSource(mPlayerCursorSource);
            style.addSource(mPlayerCursorImageSource);
            style.addLayer(new RasterLayer("player-cursor-image", "bearing-image-source"));
        });

        setupActionRadius(initialLocation);

    }

    // Function to calculate LatLngQuad for the given radius in meters
    private LatLngQuad getRadialLatLngQuad(Location center, double radiusMeters) {
        LatLng topLeft = calculateOffset(center, -radiusMeters, radiusMeters);
        LatLng topRight = calculateOffset(center, radiusMeters, radiusMeters);
        LatLng bottomRight = calculateOffset(center, radiusMeters, -radiusMeters);
        LatLng bottomLeft = calculateOffset(center, -radiusMeters, -radiusMeters);

        return new LatLngQuad(topLeft, topRight, bottomRight, bottomLeft);
    }

    // Helper function to calculate the offset LatLng
    private LatLng calculateOffset(Location center, double offsetX, double offsetY) {
        double latOffset = offsetY / S2LatLng.EARTH_RADIUS_METERS * (180 / Math.PI);
        double lngOffset = offsetX / (S2LatLng.EARTH_RADIUS_METERS * Math.cos(Math.toRadians(center.getLatitude()))) * (180 / Math.PI);

        return new LatLng(center.getLatitude() + latOffset, center.getLongitude() + lngOffset);
    }

    // Function to calculate a rotated LatLngQuad based on bearing
    private LatLngQuad getRotatedLatLngQuad(Location center, double width, double height, double bearing) {
        // Convert bearing from degrees to radians
        bearing = ((bearing % 360) + 360) % 360;
        double bearingRad = Math.toRadians(bearing);

        // Define the offsets for the image's width and height (in meters)
        double halfWidth = width / 2;
        double halfHeight = height / 2;

        // Calculate the rotated points for each corner of the image
        LatLng topLeft = calculateRotatedPoint(center, -halfWidth, halfHeight, bearingRad);
        LatLng topRight = calculateRotatedPoint(center, halfWidth, halfHeight, bearingRad);
        LatLng bottomRight = calculateRotatedPoint(center, halfWidth, -halfHeight, bearingRad);
        LatLng bottomLeft = calculateRotatedPoint(center, -halfWidth, -halfHeight, bearingRad);

        return new LatLngQuad(topLeft, topRight, bottomRight, bottomLeft);
    }

    // Helper function to calculate the rotated point around the center
    private LatLng calculateRotatedPoint(Location center, double offsetX, double offsetY, double bearingRad) {
        // Apply the rotation matrix to the point
        double rotatedX = offsetX * Math.cos(bearingRad) - offsetY * Math.sin(bearingRad);
        double rotatedY = offsetX * Math.sin(bearingRad) + offsetY * Math.cos(bearingRad);

        return calculateOffset(center, rotatedX, rotatedY);
    }


    public void setupActionRadius(Location initialLocation) {
        if (mMapLibreMap.getStyle() == null) {
            return;
        }
        LatLngQuad actionRadiusQuad = getRadialLatLngQuad(initialLocation, mActionRadiusM);
        ImageSource actionRadiusSource = new ImageSource("action-radius-source", actionRadiusQuad, mIcons.get("actionradius"));
        mMapLibreMap.getStyle().addSource(actionRadiusSource);
        RasterLayer actionRadiusLayer = new RasterLayer("action-radius-layer", "action-radius-source").withProperties(
                PropertyFactory.rasterOpacity(0.5f)
        );
        mMapLibreMap.getStyle().addLayer(actionRadiusLayer);
    }

    // Method to update the action radius position (just update the GeoJsonSource)
    public void updateActionRadiusLocation(Location newLocation) {
        if (mMapLibreMap.getStyle() == null) {
            return;
        }
        ImageSource actionRadiusSource = (ImageSource) mMapLibreMap.getStyle().getSource("action-radius-source");
        assert actionRadiusSource != null;
        actionRadiusSource.setCoordinates(getRadialLatLngQuad(newLocation, mActionRadiusM));
    }

    private float metresToPixels(double meters, Location location) {
        return (float) (meters / mMapLibreMap.getProjection().getMetersPerPixelAtLatitude(location.getLatitude()));
    }

    private float metresToPixels(double meters, LatLng location) {
        return (float) (meters / mMapLibreMap.getProjection().getMetersPerPixelAtLatitude(location.getLatitude()));
    }


    private void displayMyCurrentLocationOverlay(Location currentLocation) {

        long now = System.currentTimeMillis();

        if (mLastScan == 0 || mLastLocation == null || (now - mLastScan >= mUpdateIntervalMS) || (now - mLastScan >= mMinUpdateIntervalMS && mLastLocation.distanceTo(currentLocation) >= mUpdateDistanceM)) {
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

        drawPlayerCursor();

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mGame.getKnobs() == null) {
            Intent intent = new Intent(getActivity(), ActivitySplash.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }

        mBearingProvider = new AndroidBearingProvider(requireContext());
        mLocationProvider = new AndroidLocationProvider(requireContext());
        mBearingProvider.setBearingCallback(bearing -> updateBearing((int) bearing));
        mLocationProvider.setLocationCallback(new LocationCallback() {
            @Override
            public void onLocationUpdated(android.location.Location location) {
                slurp();
                mCurrentLocation = new Location(location.getLatitude(), location.getLongitude());
                mApp.getLocationViewModel().setLocationData(mCurrentLocation);
                mGame.updateLocation(mCurrentLocation);
                mLastLocationAcquired = new Date();

                if (!mHaveRotationSensor && location.hasBearing()) {
                    mBearing = (int) location.getBearing();
                }

                displayMyCurrentLocationOverlay(mCurrentLocation);
            }

            @Override
            public void onUpdatesStarted() {
                // FIXME this or OnLocationUpdated should setLocationInaccurate(false) by hitting displayMyLocation
            }

            @Override
            public void onUpdatesStopped() {
                setLocationInaccurate(true);
            }
        });
        setLocationInaccurate(true);

        mApp.getDeletedEntityGuidsModel().getDeletedEntityGuids().observe(this, this::onReceiveDeletedEntityGuids);

        mPortalActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onPortalActivityResult);

        mScannerKnobs = mGame.getKnobs().getScannerKnobs();
        mActionRadiusM = mScannerKnobs.getActionRadiusM();
        mUpdateIntervalMS = mScannerKnobs.getUpdateIntervalMS();
        mMinUpdateIntervalMS = mScannerKnobs.getMinUpdateIntervalMS();
        mUpdateDistanceM = mScannerKnobs.getUpdateDistanceM();

    }

    public void onReceiveDeletedEntityGuids(List<String> deletedEntityGuids) {
//        for (String guid : deletedEntityGuids) {
//
//            // for XM particles
//            if (guid.contains(".")) {
//                long particle = Long.parseLong(guid.substring(0, 16), 16);
//                if (mXMMarkers.containsKey(particle)) {
//                    mMapView.getOverlays().remove(mXMMarkers.get(particle));
//                    recycleOverlay(mXMMarkers.get(particle));
//                    mXMMarkers.remove(particle);
//                }
//                continue;
//            }
//
//            // for portals
//            if (mPortalMarkers.containsKey(guid)) {
//                mMapView.getOverlays().remove(mPortalMarkers.get(guid));
//                mPortalMarkers.remove(guid);
//                continue;
//            }
//
//            // for resonators
//            if (mResonatorToPortalSlotLookup.containsKey(guid)) {
//                var portal = Objects.requireNonNull(mResonatorToPortalSlotLookup.get(guid)).first;
//                var slot = Objects.requireNonNull(mResonatorToPortalSlotLookup.get(guid)).second;
//                var resoParts = Objects.requireNonNull(mResonatorMarkers.get(portal)).get(slot);
//                if (resoParts != null) {
//                    mMapView.getOverlays().remove(resoParts.first);
//                    mMapView.getOverlays().remove(resoParts.second);
//                    recycleOverlay(Objects.requireNonNull(resoParts.first));
//                    recyclePolyline(Objects.requireNonNull(resoParts.second));
//                }
//                mResonatorToPortalSlotLookup.remove(guid);
//                Objects.requireNonNull(mResonatorMarkers.get(portal)).remove(slot);
//                continue;
//            }
//
//            // for links
//            if (mLines.containsKey(guid)) {
//                mMapView.getOverlays().remove(mLines.get(guid));
//                recyclePolyline(mLines.get(guid));
//                mLines.remove(guid);
//                continue;
//            }
//
//            // for fields
//            if (mPolygons.containsKey(guid)) {
//                mMapView.getOverlays().remove(mPolygons.get(guid));
//                mPolygons.remove(guid);
//            }
//
//            // for dropped items
//            if (mItemMarkers.containsKey(guid)) {
//                mMapView.getOverlays().remove(mItemMarkers.get(guid));
//                recycleOverlay(mItemMarkers.get(guid));
//                mItemMarkers.remove(guid);
//            }
//
//        }
    }

//    private MapTileProviderBasic createTileProvider(MapProvider provider) {
//        ITileSource tileSource;
//        if (provider == null) {
//            tileSource = new BlankTileSource();
//        } else if (provider.getCoordinateSystem() == XYZ) {
//            tileSource = new XYTileSource(provider.getName(), provider.getMinZoom(), provider.getMaxZoom(), provider.getTileSize(), provider.getFilenameEnding(),
//                    provider.getBaseUrls());
//        } else {
//            throw new RuntimeException("Unknown slippy map coordinate system!");
//        }
//        var tileProvider = new MapTileProviderBasic(mApp.getApplicationContext(), tileSource);
//        if (provider == null) {
//            tileProvider.detach();
//        }
//        return tileProvider;
//    }

//    private MapTileProviderBasic getMapTileProvider(String name, String def) {
//        MapProvider mapProvider = mGame.getKnobs().getMapCompositionRootKnobs().fromString(mPrefs.getString(name, def));
//        return createTileProvider(mapProvider);
//    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        MapLibre.getInstance(requireContext(), "", WellKnownTileServer.MapLibre);

        View rootView = inflater.inflate(R.layout.fragment_scanner, container, false);
        mMapView = rootView.findViewById(R.id.mapView);

        mPrefs = mApp.getApplicationContext().getSharedPreferences(requireActivity().getApplicationInfo().packageName, Context.MODE_PRIVATE);
        // allows map tiles to be cached so map draws properly
//        Configuration.getInstance().load(requireContext(), mPrefs);
//        Configuration.getInstance().setOsmdroidTileCache(new File(requireActivity().getCacheDir(), "osmdroid")); // Set the cache directory

        mPrefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            Log.d("Scanner", String.format("PREFERENCE CHANGE IN ANOTHER THING: %s", key));
            if (Objects.requireNonNull(key).equals(PREFS_DEVICE_TILE_SOURCE)) {
//                mMapView.setTileProvider(getMapTileProvider(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT));
            }
        });

        // set up map tile source before creating map, so we don't download wrong tiles wastefully
//        final MapTileProviderBasic tileProvider = getMapTileProvider(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT);


        loadAssets();

        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(mapLibreMap -> {
            mMapLibreMap = mapLibreMap;
            mMapLibreMap.setMinZoomPreference(16);
            mMapLibreMap.setMaxZoomPreference(22);
            // FIXME wrong layer name or layer needs to be dynamic
            mMapLibreMap.setStyle(new Style.Builder().withSource(new RasterSource("carto-basemap", new TileSet("tileset", "https://a.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}.png"))).withLayer(new RasterLayer("carto-basemap-layer", "carto-basemap")), style -> {

                // FIXME zoom, bearing and latlng should be in prefs
                Location initialLocation = new Location(-42.673314, 171.025762);
                setupPlayerCursor(initialLocation, 0);
                mMapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation.getLatLng(), 18));

//                mMapLibreMap.getUiSettings().setCompassMargins(5, 150, 0, 0);
                mMapLibreMap.getUiSettings().setCompassMargins(5, 100, 0, 0);
                mMapLibreMap.getUiSettings().setCompassGravity(Gravity.LEFT);
                mMapLibreMap.getUiSettings().setCompassFadeFacingNorth(false);
                // lets user pan away - do not want
                mMapLibreMap.getUiSettings().setScrollGesturesEnabled(false);
                // tilt and zoom stay enabled for now
                // this is just not an appropriate way to zoom
                mMapLibreMap.getUiSettings().setDoubleTapGesturesEnabled(false);
                // our version is better (and reverses the direction)
                mMapLibreMap.getUiSettings().setQuickZoomGesturesEnabled(false);
                mMapLibreMap.getUiSettings().setAllVelocityAnimationsEnabled(false);

                mMapLibreMap.addOnMapLongClickListener(point -> {
                    if (mIsRotating || mIsZooming || mIsClickingCompass) {
                        mIsClickingCompass = false;
                        return false;
                    }
                    ((ActivityMain) requireActivity()).showFireMenu(point);
                    return true;
                });

                mMapLibreMap.addOnMapClickListener(this::onMapClick);


                mPortalGeoJsonSource = new GeoJsonSource("portal-data-layer");
                style.addSource(mPortalGeoJsonSource);

                CircleLayer circleLayer = new CircleLayer("portal-hit-layer", "portal-data-layer");
                circleLayer.setProperties(
                        circleRadius(30f),
                        circleColor("rgba(255, 0, 0, 0)")
                );
                style.addLayer(circleLayer);

                mMapLibreMap.addOnCameraMoveListener(() -> {
                    float radiusInPixels = metresToPixels(PORTAL_DIAMETER_METRES / 3, Objects.requireNonNull(mMapLibreMap.getCameraPosition().target));
                    circleLayer.setProperties(circleRadius(radiusInPixels));
                });

                mSymbolManager = new SymbolManager(mMapView, mMapLibreMap, style);
                mSymbolManager.setIconAllowOverlap(true);
                mLineManager = new LineManager(mMapView, mapLibreMap, style);
                mFillManager = new FillManager(mMapView, mapLibreMap, style);
            });
        });

        mGestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (mMapLibreMap != null) {
                    if (!mIsZooming) {
                        mIsRotating = true;

                        // Get the center of the map (which we use as the rotation point)
                        final float xc = mMapLibreMap.getWidth() / 2;
                        final float yc = mMapLibreMap.getHeight() / 2;

                        // Current touch point (e2) - where the gesture is currently
                        final float x = e2.getX();
                        final float y = e2.getY();

                        // Calculate the angle from the center of the screen to the current touch point
                        double currentAngleRad = Math.atan2(x - xc, yc - y);
                        double currentAngleDeg = Math.toDegrees(currentAngleRad);

                        // On the first scroll event, initialize the previous angle
                        if (mPrevAngle == 0f) {
                            mPrevAngle = currentAngleDeg;
                        }

                        // Calculate the angle difference relative to the previous position
                        double angleDifference = mPrevAngle - currentAngleDeg;

                        // Apply the rotation difference to the map's bearing
                        mMapLibreMap.moveCamera(CameraUpdateFactory.bearingTo(mMapLibreMap.getCameraPosition().bearing + angleDifference));

                        // Update the previous angle for the next scroll event
                        mPrevAngle = currentAngleDeg;

                        CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;

                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                mIsZooming = true;
                return true;
            }
        });

// Set a touch listener on the overlay to intercept gestures
        rootView.findViewById(R.id.gestureOverlay).setOnTouchListener((v, event) -> {
            // need to capture mouse up and mouse down here to check that we can start rotation
            boolean isGestureHandled = event.getPointerCount() == 1 && mGestureDetector.onTouchEvent(event);

            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                mPrevAngle = 0;
                mIsRotating = false;
                mIsZooming = false;
                if (checkAndProcessCompassClick(event)) {
                    return true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && mIsZooming) {
                // FIXME probably have to reset tracking here - check
                float currentY = event.getY();
                if (mStartY < currentY) {
                    mMapLibreMap.moveCamera(CameraUpdateFactory.zoomTo(mMapLibreMap.getCameraPosition().zoom - ZOOM_SENSITIVITY));
                } else {
                    mMapLibreMap.moveCamera(CameraUpdateFactory.zoomTo(mMapLibreMap.getCameraPosition().zoom + ZOOM_SENSITIVITY));
                }
                mStartY = currentY;
                return true;
            }

            // If it's not a rotation/zoom gesture, let MapLibre handle the event
            if (!isGestureHandled) {
                v.performClick();
                return mMapView.onTouchEvent(event);
            }

            return true;
        });

        return rootView;
    }

    private void addTouchTargets(List<Feature> features) {
        mPortalGeoJsonSource.setGeoJson(FeatureCollection.fromFeatures(features));
    }

    private boolean checkAndProcessCompassClick(@NonNull MotionEvent e) {
        // Get the compass margins and calculate its position
        UiSettings uiSettings = mMapLibreMap.getUiSettings();
        int[] compassMargins = new int[4];
        compassMargins[0] = uiSettings.getCompassMarginLeft();
        compassMargins[1] = uiSettings.getCompassMarginTop();
        compassMargins[2] = uiSettings.getCompassMarginRight();
        compassMargins[3] = uiSettings.getCompassMarginBottom();

        int compassSize = Objects.requireNonNull(uiSettings.getCompassImage()).getIntrinsicWidth();

        // Check if the tap occurred within the compass area
        if (e.getX() > compassMargins[0] && e.getX() < compassMargins[0] + compassSize && e.getY() > compassMargins[1] && e.getY() < compassMargins[1] + compassSize) {

            mIsClickingCompass = true;

            // could put an icon over the compass to indicate the mode
            if (CURRENT_MAP_ORIENTATION_SCHEME == MAP_ROTATION_FLOATING) {
                CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;
                mMapLibreMap.resetNorth();
            } else {
                CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_FLOATING;
            }

            return true;
        }

        return false;
    }

    protected void makePermissionsRequest() {
        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, RECORD_REQUEST_CODE);
    }


    private void addExpandingCircle(Location centerPoint, int durationMs, float radiusM, int colour, float width) {
        if (mMapLibreMap == null || mMapLibreMap.getStyle() == null) {
            Log.d("SCANNER", "No mapLibreMap");
            return;
        }
        float radius = metresToPixels(radiusM, centerPoint);
        String sourceId = "circle-source-" + ++mCircleId;
        String layerId = "circle-layer-" + mCircleId;

        GeoJsonSource circleSource = new GeoJsonSource(sourceId,
                Feature.fromGeometry(Point.fromLngLat(centerPoint.getLongitude(), centerPoint.getLatitude())));

        mMapLibreMap.getStyle().addSource(circleSource);

        CircleLayer circleLayer = new CircleLayer(layerId, sourceId);
        circleLayer.setProperties(
                circleRadius(0f),
                circleColor("rgba(0, 0, 0, 0)"),
                circleStrokeColor(colour),
                circleStrokeWidth(width),
                circleStrokeOpacity(0.5f)
        );
        mMapLibreMap.getStyle().addLayer(circleLayer);

        final long startTime = System.currentTimeMillis();
        final Choreographer choreographer = Choreographer.getInstance();

        final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min((float) elapsed / durationMs, 1f);
                float radius1 = radius * progress;

                circleLayer.setProperties(circleRadius(radius1));

                // Continue animating or clean up
                if (progress < 1f) {
                    choreographer.postFrameCallback(this);
                } else {
                    mMapLibreMap.getStyle().removeLayer(layerId);
                    mMapLibreMap.getStyle().removeSource(sourceId);
                    choreographer.removeFrameCallback(this);
                }
            }
        };

        // Start the animation
        choreographer.postFrameCallback(frameCallback);

    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = this.requireActivity();


//        Configuration.getInstance().setUserAgentValue("Slimgress/Openflux (OSMDroid)");

//        //the rest of this is restoring the last map location the user looked at
//        final float zoomLevel = mPrefs.getFloat(PREFS_OSM_ZOOM_LEVEL_DOUBLE, 18);
//        mMapView.getController().setZoom(zoomLevel);
//        mMapView.setMapOrientation(0, false);
//        final String latitudeString = mPrefs.getString(PREFS_OSM_LATITUDE_STRING, "1.0");
//        final String longitudeString = mPrefs.getString(PREFS_OSM_LONGITUDE_STRING, "1.0");
//        final double latitude = Double.parseDouble(latitudeString);
//        final double longitude = Double.parseDouble(longitudeString);
//        mMapView.setExpectedCenter(new GeoPoint(latitude, longitude));
    }

    @Override
    public void onPause() {
        //save the current location
//        final SharedPreferences.Editor edit = mPrefs.edit();
//        edit.putString(PREFS_OSM_LATITUDE_STRING, String.valueOf(mMapView.getMapCenter().getLatitude()));
//        edit.putString(PREFS_OSM_LONGITUDE_STRING, String.valueOf(mMapView.getMapCenter().getLongitude()));
//        edit.putFloat(PREFS_OSM_ZOOM_LEVEL_DOUBLE, (float) mMapView.getZoomLevelDouble());
//        edit.apply();

        mMapView.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        mMapView.onDestroy();
        super.onDestroyView();
        mBearingProvider.stopBearingUpdates();
        mLocationProvider.stopLocationUpdates();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();

        // technically this probably means we're setting this up twice.
        // probably mostly harmless, but maybe try to fix later.
//        mMapView.setTileProvider(getMapTileProvider(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT));

        if (mLastLocationAcquired == null || mLastLocationAcquired.before(new Date(System.currentTimeMillis() - mUpdateIntervalMS))) {
            setLocationInaccurate(true);
        } else if (mLastLocationAcquired.before(new Date(System.currentTimeMillis() - mMinUpdateIntervalMS))) {
            // might be pointing the wrong way til next location update but that's ok
            displayMyCurrentLocationOverlay(mCurrentLocation);
        }


        // ===========================================================
        // Other (location)
        // ===========================================================

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setMessage("Permission to access the device location is required for this app to function correctly.").setTitle("Permission required");

            builder.setPositiveButton("OK", (dialog, id) -> makePermissionsRequest());

            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            requestLocationUpdates();
        }


        if (getActivity() != null) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    public void requestLocationUpdates() {
        if (!mLocationProvider.startLocationUpdates()) {
            setLocationInaccurate(true);
        }
        mBearingProvider.startBearingUpdates();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setMapEnabled(boolean bool) {
        // there's probably a better way
        if (bool) {
            mMapView.setOnTouchListener(null);
        } else {
            mMapView.setOnTouchListener((v, event) -> true);
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
//            mMapView.getOverlayManager().remove(mActionRadius);
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
        addExpandingCircle(mCurrentLocation, 500 * 1000 / 60, 500, 0x33FFFF00, 1);
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
        mApp.getExecutorService().submit(() -> mGame.intGetObjectsInCells(mGame.getLocation(), resultHandler));
        final Handler commsHandler = new Handler(Looper.getMainLooper());
        mApp.getExecutorService().submit(() -> mGame.intLoadCommunication(false, 50, false, commsHandler));
    }

    private void slurp() {

        final Location playerLoc = mGame.getLocation();
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
        mSlurpableParticles.clear();


        Iterator<Map.Entry<Long, XMParticle>> iterator = xmParticles.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, XMParticle> entry = iterator.next();
            if (oldXM + newXM >= maxXM) {
                // continue is more computationally expensive and USUALLY not needed
                break;
            }

            XMParticle particle = entry.getValue();

            // FIXME this is honestly the worst imaginable solution, but for now it's what i have...
            assert particle != null;
            final Location location = particle.getCellLocation();
            if (location.distanceTo(playerLoc) < mActionRadiusM) {

                mSlurpableParticles.add(particle.getGuid());
                newXM += particle.getAmount();
//                var marker = mXMMarkers.remove(key);
//                mMapView.getOverlays().remove(marker);
                iterator.remove();
            }
        }

        mGame.addSlurpableXMParticles(mSlurpableParticles);
        mGame.getAgent().addEnergy(newXM);

        if (getActivity() != null && newXM > 0) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    public void updateScreen(Handler uiHandler) {
        if (mMapLibreMap == null || mMapLibreMap.getStyle() == null) {
            return;
        }

        List<Feature> features = new ArrayList<>();

        mApp.getExecutorService().submit(() -> {
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
                        GameEntityPortal portal = (GameEntityPortal) entity;
                        drawPortal(portal);
                        Feature feature = Feature.fromGeometry(Point.fromLngLat(portal.getPortalLocation().getLongitude(), portal.getPortalLocation().getLatitude()));
                        feature.addStringProperty("guid", portal.getEntityGuid());
                        features.add(feature);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Link) {
                        drawLink((GameEntityLink) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField) {
                        drawField((GameEntityControlField) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Item) {
                        GameEntityItem item = (GameEntityItem) entity;
                        drawItem(item);
                        Feature feature = Feature.fromGeometry(Point.fromLngLat(item.getItem().getItemLocation().getLongitude(), item.getItem().getItemLocation().getLatitude()));
                        feature.addStringProperty("guid", item.getEntityGuid());
                        features.add(feature);
                    }
                });

            }

            uiHandler.post(() ->
                    addTouchTargets(features));

            mLastScan = System.currentTimeMillis() + mMinUpdateIntervalMS;
        });

    }

    private void drawXMParticles() {
        // draw xm particles (as groundoverlays)
        Map<Long, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        Set<Long> keys = xmParticles.keySet();

        Style style = mMapLibreMap.getStyle();
        if (style == null) {
            return;
        }
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        for (Long key : keys) {
            XMParticle particle = xmParticles.get(key);
            assert particle != null;

            String sourceId = "particle-source-" + particle.getCellId();
            String layerId = "particle-layer-" + particle.getCellId();


            activity.runOnUiThread(() -> {
                // only draw if not already in list
                if (style.getLayer(layerId) == null) {
                    LatLngQuad quad = getRotatedLatLngQuad(particle.getCellLocation(), 10, 10, 0);
                    ImageSource imageSource = new ImageSource(sourceId, quad, mIcons.get("particle"));
                    style.addSource(imageSource);
                    RasterLayer rasterLayer = new RasterLayer(layerId, sourceId);
                    style.addLayer(rasterLayer);
                }
            });
        }
    }

    private void drawPortal(@NonNull final GameEntityPortal portal) {
        final Team team = portal.getPortalTeam();
        if (mMapView != null) {
            String guid = portal.getEntityGuid();
            String layerName = "portal-" + guid + "-layer";
            String sourceName = "portal-" + guid;

            final Location location = portal.getPortalLocation();

            ActivityMain activity = (ActivityMain) getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                // TODO: make portal marker display portal health/deployment info (opacity x white, use shield image etc)
                // it's quite possible that resonators can live in a separate Hash of markers,
                //   as long as the guids are stored with the portal info
                Bitmap portalIcon = mIcons.get(team.toString());
                assert portalIcon != null;
                mMapLibreMap.getStyle(style -> {

                    ImageSource imageSource = (ImageSource) style.getSource(sourceName);
                    if (imageSource == null) {
                        imageSource = new ImageSource(sourceName, getRotatedLatLngQuad(location, PORTAL_DIAMETER_METRES, PORTAL_DIAMETER_METRES, location.getLatitude() % 360), portalIcon);
                        style.addSource(imageSource);
                    } else {
                        imageSource.setImage(portalIcon);
                    }

                    RasterLayer rasterLayer = (RasterLayer) style.getLayer(layerName);
                    if (rasterLayer == null) {
                        style.addLayer(new RasterLayer(layerName, sourceName).withProperties(
//                            PropertyFactory.rasterOpacity(getPortalOpacity(portal)),
                                PropertyFactory.rasterSaturation(getPortalOpacity(portal) - 1)
//                            PropertyFactory.rasterContrast(getPortalOpacity(portal))
                        ));
                    } else {
                        rasterLayer.setProperties(PropertyFactory.rasterSaturation(getPortalOpacity(portal) - 1));
                    }
                });

                for (var reso : portal.getPortalResonators()) {
                    if (reso != null) {
                        drawResonatorForPortal(portal, reso);
                    }
                }
            });

        }
    }

    // Helper method to calculate portal opacity ... or saturation, or something idk
    private float getPortalOpacity(GameEntityPortal portal) {
        if (portal.getPortalMaxEnergy() == 0) {
            return 0;
        }
        float healthRatio = (float) portal.getPortalEnergy() / (float) portal.getPortalMaxEnergy();
        // Clamp the value to avoid fully invisible portals
        return Math.max(0.45f, healthRatio);
//        return healthRatio;
    }

    public void removeInfoCard() {
        // Remove the info card symbol if it exists
        if (mMarkerInfoCard != null) {
            mSymbolManager.delete(mMarkerInfoCard);
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
        textView1.setTextColor(0xFF000000 + getColourFromResources(getResources(), getLevelColour(portal.getPortalLevel())));
        textView2.setText(R.string.portal);
        textView2.setTextColor(0xFF000000 + Objects.requireNonNull(mGame.getKnobs().getTeamKnobs().getTeams().get(portal.getPortalTeam().toString())).getColour());
        textView3.setText(portal.getPortalTitle());
        int dist = (int) mCurrentLocation.distanceTo(portal.getPortalLocation());
        textview4.setText(String.format(Locale.getDefault(), "Distance: %dm", dist));


        Bitmap bitmap = createDrawableFromView(requireContext(), markerView);
        Objects.requireNonNull(mMapLibreMap.getStyle()).addImage("info_card", bitmap);
        mMarkerInfoCard = mSymbolManager.create(new SymbolOptions()
                .withLatLng(new LatLng(portal.getPortalLocation().getLatitude(), portal.getPortalLocation().getLongitude()))
                .withIconImage("info_card")
                .withIconSize(1.0f));
    }

    private void drawResonatorForPortal(GameEntityPortal portal, GameEntityPortal.LinkedResonator reso) {
        if (portal == null || reso == null) {
            return;
        }

        Style style = mMapLibreMap.getStyle();
        assert style != null;

        final double LINKED_RESO_SCALE = 3;
        float saturation = -0.85f * (0.95f - (float) reso.energyTotal / (float) reso.getMaxEnergy());

        if (mResonatorToPortalSlotLookup.containsKey(reso.id)) {
            Objects.requireNonNull(style.getLayer("reso-layer-" + reso.id)).setProperties(PropertyFactory.rasterContrast(saturation));
            return;
        }


        // Remove existing marker if present
        var m = mResonatorThreads.get(portal.getEntityGuid());
        if (m != null && m.containsKey(reso.slot)) {
            Line resoLine = Objects.requireNonNull(m.get(reso.slot));
            mLineManager.delete(resoLine);
            m.remove(reso.slot);
        }
//        if (mResonatorToPortalSlotLookup.containsKey(reso.id)) {
//            mResonatorToPortalSlotLookup.remove(reso.id);
//            style.removeLayer("reso-layer-" + reso.id);
//            style.removeSource("reso-source-" + reso.id);
//        }

        // Update markers map
        HashMap<Integer, Line> markers;
        if (mResonatorThreads.containsKey(portal.getEntityGuid())) {
            markers = mResonatorThreads.get(portal.getEntityGuid());
        } else {
            markers = new HashMap<>();
            mResonatorThreads.put(portal.getEntityGuid(), markers);
        }
        assert markers != null;

        // Calculate positions
        Location resoPos = reso.getResoLocation();
        ImageSource rasterSource = (ImageSource) style.getSource("reso-source-" + reso.id);
        if (rasterSource == null) {
            // Position the image using its coordinates (longitude, latitude)
            LatLngQuad quad = getRotatedLatLngQuad(resoPos, LINKED_RESO_SCALE, LINKED_RESO_SCALE, reso.slot * 45);
            rasterSource = new ImageSource("reso-source-" + reso.id, quad, mIcons.get("r" + reso.level));
            style.addSource(rasterSource);
        } else {
            rasterSource.setImage(mIcons.get("r" + reso.level));
        }

        RasterLayer portalImageLayer = (RasterLayer) style.getLayer("reso-layer-" + reso.id);
        if (portalImageLayer == null) {
            portalImageLayer = new RasterLayer("reso-layer-" + reso.id, "reso-source-" + reso.id)
                    .withProperties(
                            PropertyFactory.rasterContrast(saturation)
                    );
            style.addLayer(portalImageLayer);
        } else {
            portalImageLayer.setProperties(PropertyFactory.rasterContrast(saturation));
        }


        int rgb = getColourFromResources(getResources(), getLevelColour(reso.level));
        // set opacity to half
        rgb = (rgb & 0x00FFFFFF) | ((int) Math.max(48, ((float) reso.energyTotal / (float) reso.getMaxEnergy()) * (float) 128) << 24);

        LineOptions lineOptions = new LineOptions()
                .withLatLngs(Arrays.asList(portal.getPortalLocation().getLatLng(), resoPos.getLatLng()))
                .withLineColor(getRgbaStringFromColour(rgb))
                .withLineWidth(0.5f);

        markers.put(reso.slot, mLineManager.create(lineOptions));
        mResonatorToPortalSlotLookup.put(reso.id, new Pair<>(portal.getEntityGuid(), reso.slot));
    }


    private void drawLink(final GameEntityLink link) {
        if (mMapView == null) {
            return;
        }
        Style style = mMapLibreMap.getStyle();
        assert style != null;

        if (mLines.contains(link.getEntityGuid())) {
            // maybe update ?
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }


        final Location origin = link.getLinkOriginLocation();
        final Location dest = link.getLinkDestinationLocation();

        activity.runOnUiThread(() -> {

            // TODO: decay link per portal health
            Team team = link.getLinkControllingTeam();
            int colour = 0xff000000 + team.getColour(); // adding opacity

            LineOptions lineOptions = new LineOptions()
                    .withLatLngs(Arrays.asList(origin.getLatLng(), dest.getLatLng()))
                    .withLineColor(getRgbaStringFromColour(colour))
                    .withLineWidth(0.2f);

            mLineManager.create(lineOptions);

            mLines.add(link.getEntityGuid());
        });
    }

    private void drawField(final GameEntityControlField field) {
        if (mMapView == null) {
            return;
        }
        if (mPolygons.contains(field.getEntityGuid())) {
            // maybe update ?
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }


        final Location vA = field.getFieldVertexA().getPortalLocation();
        final Location vB = field.getFieldVertexB().getPortalLocation();
        final Location vC = field.getFieldVertexC().getPortalLocation();
        activity.runOnUiThread(() -> {
            Team team = field.getFieldControllingTeam();
            int colour = 0x32000000 + team.getColour(); // adding alpha

            // Create a list with the vertices of the polygon
            List<LatLng> polygonLatLngs = Arrays.asList(vA.getLatLng(), vB.getLatLng(), vC.getLatLng());

            FillOptions polygonOptions = new FillOptions()
                    .withLatLngs(Collections.singletonList(polygonLatLngs))
                    .withFillColor(getRgbaStringFromColour(colour));

            mFillManager.create(polygonOptions);
            mPolygons.add(field.getEntityGuid());
        });

    }

    public int hashGuidToMod360(String guid) {
        int hexAsInt = Integer.parseInt(guid.substring(0, 3), 16);
        return hexAsInt % 360;
    }


    private void drawItem(GameEntityItem entity) {
        if (mMapView != null) {
            String guid = entity.getEntityGuid();
            String sourceId = "item-source-" + guid;
            String layerId = "item-layer-" + guid;

            Style style = mMapLibreMap.getStyle();
            if (style == null) {
                return;
            }

            if (style.getLayer(layerId) != null) {
                return;
            }

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

                LatLngQuad quad = getRotatedLatLngQuad(entity.getItem().getItemLocation(), 5, 5, hashGuidToMod360(entity.getEntityGuid()));
                assert portalIcon != null;
                ImageSource imageSource = new ImageSource(sourceId, quad, portalIcon);
                style.addSource(imageSource);
                RasterLayer rasterLayer = new RasterLayer(layerId, sourceId);
                style.addLayer(rasterLayer);
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
        return mMapView;
    }

    public void fireBurster(int radius) {
        addExpandingCircle(mCurrentLocation, radius * 10, radius, 0xCCCC9900, metresToPixels(10, mCurrentLocation));
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
            @SuppressWarnings("unchecked") HashMap<String, Integer> items = (HashMap<String, Integer>) hackResultBundle.getSerializable("items");
            @SuppressWarnings("unchecked") HashMap<String, Integer> bonusItems = (HashMap<String, Integer>) hackResultBundle.getSerializable("bonusItems");
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
        var actualPortal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(targetGuid);
        assert actualPortal != null;
        var actualReso = actualPortal.getPortalResonator(targetSlot);
        // if reso is deleted from gameworld before damage display, we put the damage on the portal
        LatLng location;
        int percentage;
        if (actualReso != null) {
            location = actualReso.getResoLatLng();
            // note that it is allowed to be more than 100%
            percentage = (int) ((float) damageAmount / (float) actualReso.getMaxEnergy() * 100);
        } else {
            location = actualPortal.getPortalLocation().getLatLng();
            percentage = 100;
        }
        SymbolOptions symbolOptions = new SymbolOptions()
                .withLatLng(location)
                .withTextField(String.format("%d%%", percentage) + (criticalHit ? "!" : ""))
                .withTextColor(getRgbaStringFromColour(0xCCF8C03E))
//                    .withTextSize(14.0f)
                ;

        Symbol damage = mSymbolManager.create(symbolOptions);
        new Handler().postDelayed(() -> mSymbolManager.delete(damage), 2000);

    }

    @SuppressLint("DefaultLocale")
    public void displayPlayerDamage(int amount, String attackerGuid) {

        // set up
        int colour = 0xCCF83030;

        GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(attackerGuid);
        assert portal != null;

        Location playerLocation = mGame.getLocation();

        // create the zap line for player damage
        LineOptions lineOptions = new LineOptions()
                .withLatLngs(Arrays.asList(portal.getPortalLocation().getLatLng(), playerLocation.getLatLng()))
                .withLineColor(getRgbaStringFromColour(colour))
//                .withLineWidth(0.2f)
                ;

        Line line = mLineManager.create(lineOptions);
//        // let it delete itself
        new Handler(Looper.getMainLooper()).postDelayed(() -> mLineManager.delete(line), 2000);

        // now write up the text, but only if the damage was significant

        // max energy or just regular energy?
        // it is once again allowed to be ore than 100%
        int percentage = (int) ((float) amount / (float) mGame.getAgent().getEnergyMax() * 100);
        if (percentage < 1) {
            return;
        }

        SymbolOptions symbolOptions = new SymbolOptions()
                .withLatLng(playerLocation.destinationPoint(10, 0).getLatLng())
                .withTextField(String.format("%d%%", percentage))
                .withTextColor(getRgbaStringFromColour(colour))
//                    .withTextSize(14.0f)
                ;

        Symbol damage = mSymbolManager.create(symbolOptions);
        new Handler().postDelayed(() -> mSymbolManager.delete(damage), 2000);

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

    private boolean onMapClick(LatLng point) {
        if (mIsZooming) {
            return false;
        }
        PointF screenPoint = mMapLibreMap.getProjection().toScreenLocation(point);
        List<Feature> things = mMapLibreMap.queryRenderedFeatures(screenPoint);
        List<GameEntityBase> hitList = new ArrayList<>();
        for (Feature thing : things) {
            if (thing.hasProperty("guid")) {
                String guid = thing.getStringProperty("guid");
                GameEntityBase item = mGame.getWorld().getGameEntities().get(guid);
                if (item != null) {
                    hitList.add(item);
                }
            }
        }
        switch (hitList.size()) {
            case 0:
                break;
            case 1:
                interactWithEntity(hitList.get(0));
                break;
            default:
                showGameEntityDialog(hitList);
        }

        return true;
    }

    private String getEntityDescription(GameEntityBase entity) {
        String desc;
        switch (entity.getGameEntityType()) {
            case Portal -> desc = "Portal: " + ((GameEntityPortal) entity).getPortalTitle();
            case Item -> desc = ((GameEntityItem) entity).getItem().getUsefulName();
            default -> desc = "Mysterious Object";
        }
        return desc;
    }

    private void showGameEntityDialog(List<GameEntityBase> gameEntities) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select an Item");

        // Create a list of names to display in the dialog
        String[] entityNames = new String[gameEntities.size()];
        for (int i = 0; i < gameEntities.size(); i++) {
            entityNames[i] = getEntityDescription(gameEntities.get(i));
        }

        builder.setItems(entityNames, (dialog, which) -> interactWithEntity(gameEntities.get(which)));

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void interactWithEntity(GameEntityBase entity) {
        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal) {
            ActivityMain activity = (ActivityMain) requireActivity();
            if (activity.isSelectingTargetPortal()) {
                activity.setTargetPortal(entity.getEntityGuid());
                showInfoCard((GameEntityPortal) entity);
                return;
            }
            Intent myIntent = new Intent(getContext(), ActivityPortal.class);
            mGame.setCurrentPortal((GameEntityPortal) entity);
            mPortalActivityResultLauncher.launch(myIntent);
            return;
        }
        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Item) {
            mGame.intPickupItem(entity.getEntityGuid(), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    SlimgressApplication.postPlainCommsMessage(error);
                } else {
                    SlimgressApplication.postPlainCommsMessage("Picked up a " + msg.getData().getString("description"));
                }
                return true;
            }));
        }
        Toast.makeText(requireContext(), "Interacting with: " + getEntityDescription(entity), Toast.LENGTH_SHORT).show();
    }

}

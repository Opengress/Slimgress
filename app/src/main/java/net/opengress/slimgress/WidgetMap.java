package net.opengress.slimgress;

import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_MAP_TILE_SOURCE_BLANK;
import static net.opengress.slimgress.ViewHelpers.getBitmapFromAsset;
import static net.opengress.slimgress.ViewHelpers.getBitmapFromDrawable;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getRgbaStringFromColour;
import static net.opengress.slimgress.ViewHelpers.getTintedImage;
import static org.maplibre.android.style.layers.PropertyFactory.circleColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleRadius;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.common.geometry.S2LatLng;

import net.opengress.slimgress.activity.ActivityMain;
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
import net.opengress.slimgress.api.Knobs.TeamKnobs;
import net.opengress.slimgress.positioning.LatLngPool;

import org.json.JSONException;
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
import org.maplibre.android.plugins.annotation.Fill;
import org.maplibre.android.plugins.annotation.FillManager;
import org.maplibre.android.plugins.annotation.FillOptions;
import org.maplibre.android.plugins.annotation.Line;
import org.maplibre.android.plugins.annotation.LineManager;
import org.maplibre.android.plugins.annotation.LineOptions;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.ImageSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;
import org.maplibre.geojson.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class WidgetMap extends Fragment {
    // ===========================================================
    // Hardcore internal stuff
    // ===========================================================
    protected final SlimgressApplication mApp = SlimgressApplication.getInstance();
    protected final GameState mGame = mApp.getGame();
    protected final HashMap<String, Bitmap> mIcons = new HashMap<>();

    // for getting rid of the resonators when we get rid of the portals
    protected final HashMap<String, HashMap<String, Line>> mResonatorThreads = new HashMap<>();
    // for finding the portal marker when we delete it by Guid
    protected final HashMap<String, String> mResonatorToPortalLookup = new HashMap<>();
    protected final HashMap<String, Line> mLines = new HashMap<>();
    protected final HashMap<String, Fill> mPolygons = new HashMap<>();
    protected final int MAP_ROTATION_ARBITRARY = 1;
    protected final int MAP_ROTATION_FLOATING = 2;
    // ===========================================================
    // Map basics
    // ===========================================================
    final int PORTAL_DIAMETER_METRES = 40;
    private final LatLngPool mLatLngPool = new LatLngPool();
    protected MapView mMapView = null;
    protected MapLibreMap mMapLibreMap;
    protected SymbolManager mSymbolManager;
    protected LineManager mLineManager;
    protected FillManager mFillManager;
    protected String mCurrentTileSource = UNTRANSLATABLE_MAP_TILE_SOURCE_BLANK;
    protected ActivityResultLauncher<Intent> mPortalActivityResultLauncher;
    // ===========================================================
    // Fields and permissions
    // ===========================================================
    protected SharedPreferences mPrefs;
    protected Location mCurrentLocation = null;
    // FIXME this should be used to set "location inaccurate" if updates mysteriously stop
    protected Date mLastLocationAcquired = null;
    protected int CURRENT_MAP_ORIENTATION_SCHEME = MAP_ROTATION_ARBITRARY;
    protected boolean mIsMapEnabled = false;
    GeoJsonSource mPortalGeoJsonSource;
    private MapLibreMap.OnCameraIdleListener mOnCameraIdleListener;

    // ===========================================================
    // UX/UI Stuff - Events
    // ===========================================================
    private boolean mIsRotating = false;
    protected boolean mIsZooming = false;
    private GestureDetector mGestureDetector;
    private double mPrevAngle = 0;
    private float mStartY = 0;
    private final float ZOOM_SENSITIVITY = 0.1f;
    private boolean mIsClickingCompass = false;

    // Function to calculate LatLngQuad for the given radius in meters
    protected LatLngQuad getRadialLatLngQuad(Location center, double radiusMeters) {
        // Obtain LatLng instances from the pool
        LatLng topLeft = mLatLngPool.obtain();
        LatLng topRight = mLatLngPool.obtain();
        LatLng bottomRight = mLatLngPool.obtain();
        LatLng bottomLeft = mLatLngPool.obtain();

        // Calculate offsets and update LatLng instances
        calculateOffset(center, -radiusMeters, radiusMeters, topLeft);
        calculateOffset(center, radiusMeters, radiusMeters, topRight);
        calculateOffset(center, radiusMeters, -radiusMeters, bottomRight);
        calculateOffset(center, -radiusMeters, -radiusMeters, bottomLeft);

        // Create LatLngQuad (assuming it can accept mutable LatLng instances)
        LatLngQuad quad = new LatLngQuad(topLeft, topRight, bottomRight, bottomLeft);

        // Return LatLng instances to the pool after use (if they are not needed elsewhere)
        mLatLngPool.recycle(topLeft);
        mLatLngPool.recycle(topRight);
        mLatLngPool.recycle(bottomRight);
        mLatLngPool.recycle(bottomLeft);

        return quad;
    }

    // Helper function to calculate the offset LatLng
    private void calculateOffset(@NonNull Location center, double offsetX, double offsetY, @NonNull LatLng result) {
        // Perform calculations
        double latOffset = offsetY / S2LatLng.EARTH_RADIUS_METERS * (180 / Math.PI);
        double lngOffset = offsetX / (S2LatLng.EARTH_RADIUS_METERS *
                Math.cos(Math.toRadians(center.getLatitude()))) * (180 / Math.PI);

        // Update the result LatLng instance
        result.setLatitude(center.getLatitude() + latOffset);
        result.setLongitude(center.getLongitude() + lngOffset);
    }

    // Function to calculate a rotated LatLngQuad based on bearing
    protected LatLngQuad getRotatedLatLngQuad(Location center, double width, double height, double bearing) {
        // Convert bearing from degrees to radians
        bearing = ((bearing % 360) + 360) % 360;
        double bearingRad = Math.toRadians(bearing);

        double halfWidth = width / 2;
        double halfHeight = height / 2;

        // Obtain LatLng instances from the pool
        LatLng topLeft = mLatLngPool.obtain();
        LatLng topRight = mLatLngPool.obtain();
        LatLng bottomRight = mLatLngPool.obtain();
        LatLng bottomLeft = mLatLngPool.obtain();

        // Calculate rotated points and update LatLng instances
        calculateRotatedPoint(center, -halfWidth, halfHeight, bearingRad, topLeft);
        calculateRotatedPoint(center, halfWidth, halfHeight, bearingRad, topRight);
        calculateRotatedPoint(center, halfWidth, -halfHeight, bearingRad, bottomRight);
        calculateRotatedPoint(center, -halfWidth, -halfHeight, bearingRad, bottomLeft);

        // Create LatLngQuad
        LatLngQuad quad = new LatLngQuad(topLeft, topRight, bottomRight, bottomLeft);

        // Return LatLng instances to the pool after use
        mLatLngPool.recycle(topLeft);
        mLatLngPool.recycle(topRight);
        mLatLngPool.recycle(bottomRight);
        mLatLngPool.recycle(bottomLeft);

        return quad;
    }

    // Helper function to calculate the rotated point around the center
    private void calculateRotatedPoint(Location center, double offsetX, double offsetY, double bearingRad, LatLng result) {
        // Apply rotation matrix
        double rotatedX = offsetX * Math.cos(bearingRad) - offsetY * Math.sin(bearingRad);
        double rotatedY = offsetX * Math.sin(bearingRad) + offsetY * Math.cos(bearingRad);

        // Calculate offset and update the result LatLng
        calculateOffset(center, rotatedX, rotatedY, result);
    }

    protected float metresToPixels(double meters, @NonNull Location location) {
        return (float) (meters / mMapLibreMap.getProjection().getMetersPerPixelAtLatitude(location.getLatitude()));
    }

    private float metresToPixels(double meters, @NonNull LatLng location) {
        return (float) (meters / mMapLibreMap.getProjection().getMetersPerPixelAtLatitude(location.getLatitude()));
    }

    protected String getMapTileProviderStyleJSON(String name) {
        try {
            return mGame.getKnobs().getMapCompositionRootKnobs().getMapProvider(name).getStyleJSON();
        } catch (JSONException | NullPointerException e) {
            return "{}";
        }
    }

    protected void setUpStyleForMap(MapLibreMap mapLibreMap, @NonNull Style style) {

        if (style.getSource("portal-data-layer") == null) {
            mPortalGeoJsonSource = new GeoJsonSource("portal-data-layer");
            style.addSource(mPortalGeoJsonSource);
        }

        String flashLayerId = "flash-overlay-layer";
        String flashSourceId = "flash-overlay-source";

        if (style.getSource(flashLayerId) == null) {
            List<Point> worldCoordinates = Arrays.asList(
                    Point.fromLngLat(-180, 90),    // Top-left
                    Point.fromLngLat(180, 90),     // Top-right
                    Point.fromLngLat(180, -90),    // Bottom-right
                    Point.fromLngLat(-180, -90),   // Bottom-left
                    Point.fromLngLat(-180, 90)     // Closing the polygon
            );

            // Create world-covering polygon
            Polygon worldPolygon = Polygon.fromLngLats(Collections.singletonList(worldCoordinates));
            style.addSource(new GeoJsonSource(flashSourceId, FeatureCollection.fromFeature(Feature.fromGeometry(worldPolygon))));

            style.addLayerBelow(new FillLayer(flashLayerId, flashSourceId).withProperties(PropertyFactory.fillColor("rgba(255, 255, 255, 0.0)")), "player-cursor-image");

        }

        CircleLayer circleLayer = (CircleLayer) style.getLayer("portal-hit-layer");
        if (circleLayer == null) {
            circleLayer = new CircleLayer("portal-hit-layer", "portal-data-layer");
            circleLayer.setProperties(
                    circleRadius(30f),
                    circleColor("rgba(255, 0, 0, 0)")
            );
            style.addLayer(circleLayer);
        }

        mMapLibreMap.addOnCameraIdleListener(getOnCameraIdleListener(circleLayer));

        mSymbolManager = new SymbolManager(mMapView, mMapLibreMap, style);
        mSymbolManager.setIconAllowOverlap(true);
        mSymbolManager.setTextAllowOverlap(true);
        mSymbolManager.setIconIgnorePlacement(true);
        mSymbolManager.setTextIgnorePlacement(true);

        mLineManager = new LineManager(mMapView, mapLibreMap, style);
        mFillManager = new FillManager(mMapView, mapLibreMap, style);

    }

    private MapLibreMap.OnCameraIdleListener getOnCameraIdleListener(CircleLayer finalCircleLayer) {
        if (mOnCameraIdleListener != null) {
            mMapLibreMap.removeOnCameraIdleListener(mOnCameraIdleListener);
        }
        mOnCameraIdleListener = () -> {
            float radiusInPixels = metresToPixels((double) PORTAL_DIAMETER_METRES / 3, Objects.requireNonNull(mMapLibreMap.getCameraPosition().target));
            finalCircleLayer.setProperties(circleRadius(radiusInPixels));
        };
        return mOnCameraIdleListener;
    }

    protected void addTouchTargets(List<Feature> features) {
        mPortalGeoJsonSource.setGeoJson(FeatureCollection.fromFeatures(features));
    }

    protected void loadAssets() {
        AssetManager assetManager = requireActivity().getAssets();
        Map<String, TeamKnobs.TeamType> teams = mGame.getKnobs().getTeamKnobs().getTeams();
        for (String team : teams.keySet()) {
            mIcons.put(team, getTintedImage("portalTexture_NEUTRAL.webp", 0xff000000 + Objects.requireNonNull(teams.get(team)).getColour(), assetManager));
        }

        mIcons.put("particle", getBitmapFromAsset("particle.webp", assetManager));
        mIcons.put("actionradius", getBitmapFromAsset("actionradius.png", assetManager));
        mIcons.put("playercursor", getTintedImage("playercursor.webp", 0xff000000 + Objects.requireNonNull(mGame.getAgent().getTeam()).getColour(), assetManager));
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
        mIcons.put("bursterRing", getBitmapFromAsset("rainbowburst.webp", assetManager));
        Bitmap bitmap = Bitmap.createBitmap((int) (float) 1024, (int) (float) 1024, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((float) 3);
        paint.setAntiAlias(true);
        paint.setColor(0x33FFFF00);
        new Canvas(bitmap).drawCircle((float) 1024 / 2, (float) 1024 / 2, (float) 1024 / 2, paint);
        mIcons.put("sonarRing", bitmap);
    }

    protected void drawXMParticles() {
        // draw xm particles (as groundoverlays)
        Map<Long, XMParticle> xmParticles = mGame.getWorld().getXMParticles();

        Style style = mMapLibreMap.getStyle();
        if (style == null) {
            return;
        }
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        for (Long key : xmParticles.keySet()) {
            XMParticle particle = xmParticles.get(key);
            assert particle != null;

            String sourceId = "particle-source-" + particle.getCellId();
            String layerId = "particle-layer-" + particle.getCellId();


            activity.runOnUiThread(() -> {
                // only draw if not already in list
                if (style.getLayer(layerId) == null) {
                    LatLngQuad quad = getRotatedLatLngQuad(particle.getCellLocation(), 10, 10, 0);
                    ImageSource imageSource = new ImageSource(sourceId, quad, Objects.requireNonNull(mIcons.get("particle")));
                    style.addSource(imageSource);
                    RasterLayer rasterLayer = new RasterLayer(layerId, sourceId);
                    style.addLayer(rasterLayer);
                }
            });
        }
    }

    protected void drawPortal(@NonNull final GameEntityPortal portal) {
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
                        imageSource = new ImageSource(sourceName, getRotatedLatLngQuad(location, PORTAL_DIAMETER_METRES, PORTAL_DIAMETER_METRES, location.getLatitudeE6() % 360), portalIcon);
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
    private float getPortalOpacity(@NonNull GameEntityPortal portal) {
        if (portal.getPortalMaxEnergy() == 0) {
            return 0;
        }
        float healthRatio = (float) portal.getPortalEnergy() / (float) portal.getPortalMaxEnergy();
        // Clamp the value to avoid fully invisible portals
        return Math.max(0.45f, healthRatio);
//        return healthRatio;
    }

    private void drawResonatorForPortal(GameEntityPortal portal, GameEntityPortal.LinkedResonator reso) {
        if (portal == null || reso == null) {
            return;
        }

        Location resoPos = reso.getResoLocation();

        Style style = mMapLibreMap.getStyle();
        assert style != null;

        float saturation = -0.85f * (0.95f - (float) reso.energyTotal / (float) reso.getMaxEnergy());
        int rgb = getColourFromResources(getResources(), getLevelColour(reso.level));
        // set opacity
        rgb = (rgb & 0x00FFFFFF) | ((int) Math.max(48, ((float) reso.energyTotal / (float) reso.getMaxEnergy()) * (float) 128) << 24);

        if (mResonatorToPortalLookup.containsKey(reso.id) && style.getLayer("reso-layer-" + reso.id) != null) {
            Objects.requireNonNull(style.getLayer("reso-layer-" + reso.id)).setProperties(PropertyFactory.rasterContrast(saturation));
            Objects.requireNonNull(Objects.requireNonNull(mResonatorThreads.get(portal.getEntityGuid())).get(reso.id)).setLineColor(getRgbaStringFromColour(rgb));
            return;
        }


        // Remove existing marker if present
        var m = mResonatorThreads.get(portal.getEntityGuid());
        if (m != null && m.containsKey(reso.id)) {
            Line resoLine = Objects.requireNonNull(m.get(reso.id));
            mLineManager.delete(resoLine);
            m.remove(reso.id);
            mResonatorToPortalLookup.remove(reso.id);
        }

        // Update threads map
        HashMap<String, Line> threads;
        if (mResonatorThreads.containsKey(portal.getEntityGuid())) {
            threads = mResonatorThreads.get(portal.getEntityGuid());
        } else {
            threads = new HashMap<>();
            mResonatorThreads.put(portal.getEntityGuid(), threads);
        }
        assert threads != null;

        // Calculate positions
        ImageSource rasterSource = (ImageSource) style.getSource("reso-source-" + reso.id);
        if (rasterSource == null) {
            // Position the image using its coordinates (longitude, latitude)
            LatLngQuad quad = getRotatedLatLngQuad(resoPos, 3, 3, reso.slot * 45);
            rasterSource = new ImageSource("reso-source-" + reso.id, quad, Objects.requireNonNull(mIcons.get("r" + reso.level)));
            style.addSource(rasterSource);
        } else {
            rasterSource.setImage(Objects.requireNonNull(mIcons.get("r" + reso.level)));
        }

        RasterLayer resoImageLayer = (RasterLayer) style.getLayer("reso-layer-" + reso.id);
        if (resoImageLayer == null) {
            resoImageLayer = new RasterLayer("reso-layer-" + reso.id, "reso-source-" + reso.id)
                    .withProperties(
                            PropertyFactory.rasterContrast(saturation)
                    );
            style.addLayer(resoImageLayer);
        } else {
            resoImageLayer.setProperties(PropertyFactory.rasterContrast(saturation));
        }

        LineOptions lineOptions = new LineOptions()
                .withLatLngs(Arrays.asList(portal.getPortalLocation().getLatLng(), resoPos.getLatLng()))
                .withLineColor(getRgbaStringFromColour(rgb))
                .withLineWidth(0.5f);

        threads.put(reso.id, mLineManager.create(lineOptions));
        mResonatorToPortalLookup.put(reso.id, portal.getEntityGuid());
    }

    protected void drawLink(final GameEntityLink link) {
        if (mMapView == null) {
            return;
        }
        Style style = mMapLibreMap.getStyle();
        assert style != null;

        if (mLines.containsKey(link.getEntityGuid())) {
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

            mLines.put(link.getEntityGuid(), mLineManager.create(lineOptions));
        });
    }

    protected void drawField(final GameEntityControlField field) {
        if (mMapView == null) {
            return;
        }
        if (mPolygons.containsKey(field.getEntityGuid())) {
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

            mPolygons.put(field.getEntityGuid(), mFillManager.create(polygonOptions));
        });

    }

    public int hashGuidToMod360(@NonNull String guid) {
        int hexAsInt = Integer.parseInt(guid.substring(0, 3), 16);
        return hexAsInt % 360;
    }

    protected void drawItem(GameEntityItem entity) {
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

    protected void setUpTileSource() {
        String styleJSON = getMapTileProviderStyleJSON(mCurrentTileSource);
        assert styleJSON != null;
        mMapLibreMap.setStyle(new Style.Builder().fromJson(styleJSON), style -> {
            setUpStyleForMap(mMapLibreMap, style);
            updateScreen(new Handler(Looper.getMainLooper()));
        });
    }

    public MapView getMap() {
        return mMapView;
    }

    protected String getEntityDescription(@NonNull GameEntityBase entity) {
        String desc;
        switch (entity.getGameEntityType()) {
            case Portal -> desc = "Portal: " + ((GameEntityPortal) entity).getPortalTitle();
            case Item -> desc = ((GameEntityItem) entity).getItem().getUsefulName();
            default -> desc = "Mysterious Object";
        }
        return desc;
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
                        Feature feature = Feature.fromGeometry(portal.getPortalLocation().getPoint());
                        feature.addStringProperty("guid", portal.getEntityGuid());
                        features.add(feature);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Link) {
                        drawLink((GameEntityLink) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField) {
                        drawField((GameEntityControlField) entity);
                    } else if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Item) {
                        GameEntityItem item = (GameEntityItem) entity;
                        drawItem(item);
                        Feature feature = Feature.fromGeometry(item.getItem().getItemLocation().getPoint());
                        feature.addStringProperty("guid", item.getEntityGuid());
                        features.add(feature);
                    }
                });

            }

            uiHandler.post(() ->
                    addTouchTargets(features));

        });

    }

    @SuppressLint("ClickableViewAccessibility")
    void setMapEnabled(boolean bool) {
        mIsMapEnabled = bool;
        // there's probably a better way
        if (bool) {
            mMapView.setOnTouchListener(null);
        } else {
            mMapView.setOnTouchListener((v, event) -> true);
        }
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
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    public void onReceiveDeletedEntityGuids(List<String> deletedEntityGuids) {
        if (mMapLibreMap == null || mMapLibreMap.getStyle() == null) {
            return;
        }
        // suddenly i understand why each guid in other games has a suffix indicating the type
        for (String guid : deletedEntityGuids) {
            AtomicBoolean shouldContinue = new AtomicBoolean(true);

            // for XM particles
            if (guid.endsWith(".6")) {
                long particle = Long.parseLong(guid.substring(0, 16), 16);
                mMapLibreMap.getStyle(style -> {
                    style.removeLayer("particle-layer-" + particle);
                    style.removeSource("particle-source-" + particle);
                });
                continue;
            }

            // for portals
            mMapLibreMap.getStyle(style -> {
                style.removeLayer("portal-" + guid + "-layer");
                shouldContinue.set(style.removeSource("portal-" + guid));
                if (shouldContinue.get() && mResonatorThreads.containsKey(guid)) {
                    for (var k : Objects.requireNonNull(mResonatorThreads.get(guid)).keySet()) {
                        var resoParts = Objects.requireNonNull(mResonatorThreads.get(guid)).get(k);

                        if (resoParts != null) {
                            mLineManager.delete(resoParts);
                        }
                        mResonatorToPortalLookup.remove(k);
                        Objects.requireNonNull(mResonatorThreads.get(guid)).remove(k);
                        style.removeLayer("reso-layer-" + k);
                        style.removeSource("reso-source-" + k);
                    }
                }
            });
            if (shouldContinue.get()) {
                continue;
            }

            // for resonators
            if (mResonatorToPortalLookup.containsKey(guid)) {
                var portal = Objects.requireNonNull(mResonatorToPortalLookup.get(guid));
                var resoParts = Objects.requireNonNull(mResonatorThreads.get(portal)).get(guid);

                if (resoParts != null) {
                    mLineManager.delete(resoParts);
                }
                mResonatorToPortalLookup.remove(guid);
                Objects.requireNonNull(mResonatorThreads.get(portal)).remove(guid);
                mMapLibreMap.getStyle(style -> {
                    style.removeLayer("reso-layer-" + guid);
                    style.removeSource("reso-source-" + guid);
                });
                continue;
            }

            // for links
            if (mLines.containsKey(guid)) {
                mLineManager.delete(mLines.get(guid));
                mLines.remove(guid);
                continue;
            }

            // for fields
            if (mPolygons.containsKey(guid)) {
                mFillManager.delete(mPolygons.get(guid));
                mPolygons.remove(guid);
                continue;
            }

            // for dropped items
            mMapLibreMap.getStyle(style -> {
                style.removeLayer("item-layer-" + guid);
                style.removeSource("item-source-" + guid);
            });

        }
    }

    @Override
    public void onDestroyView() {
        mMapView.onDestroy();
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();

        String newTileSource = mPrefs.getString(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT);
        if (!Objects.equals(newTileSource, mCurrentTileSource)) {
            mCurrentTileSource = newTileSource;
            setUpTileSource();
        }

        if (getActivity() != null) {
            ((ActivityMain) getActivity()).updateAgent();
        }
    }

    @Override
    public void onPause() {
        // Get the current camera position
        if (mMapLibreMap != null) {
            CameraPosition cameraPosition = mMapLibreMap.getCameraPosition();
            SharedPreferences.Editor editor = mPrefs.edit();

            editor.putFloat("camera_bearing", (float) cameraPosition.bearing);
            assert cameraPosition.target != null;
            editor.putFloat("camera_latitude", (float) cameraPosition.target.getLatitude());
            editor.putFloat("camera_longitude", (float) cameraPosition.target.getLongitude());
            editor.putFloat("camera_tilt", (float) cameraPosition.tilt);
            editor.putFloat("camera_zoom", (float) cameraPosition.zoom);
            editor.putInt("orientation_scheme", CURRENT_MAP_ORIENTATION_SCHEME);

            editor.apply();
        }
        mMapView.onPause();
        super.onPause();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        MapLibre.getInstance(requireContext(), "", WellKnownTileServer.MapLibre);

        View rootView = inflater.inflate(R.layout.fragment_scanner, container, false);
        mMapView = rootView.findViewById(R.id.mapView);

        mPrefs = mApp.getApplicationContext().getSharedPreferences(requireActivity().getApplicationInfo().packageName, Context.MODE_PRIVATE);

        loadAssets();

        mCurrentTileSource = mPrefs.getString(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT);
        // COULD use setUpTileSource - but do not need updateScreen() so maybe not ??
        String styleJSON = getMapTileProviderStyleJSON(mCurrentTileSource);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(mapLibreMap -> {
            mMapLibreMap = mapLibreMap;
            mMapLibreMap.setMinZoomPreference(16);
            mMapLibreMap.setMaxZoomPreference(22);

//            mMapLibreMap.setStyle(new Style.Builder().fromJson(styleJSON), style -> setUpStyleForMap(mapLibreMap, style));
            mMapLibreMap.setStyle(new Style.Builder().fromJson(styleJSON), style -> setUpStyleForMap(mapLibreMap, style));
            // Retrieve saved camera properties
            float bearing = mPrefs.getFloat("camera_bearing", 0f);
            double latitude = mPrefs.getFloat("camera_latitude", 0f);
            double longitude = mPrefs.getFloat("camera_longitude", 0f);
            float tilt = mPrefs.getFloat("camera_tilt", 0f);
            float zoom = mPrefs.getFloat("camera_zoom", 18f);
            CURRENT_MAP_ORIENTATION_SCHEME = mPrefs.getInt("orientation_scheme", CURRENT_MAP_ORIENTATION_SCHEME);

            // Build the saved camera position
            Location initialLocation = new Location(latitude, longitude);
            CameraPosition position = new CameraPosition.Builder()
                    .target(initialLocation.getLatLng())
                    .bearing(bearing)
                    .tilt(tilt)
                    .zoom(zoom)
                    .build();

            // Set camera position on the map
            mMapLibreMap.setCameraPosition(position);

            setupPlayerCursor(initialLocation, (int) bearing);

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

            mMapLibreMap.getUiSettings().setAttributionEnabled(false);
            mMapLibreMap.getUiSettings().setLogoEnabled(false);

            mMapLibreMap.addOnMapLongClickListener(point -> {
                if (!mIsMapEnabled) {
                    return true;
                }
                if (mIsRotating || mIsZooming || mIsClickingCompass) {
                    mIsClickingCompass = false;
                    return false;
                }
                ((ActivityMain) requireActivity()).showFireMenu(point);
                return true;
            });

            mMapLibreMap.addOnMapClickListener(this::onMapClick);
        });

        mGestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (!mIsMapEnabled) {
                    return true;
                }
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
                if (!mIsMapEnabled) {
                    return true;
                }
                mIsZooming = true;
                return true;
            }
        });

// Set a touch listener on the overlay to intercept gestures
        rootView.findViewById(R.id.gestureOverlay).setOnTouchListener((v, event) -> {
            if (!mIsMapEnabled) {
                return true;
            }
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

        if (this instanceof ScannerView) {
            ((ActivityMain) requireActivity()).setScanner((ScannerView) this);
        }

        return rootView;
    }

    protected boolean onMapClick(LatLng ignored) {
        return false;
    }

    protected void setupPlayerCursor(Location ignoredLocation, int ignoredInt) {
    }


    boolean checkAndProcessCompassClick(@NonNull MotionEvent e) {
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

}

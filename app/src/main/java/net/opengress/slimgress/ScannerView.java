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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.WRAP_CONTENT;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE_DEFAULT;
import static net.opengress.slimgress.SlimgressApplication.postPlainCommsMessage;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getRgbaStringFromColour;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;
import static net.opengress.slimgress.api.Common.Utils.notBouncing;
import static net.opengress.slimgress.api.Item.ItemBase.ItemType.PortalKey;
import static net.opengress.slimgress.api.Knobs.MapCompositionRootKnobs.MapProvider.MapType.RASTER;
import static net.opengress.slimgress.api.Player.PlayerEntity.EnergyState.Depleted;
import static net.opengress.slimgress.net.NetworkMonitor.hasInternetConnectionCold;
import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;

import net.opengress.slimgress.activity.ActivityMain;
import net.opengress.slimgress.activity.ActivitySplash;
import net.opengress.slimgress.activity.FragmentPortal;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.XMParticle;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.api.Knobs.ScannerKnobs;
import net.opengress.slimgress.dialog.DialogHackResult;
import net.opengress.slimgress.net.NetworkMonitor;
import net.opengress.slimgress.positioning.AndroidBearingProvider;
import net.opengress.slimgress.positioning.AndroidLocationProvider;
import net.opengress.slimgress.positioning.LocationCallback;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngQuad;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Line;
import org.maplibre.android.plugins.annotation.LineOptions;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.ImageSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

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
import java.util.concurrent.TimeUnit;

public class ScannerView extends WidgetMap {

    // ===========================================================
    // Knobs quick reference
    // ===========================================================
    ScannerKnobs mScannerKnobs;
    private int mActionRadiusM = 40;
    private int mUpdateIntervalMS = 30000;
    private int mMinUpdateIntervalMS;
    private int mUpdateDistanceM;


    private LatLngQuad mPlayerCursorPosition;
    private final CameraPosition.Builder mCameraPositionBuilder = new CameraPosition.Builder();

    private long mLastScan = 0;
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


    // ===========================================================
    // UX/UI Stuff - Events
    // ===========================================================
    private Symbol mMarkerInfoCard;
    private long mCircleId = 1;
    private TextView mBigMessageText;

    // ===========================================================
    // Misc
    // ===========================================================
    private final Set<String> mSlurpableParticles = new HashSet<>();
    private final NetworkMonitor mNetworkMonitor = new NetworkMonitor();
    private final Handler mUpdateHandler = new Handler(Looper.getMainLooper());
    // This ensures that we have some idea of what is happening on the map even when we can't do anything
    private final Runnable mGetObjectsTask = new Runnable() {
        @Override
        public void run() {
            try {
                if (!mGame.isLocationAccurate() && mGame.getLocation() != null) {
                    updateWorld();
                }
            } catch (Exception e) {
                // not critical in here (yet)
            }
            mUpdateHandler.postDelayed(this, mUpdateIntervalMS);
        }
    };
    private final OnSharedPreferenceChangeListener mPreferenceChangeListener =
            (sharedPreferences, key) -> {
                if (Objects.equals(key, PREFS_DEVICE_TILE_SOURCE)) {
                    String newTileSource = sharedPreferences.getString(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT);
                    if (!Objects.equals(newTileSource, mCurrentTileSource)) {
                        mCurrentTileSource = newTileSource;
                        setUpTileSource();
                        mFieldFeatures.clear();
                        mLinkFeatures.clear();
                        // crashes because i don't currently clean out the rest of the portal details
//        mResonatorLineFeatures.clear();
                        mTouchTargetFeatures.clear();
                        drawEntities(mGame.getWorld().getGameEntitiesList());

                        mXmParticleFeatures.clear();
                        drawXMParticles(mGame.getWorld().getXMParticles().values());
                    }
                }
            };


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

    @Override
    public void onPause() {
        super.onPause();
        mUpdateHandler.removeCallbacks(mGetObjectsTask);
    }

    private void drawPlayerCursor() {
        // hardcoded and possibly incorrect, also not enough teams

        if (mapIsNotReady()) {
            return;
        }

        mPlayerCursorPosition = getRotatedLatLngQuad(mCurrentLocation, 25, 25, mBearing);
        mPlayerCursorImageSource.setCoordinates(mPlayerCursorPosition);

        if (CURRENT_MAP_ORIENTATION_SCHEME == MAP_ROTATION_ARBITRARY) {
            mMapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation.getLatLng(), mMapLibreMap.getCameraPosition().zoom));
        } else {
            mMapLibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(mCameraPositionBuilder
                    .target(mCurrentLocation.getLatLng())
                    .zoom(mMapLibreMap.getCameraPosition().zoom)
                    .bearing((360 - mBearing + 360) % 360)
                    .build()));
        }

        updateActionRadiusLocation(mCurrentLocation);
    }

    private boolean mapIsNotReady() {
        return mMapLibreMap == null || mPlayerCursorImageSource == null || mCurrentLocation == null || mMapLibreMap.getStyle() == null || !mMapLibreMap.getStyle().isFullyLoaded();
    }

    @Override
    public void setupPlayerCursor(Location initialLocation, int bearing) {
        if (mMapLibreMap.getStyle() == null) {
            return;
        }

        if (mPlayerCursorImageSource != null) {
            mPlayerCursorImageSource = null;
        }

        mPlayerCursorSource = new GeoJsonSource("player-cursor-source", Feature.fromGeometry(Point.fromLngLat(initialLocation.getLongitude(), initialLocation.getLatitude())));

        LatLngQuad rotatedQuad = getRotatedLatLngQuad(initialLocation, 25, 25, bearing);
        mPlayerCursorImageSource = new ImageSource("bearing-image-source", rotatedQuad, requireNonNull(mIcons.get("playercursor")));

        mMapLibreMap.getStyle(style -> {
            style.addSource(mPlayerCursorSource);
            style.addSource(mPlayerCursorImageSource);
            style.addLayer(new RasterLayer("player-cursor-image", "bearing-image-source"));
        });

        setupActionRadius(initialLocation);

    }

    public void setupActionRadius(Location initialLocation) {
        if (mMapLibreMap.getStyle() == null) {
            return;
        }
        LatLngQuad actionRadiusQuad = getRadialLatLngQuad(initialLocation, mActionRadiusM);
        ImageSource actionRadiusSource = new ImageSource("action-radius-source", actionRadiusQuad, requireNonNull(mIcons.get("actionradius")));
        mMapLibreMap.getStyle().addSource(actionRadiusSource);
        RasterLayer actionRadiusLayer = new RasterLayer("action-radius-layer", "action-radius-source").withProperties(
                PropertyFactory.rasterOpacity(0.5f)
        );
        mMapLibreMap.getStyle().addLayer(actionRadiusLayer);


        if (mGame.getLocation() == null) {
            mGame.updateLocation(initialLocation);
        }

        updateWorld();
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


    private void displayMyCurrentLocationOverlay(Location currentLocation) {
        long now = currentTimeMillis();

        if (mLastScan == 0 || mLastLocation == null || (now - mLastScan >= mUpdateIntervalMS) || (now - mLastScan >= mMinUpdateIntervalMS && mLastLocation.distanceTo(currentLocation) >= mUpdateDistanceM)) {
            if (mGame.getLocation() != null) {
                final Handler uiHandler = new Handler();
                uiHandler.post(() -> {
                    // FIXME you can probably use the debouncer now
                    // guard against scanning too fast if request fails
                    mLastScan = now + mMinUpdateIntervalMS;
                    updateWorld();
                });
            }
        }

        // guard against saving borked locations
        if (currentLocation == null) {
            return;
        }

        mLastLocationAcquired.setTime(currentTimeMillis());

        if (mLastLocation == null || !mLastLocation.equals(currentLocation)) {
            mLastLocation = currentLocation;
            drawPlayerCursor();
        }

        ActivityMain activity = (ActivityMain) getActivity();
        if (activity != null && activity.isSelectingTargetPortal()) {
            activity.updateTargetPortalFromSelection();
            return;
        }

        setLocationInaccurate(false);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        mPrefs.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        mBigMessageText = v.findViewById(R.id.big_message_text);
        return v;
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
        mLocationProvider = AndroidLocationProvider.getInstance(SlimgressApplication.getInstance());
        mBearingProvider.setBearingCallback(bearing -> updateBearing((int) bearing));
        mLocationProvider.addLocationCallback(new LocationCallback() {
            @Override
            public void onLocationUpdated(android.location.Location location) {
                slurp();
                mCurrentLocation = new Location(location.getLatitude(), location.getLongitude());
                mApp.getLocationViewModel().setLocationData(mCurrentLocation);
                mGame.updateLocation(mCurrentLocation);
                mLastLocationAcquired.setTime(currentTimeMillis());

                if (!mHaveRotationSensor && location.hasBearing()) {
                    mBearing = (int) location.getBearing();
                }

                displayMyCurrentLocationOverlay(mCurrentLocation);
            }

            @Override
            public void onUpdatesStarted() {
                // should this, or OnLocationUpdated, should setLocationInaccurate(false) by hitting displayMyLocation?
            }

            @Override
            public void onUpdatesStopped() {
                setLocationInaccurate(true);
            }
        });
        // NB setting it to accurate means the scanner notices the change and sets the message
        mGame.setLocationAccurate(true);
        setLocationInaccurate(true);
        mNetworkMonitor.registerNetworkMonitor(requireContext(),
                this::onLostConnection,
                this::onRegainedConnection
        );

        mApp.getUpdatedEntitiesViewModel().getEntities().observe(this, this::drawEntities);
        mApp.getUpdatedEntitiesViewModel().getParticles().observe(this, this::drawXMParticles);
        mApp.getDeletedEntityGuidsViewModel().getGuids().observe(this, this::onReceiveDeletedEntityGuids);

        mScannerKnobs = mGame.getKnobs().getScannerKnobs();
        mActionRadiusM = mScannerKnobs.getActionRadiusM();
        mUpdateIntervalMS = mScannerKnobs.getUpdateIntervalMS();
        mMinUpdateIntervalMS = mScannerKnobs.getMinUpdateIntervalMS();
        mUpdateDistanceM = mScannerKnobs.getUpdateDistanceM();

    }

    private void addExpandingCircle(Location centerPoint, int durationMs, float radiusM, Bitmap bm) {
        if (mMapLibreMap == null || mMapLibreMap.getStyle() == null) {
            return;
        }
        float radius = metresToPixels(radiusM, centerPoint);
        String sourceId = "circle-source-" + ++mCircleId;
        String layerId = "circle-layer-" + mCircleId;

        // Create the initial circular bitmap from scratch
        ImageSource imageSource = new ImageSource(sourceId, getRadialLatLngQuad(centerPoint, 0), bm);
        mMapLibreMap.getStyle().addSource(imageSource);

        RasterLayer circleLayer = new RasterLayer(layerId, sourceId);
        mMapLibreMap.getStyle().addLayer(circleLayer);

        final long startTime = currentTimeMillis();
        final Choreographer choreographer = Choreographer.getInstance();

        final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
            private void cleanUp() {
                mMapLibreMap.getStyle(style -> {
                    style.removeLayer(layerId);
                    style.removeSource(sourceId);
                });
                choreographer.removeFrameCallback(this);
            }

            @Override
            public void doFrame(long frameTimeNanos) {
                mMapLibreMap.getStyle(style -> {
                    long elapsed = currentTimeMillis() - startTime;
                    float progress = Math.min((float) elapsed / durationMs, 1f);
                    float radius1 = radius * progress;

                    try {
                        imageSource.setCoordinates(getRadialLatLngQuad(centerPoint, radius1));
                        RasterLayer circleLayer = (RasterLayer) style.getLayer(layerId);
                        if (circleLayer != null) {
                            circleLayer.setProperties(PropertyFactory.rasterOpacity(1f - (progress)));
                        }
                    } catch (IllegalStateException e) {
                        cleanUp();
                        return;
                    }

                    // Continue animating or clean up
                    if (progress < 1f) {
                        choreographer.postFrameCallback(this);
                    } else {
                        cleanUp();
                    }

                });
            }
        };

        // Start the animation
        choreographer.postFrameCallback(frameCallback);

    }

    @Override
    public void onDestroyView() {
        mMapView.onDestroy();
        super.onDestroyView();
        mBearingProvider.stopBearingUpdates();
        mLocationProvider.stopLocationUpdates();
        mPrefs.unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
        mNetworkMonitor.unregisterNetworkMonitor(requireContext());
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

        if (mLastLocationAcquired.before(new Date(currentTimeMillis() - mUpdateIntervalMS))) {
            setLocationInaccurate(true);
        } else if (mLastLocationAcquired.before(new Date(currentTimeMillis() - mMinUpdateIntervalMS))) {
            // might be pointing the wrong way til next location update but that's ok
            displayMyCurrentLocationOverlay(mCurrentLocation);
        }

        mLocationProvider.checkPermissionsAndRequestUpdates(requireActivity(), this::requestLocationUpdates);

        mFieldFeatures.clear();
        mLinkFeatures.clear();
        // crashes because i don't currently clean out the rest of the portal details
//        mResonatorLineFeatures.clear();
        mTouchTargetFeatures.clear();
        drawEntities(mGame.getWorld().getGameEntitiesList());

        mXmParticleFeatures.clear();
        drawXMParticles(mGame.getWorld().getXMParticles().values());
        // try it anyway
        onReceiveDeletedEntityGuids(mApp.getDeletedEntityGuidsViewModel().getGuids().getValue());
        mUpdateHandler.post(mGetObjectsTask);
    }

    private void showHackResultDialog(Bundle hackResultBundle) {

        if (mGame.getLocation() != null) {
            final Handler uiHandler = new Handler();
            uiHandler.post(() -> {
                // guard against scanning too fast if request fails
                mLastScan = currentTimeMillis() + mMinUpdateIntervalMS;
                updateWorld();
            });
        }

        if (hackResultBundle == null) {
            // just to be extra safe?
            return;
        }

        String error = hackResultBundle.getString("error");
        @SuppressWarnings("unchecked")
        HashMap<String, Integer> items = (HashMap<String, Integer>) hackResultBundle.getSerializable("items");
        @SuppressWarnings("unchecked")
        HashMap<String, Integer> bonusItems = (HashMap<String, Integer>) hackResultBundle.getSerializable("bonusItems");

        Context ctx = getContext();
        if (error != null && ctx != null) {
            DialogHackResult newDialog = new DialogHackResult(getContext());
            newDialog.setMessage(error);
            newDialog.show();
            checkAndShowHackResults(newDialog);
        } else if (items != null && ctx != null) {
            DialogHackResult newDialog = new DialogHackResult(getContext());
            newDialog.setTitle("Acquired items");
            newDialog.setItems(items);
            newDialog.show();

            if (bonusItems == null) {
                checkAndShowHackResults(newDialog);
            } else {
                newDialog.setOnDismissListener(dialog -> {
                    DialogHackResult newDialog1 = new DialogHackResult(getContext());
                    newDialog1.setTitle("Bonus items");
                    newDialog1.setItems(bonusItems);
                    newDialog1.show();
                    checkAndShowHackResults(newDialog1);
                });
            }

        } else if (bonusItems != null && ctx != null) {
            DialogHackResult newDialog = new DialogHackResult(getContext());
            newDialog.setTitle("Bonus items");
            newDialog.setItems(bonusItems);
            newDialog.show();
            checkAndShowHackResults(newDialog);
        }
    }

    private void checkAndShowHackResults(DialogHackResult newDialog) {
        if (mGame.hasHackResults()) {
            newDialog.setOnDismissListener(d -> {
                Bundle hackResult = mGame.pollHackResult();
                if (hackResult != null) {
                    showHackResultDialog(hackResult);
                }
            });
        }
    }

    public void requestLocationUpdates() {
        if (mGame.isLocationAccurate()) {
            return;
        }
        if (!mLocationProvider.startLocationUpdates()) {
            setLocationInaccurate(true);
        }
        mBearingProvider.startBearingUpdates();
    }

    protected void setUpTileSource() {
        if (getMapTileProviderType(mCurrentTileSource) == RASTER) {
            String styleJSON = getMapTileProviderStyleJSON(mCurrentTileSource);
            assert styleJSON != null;
            mMapLibreMap.setStyle(new Style.Builder().fromJson(styleJSON), style -> {
                setUpStyleForMap(mMapLibreMap, style);
                setupPlayerCursor(mCurrentLocation, mBearing);
            });
        } else {
            String uri = getMapTileProviderStyleUri(mCurrentTileSource);
            assert uri != null;
            mMapLibreMap.setStyle(uri, style -> {
                setUpStyleForMap(mMapLibreMap, style);
                setupPlayerCursor(mCurrentLocation, mBearing);
            });
        }
    }


    private void setLocationInaccurate(boolean isInaccurate) {
//        Log.d("SCANNER", "IS MY LOCATION ACCURATE OR NOT? "+(isInaccurate ? "no" : "yes"));

        if (getActivity() == null || requireActivity().findViewById(R.id.quickMessage) == null) {
            return;
        }

        mGame.setLocationAccurate(!isInaccurate);
        updateShowScannerDisabledOverlay();

        if (isInaccurate) {
            setBigMessageText(getStringSafely(R.string.location_inaccurate));
            displayQuickMessage(getStringSafely(R.string.location_inaccurate));
//            mMapView.getOverlayManager().remove(mActionRadius);
        } else {
            if (Objects.equals(getQuickMessage(), getStringSafely(R.string.location_inaccurate))) {
                setBigMessageText(null);
                hideQuickMessage();
            }
        }
    }

    @SuppressLint("DefaultLocale")
    public void updateWorld() {
        if (!notBouncing("updateWorld", mMinUpdateIntervalMS)) {
            return;
        }

        if (mGame.isLocationAccurate() && mGame.scannerIsEnabled()) {
            addExpandingCircle(mCurrentLocation, 1000 * 1000 / 60, 1000, mIcons.get("sonarRing"));
        }
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

            mLastScan = currentTimeMillis() + mMinUpdateIntervalMS;

            return true;
        });

        if (mGame.isLocationAccurate() && mGame.scannerIsEnabled()) {
            slurp();
        }

        // get objects (on new thread)
        mApp.getExecutorService().submit(() -> mGame.intGetObjectsInCells(mGame.getLocation(), resultHandler));
        final Handler commsHandler = new Handler(Looper.getMainLooper());
        mApp.getExecutorService().submit(() -> mGame.intLoadCommunication(false, 50, false, commsHandler));
    }

    private void slurp() {
        // just to be safe
        if (!mGame.isLocationAccurate()) {
            return;
        }

        if (mGame.hasHackResults()) {
            Bundle hackResult = mGame.pollHackResult();
            showHackResultDialog(hackResult);
        }

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

        /*
         * Do we need to compute an "optimal slurp"?
         */
        Map<Long, XMParticle> xmParticles = mGame.getWorld().getXMParticles();
        mSlurpableParticles.clear();

        for (Map.Entry<Long, XMParticle> entry : xmParticles.entrySet()) {
            if (oldXM + newXM >= maxXM) {
                // continue is more computationally expensive and USUALLY not needed
                break;
            }

            XMParticle particle = entry.getValue();
            if (particle == null) {
                continue;
            }
            // FIXME this is honestly the worst imaginable solution, but for now it's what i have...
            if (particle.getCellLocation().distanceTo(playerLoc) < mActionRadiusM) {
                mSlurpableParticles.add(particle.getGuid());
                newXM += particle.getAmount();
                mXmParticleFeatures.remove(particle.getGuid());
                xmParticles.remove(entry.getKey());
            }
        }

        if (newXM > 0) {
            mGame.addSlurpableXMParticles(mSlurpableParticles);
            mGame.getAgent().addEnergy(newXM);
            updateXMParticles();
        }
    }

    public void removeInfoCard() {
        // Remove the info card symbol if it exists
        if (mMarkerInfoCard != null) {
            mSymbolManager.delete(mMarkerInfoCard);
            mMarkerInfoCard = null;
        }
        removeMarkerAroundTargetPortal();
    }

    private void removeMarkerAroundTargetPortal() {
        if (mMapLibreMap == null || mMapLibreMap.getStyle() == null) {
            return;
        }
        mMapLibreMap.getStyle(style -> {
            style.removeLayer("single-portal-circle-layer");
            style.removeSource("single-portal-circle-source");
        });
    }


    private void drawMarkerAroundTargetPortal(@NonNull GameEntityPortal portal) {
        if (mMapLibreMap == null || mMapLibreMap.getStyle() == null) {
            return;
        }

        mMapLibreMap.getStyle(style -> {
            String sourceId = "single-portal-circle-source";
            String layerId = "single-portal-circle-layer";

            // Remove any existing circle before adding a new one
            style.removeLayer(layerId);
            style.removeSource(sourceId);

            // Use your existing method to calculate the circle geometry
            LatLngQuad circleQuad = getRadialLatLngQuad(portal.getPortalLocation(), 30);

            // Create an ImageSource for the circle
            ImageSource circleSource = new ImageSource(sourceId, circleQuad, requireNonNull(mIcons.get("targetPortalMarker")));
            style.addSource(circleSource);

            // Add a RasterLayer for rendering
            RasterLayer circleLayer = new RasterLayer(layerId, sourceId).withProperties(
                    PropertyFactory.rasterOpacity(0.5f)
            );
            style.addLayer(circleLayer);
        });
    }


    @SuppressLint("InflateParams")
    public void showInfoCard(@NonNull GameEntityPortal portal) {
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
        textView2.setTextColor(0xFF000000 + requireNonNull(mGame.getKnobs().getTeamKnobs().getTeams().get(portal.getPortalTeam().toString())).getColour());
        textView3.setText(portal.getPortalTitle());
        int dist;
        if (mCurrentLocation == null) {
            dist = (int) mGame.getLocation().distanceTo(portal.getPortalLocation());
        } else {
            dist = (int) mCurrentLocation.distanceTo(portal.getPortalLocation());
        }
        textview4.setText(String.format(Locale.getDefault(), "Distance: %dm", dist));

        Bitmap bitmap = createDrawableFromView(requireContext(), markerView);
        requireNonNull(mMapLibreMap.getStyle()).addImage("info_card", bitmap);
        mMarkerInfoCard = mSymbolManager.create(new SymbolOptions()
                .withLatLng(portal.getPortalLocation().getLatLng())
                .withIconImage("info_card")
                .withIconSize(1.0f));

        drawMarkerAroundTargetPortal(portal);
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
        activity.runOnUiThread(() -> {
            TextView quickMessageView = activity.findViewById(R.id.quickMessage);
            quickMessageView.setText(message);
            quickMessageView.setVisibility(View.VISIBLE);
        });
    }

    public void hideQuickMessage() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> activity.findViewById(R.id.quickMessage).setVisibility(View.GONE));
    }

    public void setQuickMessageTimeout() {
        // not essential so not guaranteed to do anything useful
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        mApp.schedule_(this::hideQuickMessage, 3000, TimeUnit.MILLISECONDS);
    }

    public void fireBurster(int radius) {
        addExpandingCircle(mCurrentLocation, radius * 10, radius, mIcons.get("bursterRing"));
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
            location = actualReso.destinationPoint(actualReso.getResoLatLng(), 7, 0);
            // note that it is allowed to be more than 100%
            percentage = (int) ((float) damageAmount / (float) actualReso.getMaxEnergy() * 100);
        } else {
            location = actualPortal.getPortalLocation().destinationPoint(10, 10).getLatLng();
            percentage = 100;
        }

        SymbolOptions symbolOptions = new SymbolOptions()
                .withLatLng(location)
                .withTextField(String.format("%d%%", percentage) + (criticalHit ? "!" : ""))
                .withTextColor(getRgbaStringFromColour(0xCCF8C03E))
                .withTextHaloColor(getRgbaStringFromColour(0XFF000000))
                .withTextHaloWidth(1.0f)
//                    .withTextSize(14.0f)
                ;

        Symbol damage = mSymbolManager.create(symbolOptions);
        mApp.schedule_(() -> requireActivity().runOnUiThread(() -> mSymbolManager.delete(damage)), 2000, TimeUnit.MILLISECONDS);

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

        Line line = mPlayerDamageLineManager.create(lineOptions);
//        // let it delete itself
        mApp.schedule_(() -> requireActivity().runOnUiThread(
                () -> mPlayerDamageLineManager.delete(line)
        ), 2000, TimeUnit.MILLISECONDS);

        // now write up the text, but only if the damage was significant

        // max energy or just regular energy?
        // it is once again allowed to be more than 100%
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

        mApp.schedule_(() -> mSymbolManager.delete(mSymbolManager.create(symbolOptions)), 2000, TimeUnit.MILLISECONDS);
    }

    @NonNull
    private Bitmap createDrawableFromView(@NonNull Context context, @NonNull View view) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
        view.setLayoutParams(new WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        view.measure(displayMetrics.widthPixels, displayMetrics.heightPixels);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.buildDrawingCache();
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight() + 300, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        return bitmap;
    }

    @Override
    protected boolean onMapClick(LatLng point) {
        if (!mIsMapEnabled) {
            return true;
        }
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
                    if (!((ActivityMain) requireActivity()).isSelectingTargetPortal() || item.getGameEntityType() == GameEntityBase.GameEntityType.Portal) {
                        hitList.add(item);
                    }
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

    private void showGameEntityDialog(@NonNull List<GameEntityBase> gameEntities) {
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

    private void interactWithEntity(@NonNull GameEntityBase entity) {
        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal) {
            ActivityMain activity = (ActivityMain) requireActivity();
            if (activity.isSelectingTargetPortal()) {
                activity.setTargetPortal(entity.getEntityGuid());
                return;
            }
            FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, FragmentPortal.newInstance(entity.getEntityGuid()), "PORTAL");
            transaction.addToBackStack("PORTAL");
            transaction.commit();
            return;
        }
        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Item) {
            if (!mGame.scannerIsEnabled()) {
                postPlainCommsMessage("Pickup failed: scanner disabled");
                return;
            }
            mGame.intPickupItem(entity.getEntityGuid(), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    postPlainCommsMessage(error);
                } else {
                    postPlainCommsMessage("Picked up a " + msg.getData().getString("description"));
                }
                return true;
            }));
            return;
        }
        Toast.makeText(requireContext(), "Unhandled interaction with: " + getEntityDescription(entity), Toast.LENGTH_SHORT).show();
    }

    public void forceSync() {
        ArrayList<String> guids = new ArrayList<>();
        for (var entity : mGame.getWorld().getGameEntitiesList()) {
            guids.add(entity.getEntityGuid());
        }
        setUpTileSource();
        onReceiveDeletedEntityGuids(guids);
        mGame.clear();
        // FIXME this might not be strictly necessary
//        Activity activity = getActivity();
//        if (activity != null) {
//            Intent intent = new Intent(activity, activity.getClass());
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
//            activity.startActivity(intent);
//            activity.finish();
//        }
    }

    private void onLostConnection() {
        if (getActivity() == null || requireActivity().findViewById(R.id.quickMessage) == null) {
            return;
        }
        displayQuickMessage(getString(R.string.scanner_disabled_network_connection_lost));
        setBigMessageText(getStringSafely(R.string.scanner_disabled_network_connection_lost));
        updateShowScannerDisabledOverlay();
    }

    private void onRegainedConnection() {
        if (getActivity() == null || requireActivity().findViewById(R.id.quickMessage) == null) {
            return;
        }
        updateShowScannerDisabledOverlay();
    }

    void updateShowScannerDisabledOverlay() {
        if (getActivity() == null || requireActivity().findViewById(R.id.scannerDisabledOverlay) == null) {
            return;
        }

        boolean shouldShow = !mGame.isLocationAccurate();
        if (shouldShow) {
            displayQuickMessage(getStringSafely(R.string.location_inaccurate));
            setBigMessageText(getStringSafely(R.string.location_inaccurate));
//            mMapView.getOverlayManager().remove(mActionRadius);
        } else {
            if (Objects.equals(getQuickMessage(), getStringSafely(R.string.location_inaccurate))) {
                setBigMessageText(null);
                hideQuickMessage();
            }
        }
        // FIXME listen for this somehow ... later...
//        shouldShow = shouldShow && mGame.getAgent().getEnergyState() == PlayerEntity.EnergyState.OK;
        boolean shouldShow2 = !hasInternetConnectionCold(requireContext());
        if (shouldShow2) {
            displayQuickMessage(getStringSafely(R.string.scanner_disabled_network_connection_lost));
            setBigMessageText(getStringSafely(R.string.scanner_disabled_network_connection_lost));
//            mMapView.getOverlayManager().remove(mActionRadius);
        } else {
            if (Objects.equals(getQuickMessage(), getStringSafely(R.string.scanner_disabled_network_connection_lost))) {
                setBigMessageText(null);
                hideQuickMessage();
            }
        }

        boolean shouldShow3 = Objects.equals(mGame.getAgent().getEnergyState(), Depleted);
        if (shouldShow3) {
            setBigMessageText(getStringSafely(R.string.scanner_disabled_collect_more_xm));
            displayQuickMessage(getStringSafely(R.string.scanner_disabled_collect_more_xm));
        } else {
            if (Objects.equals(getQuickMessage(), getStringSafely(R.string.scanner_disabled_collect_more_xm))) {
                setBigMessageText(null);
                hideQuickMessage();
            }
        }

        shouldShow = shouldShow || shouldShow2 || shouldShow3;
        // disruptive in new way of thinking
//        if (shouldShow && !requireActivity().getSupportFragmentManager().isStateSaved()) {
//            requireActivity().getSupportFragmentManager().popBackStack(null, POP_BACK_STACK_INCLUSIVE);
//        }
        int visibility = shouldShow ? View.VISIBLE : View.GONE;
        mGame.setScannerEnabled(!shouldShow);
        requireActivity().runOnUiThread(() -> {
//            Log.d("SCANNER", "Am I going to show or hide the overlay? "+(finalShouldShow ? "show" : "hide"));
            if (getActivity() == null || requireActivity().findViewById(R.id.scannerDisabledOverlay) == null) {
                return;
            }
            requireActivity().findViewById(R.id.scannerDisabledOverlay).setVisibility(visibility);
            ((ActivityMain) requireActivity()).setFireButtonState();
        });
    }


    // TODO maybe make this into a more general scanner disabled overlay toggler
    public void setBigMessageText(String text) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (mBigMessageText == null) {
            // uh oh
            return;
        }
        activity.runOnUiThread(() -> {
            if (text == null || text.isEmpty()) {
                mBigMessageText.setText(null);
                mBigMessageText.setVisibility(GONE);
                return;
            }
            mBigMessageText.setText(text);
            mBigMessageText.setVisibility(VISIBLE);
        });
    }
}

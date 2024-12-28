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

package net.opengress.slimgress.api.Common;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.common.geometry.S2Cap;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;
import com.google.common.geometry.S2Region;
import com.google.common.geometry.S2RegionCoverer;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A collection of utility methods for various functionalities, including:
 * <ul>
 *   <li>Generating S2 geometry coverage (e.g., cell IDs from locations, areas, or rectangular regions).</li>
 *   <li>Handling "bounce" logic with cooldown periods to prevent repeated actions in rapid succession.</li>
 *   <li>Extracting error messages from API response bundles.</li>
 * </ul>
 */
public class Utils
{
    /**
     * Maintains a mapping of keys (identifiers for certain actions) to their most recent execution timestamp.
     * This is used to implement "bounce" logic, ensuring that the same action—identified by its key—is not
     * invoked more frequently than the allowed cooldown period.
     */
    private static final HashMap<String, Long> mBouncables = new HashMap<>();

    /**
     * Returns an array of cell IDs that cover the specified geographic location and surrounding area
     * using S2 geometry. The method calculates a spherical cap based on the provided location and area,
     * then derives all cell IDs within the specified range of S2 levels.
     *
     * <p>The function starts by converting the latitude and longitude from the given {@link Location},
     * expressed in E6 format, into an {@link S2LatLng}. It then creates an {@link S2Cap} representing
     * the area around that point by using the provided {@code areaM2}. The cap is subsequently passed
     * to {@link #getCellIdsFromRegion(S2Region, int, int)} for retrieving the cell IDs that define
     * the coverage area.</p>
     *
     * @param location the location containing latitude and longitude in E6 format
     * @param areaM2   the approximate area, in square meters, defining the region to cover
     * @param minLevel the minimum S2 cell level to use when covering the area
     * @param maxLevel the maximum S2 cell level to use when covering the area
     * @return an array of {@code String} cell IDs covering the specified region at the given S2 level range
     */
    @NonNull
    public static String[] getCellIdsFromLocationArea(@NonNull Location location, double areaM2, int minLevel, int maxLevel)
    {
        final double radius_m2 = 6371 * 1000;
        final double sr = areaM2 / (radius_m2 * radius_m2);

        S2LatLng pointLatLng = S2LatLng.fromE6(location.getLatitudeE6(), location.getLongitudeE6());
        S2Cap cap = S2Cap.fromAxisArea(pointLatLng.toPoint(), sr);

        return getCellIdsFromRegion(cap, minLevel, maxLevel);
    }

    /**
     * Returns an array of cell IDs that cover the specified geographic location
     * within a given radius in kilometers, using S2 geometry. This method
     * calculates the corresponding area in square meters and delegates to
     * {@link #getCellIdsFromLocationArea(Location, double, int, int)}.
     *
     * @param location the location containing latitude and longitude in E6 format
     * @param radiusKm the radius around the location in kilometers
     * @param minLevel the minimum S2 cell level to use
     * @param maxLevel the maximum S2 cell level to use
     * @return an array of {@code String} cell IDs covering the specified region
     * at the given S2 level range
     */
    @NonNull
    public static String[] getCellIdsFromLocationRadiusKm(Location location, double radiusKm, int minLevel, int maxLevel) {
        double radiusMeters = radiusKm * 1000;
        double areaM2 = Math.PI * radiusMeters * radiusMeters;
        return getCellIdsFromLocationArea(location, areaM2, minLevel, maxLevel);
    }

    /**
     * Returns an array of hex-encoded S2 cell IDs covering the rectangular region defined
     * by the minimum and maximum {@link Location} coordinates. The method constructs
     * an {@link S2LatLngRect} from the two points and delegates to
     * {@link #getCellIdsFromRegion(S2Region, int, int)} to retrieve the cell IDs.
     *
     * @param min      the {@link Location} representing the lower (south/west) bound of the rectangle
     * @param max      the {@link Location} representing the upper (north/east) bound of the rectangle
     * @param minLevel the minimum S2 cell level used in the coverage
     * @param maxLevel the maximum S2 cell level used in the coverage
     * @return a non-null array of hex-encoded S2 cell IDs representing the coverage
     * of the rectangular region
     */
    @NonNull
    public static String[] getCellIdsFromMinMax(@NonNull Location min, @NonNull Location max, int minLevel, int maxLevel)
    {
        S2LatLngRect region = S2LatLngRect.fromPointPair(S2LatLng.fromE6(min.getLatitudeE6(), min.getLongitudeE6()),
                S2LatLng.fromE6(max.getLatitudeE6(), max.getLongitudeE6()));
        return getCellIdsFromRegion(region, minLevel, maxLevel);
    }

    /**
     * Returns an array of hex-encoded S2 cell IDs covering the specified {@link S2Region}
     * within the given S2 cell level limits. It uses {@link S2RegionCoverer} to generate
     * a coverage set of {@link S2CellId}s, filters them to ensure the cell levels fall
     * within the {@code minLevel} and {@code maxLevel} range, and converts them to their
     * hex-string representations.
     *
     * @param region    the S2 region (e.g., rectangle, cap, polygon) to cover
     * @param minLevel  the minimum S2 cell level used in the coverage
     * @param maxLevel  the maximum S2 cell level used in the coverage
     * @return a non-null array of hex-encoded S2 cell IDs covering the specified region
     */
    @NonNull
    public static String[] getCellIdsFromRegion(S2Region region, int minLevel, int maxLevel)
    {
        S2RegionCoverer rCov = new S2RegionCoverer();

        rCov.setMinLevel(minLevel);
        rCov.setMaxLevel(maxLevel);

        // get cells
        ArrayList<S2CellId> cells = new ArrayList<>();
        rCov.getCovering(region, cells);

        ArrayList<Long> cellIds = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {

            S2CellId cellId = cells.get(i);

            // can happen for some reason
            if (cellId.level() < minLevel || cellId.level() > maxLevel)
                continue;

            cellIds.add(cellId.id());
        }

        // convert to hex values
        String[] cellIdsHex = new String[cellIds.size()];
        for (int i = 0; i < cellIdsHex.length; i++) {
            cellIdsHex[i] = Long.toHexString(cellIds.get(i));
        }

        return cellIdsHex;
    }

    /**
     * Checks whether the action associated with the given key is allowed to proceed based on a cooldown period.
     * <p>
     * This method uses an internal map to track the last time a given action (identified by {@code key}) was
     * performed. If the action has not occurred within the specified {@code cooldownMillis} or if it has never
     * occurred, this method updates the last occurrence time and returns {@code true}, indicating the action can
     * proceed. Otherwise, it returns {@code false}, indicating the action is still on cooldown.
     * </p>
     *
     * @param key           a unique identifier for the specific action (e.g., a string or event name)
     * @param cooldownMillis the minimum time interval (in milliseconds) that must pass between actions
     * @return {@code true} if enough time has elapsed since the last action (or if no record exists),
     *         {@code false} otherwise
     */
    public static boolean notBouncing(String key, long cooldownMillis) {
        long currentTime = System.currentTimeMillis();
        Long lastPing = mBouncables.get(key);

        if (lastPing == null || currentTime - lastPing >= cooldownMillis) {
            // Either no previous ping or cooldown time has passed
            mBouncables.put(key, currentTime); // Update the timestamp
            return true; // Allow the action
        } else {
            return false; // Action is still on cooldown
        }
    }

    /**
     * Extracts an error message from the provided {@link Bundle}, looking first for the key "Exception"
     * and then for "Error" if "Exception" is not found. If neither key is present, returns {@code null}.
     *
     * @param data the bundle containing potential error information
     * @return the error string if found in the bundle, or {@code null} otherwise
     */
    public static String getErrorStringFromAPI(@NonNull Bundle data) {
        String error = data.getString("Exception");
        if (error == null) {
            error = data.getString("Error");
        }
        return error;
    }

}

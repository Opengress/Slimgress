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

import com.google.common.geometry.S2Cap;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;
import com.google.common.geometry.S2Region;
import com.google.common.geometry.S2RegionCoverer;

import java.util.ArrayList;
import java.util.HashMap;


public class Utils
{
    private static final HashMap<String, Long> mBouncables = new HashMap<>();

    public static String[] getCellIdsFromLocationArea(Location location, double areaM2, int minLevel, int maxLevel)
    {
        final double radius_m2 = 6371 * 1000;
        final double sr = areaM2 / (radius_m2 * radius_m2);

        S2LatLng pointLatLng = S2LatLng.fromE6(location.getLatitudeE6(), location.getLongitudeE6());
        S2Cap cap = S2Cap.fromAxisArea(pointLatLng.toPoint(), sr);

        return getCellIdsFromRegion(cap, minLevel, maxLevel);
    }

    public static String[] getCellIdsFromMinMax(Location min, Location max, int minLevel, int maxLevel)
    {
        S2LatLngRect region = S2LatLngRect.fromPointPair(S2LatLng.fromE6(min.getLatitudeE6(), min.getLongitudeE6()),
                S2LatLng.fromE6(max.getLatitudeE6(), max.getLongitudeE6()));
        return getCellIdsFromRegion(region, minLevel, maxLevel);
    }

    // retrieve cell ids from location and covering area in m2
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

    public static String getErrorStringFromAPI(Bundle data) {
        String error = data.getString("Exception");
        if (error == null) {
            error = data.getString("Error");
        }
        return error;
    }

}

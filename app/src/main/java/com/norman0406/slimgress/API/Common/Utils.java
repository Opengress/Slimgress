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

package com.norman0406.slimgress.API.Common;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

import com.google.common.geometry.S2Cap;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;
import com.google.common.geometry.S2Region;
import com.google.common.geometry.S2RegionCoverer;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Utils
{
    private static OkHttpClient mCachedClient;

    public static String[] getCellIdsFromLocationArea(Location location, double areaM2, int minLevel, int maxLevel)
    {
        final double radius_m2 = 6371 * 1000;
        final double sr = areaM2 / (radius_m2 * radius_m2);

        S2LatLng pointLatLng = S2LatLng.fromE6(location.getLatitude(), location.getLongitude());
        S2Cap cap = S2Cap.fromAxisArea(pointLatLng.toPoint(), sr);

        return getCellIdsFromRegion(cap, minLevel, maxLevel);
    }

    public static String[] getCellIdsFromMinMax(Location min, Location max, int minLevel, int maxLevel)
    {
        S2LatLngRect region = S2LatLngRect.fromPointPair(S2LatLng.fromE6(min.getLatitude(), min.getLongitude()),
                S2LatLng.fromE6(max.getLatitude(), max.getLongitude()));
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

    public static OkHttpClient getCachedClient(File cacheDir) {
        if (mCachedClient == null) {
            int cacheSize = 75 * 1024 * 1024; // 75 MiB
            Cache cache = new Cache(new File(cacheDir, "http-cache"), cacheSize);

            mCachedClient = new OkHttpClient.Builder()
                    .cache(cache)
                    .build();
        }
        return mCachedClient;
    }

    public static Bitmap getImageBitmap(String url, File cacheDir) {
        Bitmap bm = null;
        try {
            Request get = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Opengress/Slimgress (API dev)")
                    .build();
            Response response = getCachedClient(cacheDir).newCall(get).execute();
            InputStream content = Objects.requireNonNull(response.body()).byteStream();
            bm = BitmapFactory.decodeStream(content);
            content.close();
        } catch (IOException e) {
            Log.e("Utils.getImageBitmap", "Error getting bitmap", e);
        }
        return bm;
    }

}

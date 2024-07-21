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

package net.opengress.slimgress.API.Common;

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

import net.opengress.slimgress.API.Interface.Interface;
import net.opengress.slimgress.R;

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
            int cacheSize = 125 * 1024 * 1024; // 125 MiB
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
                    .header("User-Agent", Interface.mUserAgent)
                    .build();

            try (Response response = getCachedClient(cacheDir).newCall(get).execute()) {
                if (!response.isSuccessful()) {
                    Log.e("Utils.getImageBitmap", "HTTP error code: " + response.code());
                    return null;
                }

                try (InputStream content = response.body().byteStream()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 3; // Adjust as needed
                    bm = BitmapFactory.decodeStream(content, null, options);
}
            }
        } catch (IOException e) {
            Log.e("Utils.getImageBitmap", "Error getting bitmap", e);
        }
        return bm;
    }

    public static int getLevelColor(int level) {
        int levelColour = R.color.level_one;
        switch (level) {
            case 2:
                levelColour = R.color.level_two;
                break;
            case 3:
                levelColour = R.color.level_three;
                break;
            case 4:
                levelColour = R.color.level_four;
                break;
            case 5:
                levelColour = R.color.level_five;
                break;
            case 6:
                levelColour = R.color.level_six;
                break;
            case 7:
                levelColour = R.color.level_seven;
                break;
            case 8:
                levelColour = R.color.level_eight;
                break;
        }
        return levelColour;
    }

}

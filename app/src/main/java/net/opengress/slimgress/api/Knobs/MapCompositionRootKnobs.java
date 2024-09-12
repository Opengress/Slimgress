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

package net.opengress.slimgress.api.Knobs;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class MapCompositionRootKnobs extends Knobs {

    public static class MapProvider {

        private final String mName;
        private final String[] mBaseUrls;
        private final String mFilenameEnding;
        private final int mMinZoom;
        private final int mMaxZoom;
        private final int mTileSize;
        private final CoordinateSystem mCoordinateSystem;

        public enum CoordinateSystem {
            XYZ,
            TMS
        }

        public MapProvider(JSONObject json) throws JSONException {
            mName = json.getString("name");
            var baseUrls = json.getJSONArray("baseUrls");
            ArrayList<String> urls = new ArrayList<>();
            for (int x = 0; x < baseUrls.length(); x++) {
                urls.add(baseUrls.getString(x));
            }
            mBaseUrls = urls.toArray(new String[0]);
            mFilenameEnding = json.getString("filenameEnding");
            mMinZoom = json.getInt("minZoom");
            mMaxZoom = json.getInt("maxZoom");
            mTileSize = json.getInt("tileSize");
            if (json.getString("coordinateSystem").equals("XYZ")) {
                mCoordinateSystem = CoordinateSystem.XYZ;
            } else {
                mCoordinateSystem = CoordinateSystem.TMS;
            }
        }

        public String getName() {
            return mName;
        }

        public int getMinZoom() {
            return mMinZoom;
        }

        public int getMaxZoom() {
            return mMaxZoom;
        }

        public String[] getBaseUrls() {
            return mBaseUrls;
        }

        public int getTileSize() {
            return mTileSize;
        }

        public String getFilenameEnding() {
            return mFilenameEnding;
        }

        public CoordinateSystem getCoordinateSystem() {
            return mCoordinateSystem;
        }

    }

    private final HashMap<String, MapProvider> mMapProviders;

    public MapCompositionRootKnobs(JSONObject json) throws JSONException {
        super(json);

        mMapProviders = new HashMap<>();

        var mapProviders = json.getJSONArray("mapProviders");

        for (int x = 0; x < mapProviders.length(); x++) {
            MapProvider m = new MapProvider(mapProviders.getJSONObject(x));
            mMapProviders.put(m.getName(), m);
        }
    }

    public MapProvider fromString(String name) {
        if (Objects.equals(name, "default")) {
            return mMapProviders.entrySet().iterator().next().getValue();
        }
        return mMapProviders.get(name);
    }

    public HashMap<String, MapProvider> getMapProviders() {
        return mMapProviders;
    }
}

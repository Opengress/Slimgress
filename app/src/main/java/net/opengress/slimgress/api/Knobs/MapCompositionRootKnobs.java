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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class MapCompositionRootKnobs extends Knobs {

    public static class MapProvider {

        private final String mName;
        private final MapType mType;
        private final String mAttribution;
        private final String[] mBaseUrls;
        private final String mTileUrl;
        private final String mFilenameEnding;
        private final int mMinZoom;
        private final int mMaxZoom;
        private final int mTileSize;
        private final CoordinateSystem mCoordinateSystem;

        public enum CoordinateSystem {
            XYZ,
            TMS
        }

        public enum MapType {
            RASTER,
            VECTOR
        }

        public MapProvider(JSONObject json) throws JSONException {
            mName = json.getString("name");
            if (json.getString("type").equals("raster")) {
                mType = MapType.RASTER;
            } else {
                mType = MapType.VECTOR;
            }
            mAttribution = json.getString("attribution");
            var baseUrls = json.getJSONArray("baseUrls");
            ArrayList<String> urls = new ArrayList<>();
            for (int x = 0; x < baseUrls.length(); x++) {
                urls.add(baseUrls.getString(x));
            }
            mBaseUrls = urls.toArray(new String[0]);
            mTileUrl = json.getString("tileUrl");
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

        /**
         * @return Pretty name of the map provider, such as "Carto DarkMatter (no labels)"
         */
        public String getName() {
            return mName;
        }

        /**
         * @return Type of the map, currently being either raster or vector
         */
        public MapType getType() {
            return mType;
        }

        /**
         * @return An attribution string as usually required when using map tiles
         */
        public String getAttribution() {
            return mAttribution;
        }

        /**
         * @return The minimum zoom at which this tileset will display
         */
        public int getMinZoom() {
            return mMinZoom;
        }

        /**
         * @return The maximum zoom at which this tileset will display
         */
        public int getMaxZoom() {
            return mMaxZoom;
        }

        /**
         * @return A list of base URLs suitable for loading into OSMDroid
         */
        public String[] getBaseUrls() {
            return mBaseUrls;
        }

        /**
         * @return A tileUrl suitable for use in MapLibre or similar
         */
        public String getTileUrl() {
            return mTileUrl;
        }

        /**
         * @return Tile size in pixels
         */
        public int getTileSize() {
            return mTileSize;
        }

        /**
         * @return a file extension such as ".png" or ".webp" to append to URLs
         */
        public String getFilenameEnding() {
            return mFilenameEnding;
        }

        /**
         * @return A TMS or XYZ coordinate system. Usually XYZ is correct
         */
        public CoordinateSystem getCoordinateSystem() {
            return mCoordinateSystem;
        }

        /**
         * @return The JSON style for displaying this map alone.
         */
        public String getStyleJSON() throws JSONException {
            var provider = this;
            JSONObject source = new JSONObject();
            source.put("type", provider.getType() == MapType.RASTER ? "raster" : "vector");

            // Define tiles array based on base URLs and filename ending
            JSONArray tilesArray = new JSONArray();
            for (String baseUrl : provider.getBaseUrls()) {
                tilesArray.put(baseUrl + "{z}/{x}/{y}" + provider.getFilenameEnding());
            }

            source.put("tiles", tilesArray);
            source.put("minzoom", provider.getMinZoom());
            source.put("maxzoom", provider.getMaxZoom());
            source.put("tileSize", provider.getTileSize());
            source.put("attribution", provider.getAttribution());

            // Conditionally set the scheme to either "xyz" or "tms"
            if (provider.getCoordinateSystem() == CoordinateSystem.TMS) {
                source.put("scheme", "tms");
            } else {
                source.put("scheme", "xyz");  // Explicitly set for clarity
            }

            // Create the sources object
            JSONObject sources = new JSONObject();
            sources.put(provider.getName().toLowerCase().replace(" ", "-"), source);

            // Create the layer JSON
            JSONObject layer = new JSONObject();
            layer.put("id", provider.getName().toLowerCase().replace(" ", "-") + "-layer");
            layer.put("type", "raster");
            layer.put("source", provider.getName().toLowerCase().replace(" ", "-"));
            layer.put("minzoom", provider.getMinZoom());
            layer.put("maxzoom", provider.getMaxZoom());

            // Create the layers array
            JSONArray layersArray = new JSONArray();
            layersArray.put(layer);

            // Create the final style JSON
            JSONObject styleJson = new JSONObject();
            styleJson.put("version", 8);
            styleJson.put("name", provider.getName());
            styleJson.put("sources", sources);
            // FIXME awful and wrong thing to hardcode?
            styleJson.put("glyphs", "https://opengress.net/pbf/{range}.pbf");
            styleJson.put("layers", layersArray);

            return styleJson.toString();
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

    public MapProvider getMapProvider(String name) {
        if (Objects.equals(name, "default")) {
            return mMapProviders.entrySet().iterator().next().getValue();
        }
        return mMapProviders.get(name);
    }

    public HashMap<String, MapProvider> getMapProviders() {
        return mMapProviders;
    }
}

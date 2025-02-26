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

import androidx.annotation.NonNull;

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;

import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.geojson.Point;

import java.io.Serializable;

public class Location implements Serializable
{
    private final long latE6;
    private final long lngE6;

    public Location(@NonNull android.location.Location pos) {
        latE6 = Math.round(pos.getLatitude() * 1e6);
        lngE6 = Math.round(pos.getLongitude() * 1e6);
    }

    public Location(@NonNull JSONObject json) throws JSONException
    {
        latE6 = json.getInt("latE6");
        lngE6 = json.getInt("lngE6");
    }

    public Location(long cellId)
    {
        S2CellId cell = new S2CellId(cellId);

        S2LatLng pos = cell.toLatLng();
        latE6 = pos.lat().e6();
        lngE6 = pos.lng().e6();
    }

    public Location(double latDeg, double lngDeg)
    {
        latE6 = Math.round(latDeg * 1e6);
        lngE6 = Math.round(lngDeg * 1e6);
    }

    public Location(long latE6, long lngE6)
    {
        this.latE6 = latE6;
        this.lngE6 = lngE6;
    }

    public Location(@NonNull String hexLatLng)
    {
        String[] parts = hexLatLng.split(",");
        // a previous implementation could handle 64 bit values, which were overkill
        latE6 = (int) Long.parseLong(parts[0], 16);
        lngE6 = (int) Long.parseLong(parts[1], 16);
    }

    public Location(LatLng target) {
        latE6 = (long) (target.getLatitude() * 1e6);
        lngE6 = (long) (target.getLongitude() * 1e6);
    }

    public long getLatitudeE6()
    {
        return latE6;
    }

    public long getLongitudeE6()
    {
        return lngE6;
    }

    public double getLatitude() {
//        return latE6 * 1e-6;
        return latE6 / 1e6;
    }

    public double getLongitude() {
//        return lngE6 * 1e-6;
        return lngE6 / 1e6;
    }

    public S2LatLng getS2LatLng() {
        return S2LatLng.fromE6(latE6, lngE6);
    }

    public LatLng getLatLng()
    {
        return new LatLng(getLatitude(), getLongitude());
    }

    public Point getPoint() {
        return Point.fromLngLat(getLongitude(), getLatitude());
    }

    @NonNull
    public String toString() {
        return getLatitude() + "," + getLongitude();
    }

    public Location destinationPoint(int distMetres, int bearingDegrees) {
        double lat1 = Math.toRadians(getLatitude());
        double lon1 = Math.toRadians(getLongitude());
        double angularDistance = distMetres / S2LatLng.EARTH_RADIUS_METERS;
        double bearingRad = Math.toRadians(bearingDegrees);

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDistance) +
                Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearingRad));
        double lon2 = lon1 + Math.atan2(Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1),
                Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2));

        return new Location(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }

    // Method to calculate distance between this point and another point
    public double distanceTo(@NonNull Location other) {

        // Convert latitude and longitude from degrees to radians
        double lat1 = Math.toRadians(getLatitude());
        double lon1 = Math.toRadians(getLongitude());
        double lat2 = Math.toRadians(other.getLatitude());
        double lon2 = Math.toRadians(other.getLongitude());

        // Haversine formula
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Distance in meters
        return S2LatLng.EARTH_RADIUS_METERS * c;
    }

    // Method to calculate distance between this point and another point
    public double distanceTo(@NonNull android.location.Location other) {

        // Convert latitude and longitude from degrees to radians
        double lat1 = Math.toRadians(getLatitude());
        double lon1 = Math.toRadians(getLongitude());
        double lat2 = Math.toRadians(other.getLatitude());
        double lon2 = Math.toRadians(other.getLongitude());

        // Haversine formula
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Distance in meters
        return S2LatLng.EARTH_RADIUS_METERS * c;
    }

    public boolean approximatelyEqualTo(Location other) {
        return other != null && distanceTo(other) < 0.1;
    }
}

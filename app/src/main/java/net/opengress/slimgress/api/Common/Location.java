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

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.math.BigInteger;

public class Location
{
    private final long latitude;
    private final long longitude;

    private double hexToDecimal(String hex) {
        BigInteger bigInt = new BigInteger(hex, 16);
        // Handle negative values for latitude
        if (bigInt.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            bigInt = bigInt.subtract(BigInteger.ONE.shiftLeft(64)); // Adjust for signed 64-bit integer
        }
        return bigInt.doubleValue();
    }

    public Location(JSONObject json) throws JSONException
    {
        latitude = json.getInt("latE6");
        longitude = json.getInt("lngE6");
    }

    public Location(long cellId)
    {
        S2CellId cell = new S2CellId(cellId);

        S2LatLng pos = cell.toLatLng();
        latitude = pos.lat().e6();
        longitude = pos.lng().e6();
    }

    public Location(double latDeg, double lngDeg)
    {
        S2LatLng pos = S2LatLng.fromDegrees(latDeg, lngDeg);

        latitude = pos.lat().e6();
        longitude = pos.lng().e6();
    }

    public Location(long latE6, long lngE6)
    {
        latitude = latE6;
        longitude = lngE6;
    }

    public Location(String hexLatLng)
    {
        String[] parts = hexLatLng.split(",");
        latitude = (long) hexToDecimal(parts[0]);
        longitude = (long) hexToDecimal(parts[1]);
    }

    public long getLatitude()
    {
        return latitude;
    }

    public long getLongitude()
    {
        return longitude;
    }

    public S2LatLng getS2LatLng() {
        return S2LatLng.fromE6(latitude, longitude);
    }

    public GeoPoint getLatLng()
    {
        S2LatLng pos = S2LatLng.fromE6(latitude, longitude);
        return new GeoPoint(pos.latDegrees(), pos.lngDegrees());
    }
}

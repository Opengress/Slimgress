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

package net.opengress.slimgress.API.Knobs;

import org.json.JSONException;
import org.json.JSONObject;

public class ScannerKnobs extends Knobs
{
    private final int mRangeM;
    private final int mUpdateDistanceM;
    private final int mUpdateIntervalMS;
    private final int mMinUpdateIntervalMS;
    private final int mActionRadiusM;

    public ScannerKnobs(JSONObject json) throws JSONException
    {
        super(json);

        mRangeM = json.getInt("rangeM");
        mUpdateDistanceM = json.getInt("updateDistanceM");
        mUpdateIntervalMS = json.getInt("updateIntervalMs");
        mMinUpdateIntervalMS = json.getInt("minUpdateIntervalMs");
        mActionRadiusM = json.getInt("actionRadiusM");
    }

    public int getRangeM()
    {
        return mRangeM;
    }

    public int getUpdateDistanceM()
    {
        return mUpdateDistanceM;
    }

    public int getUpdateIntervalMS()
    {
        return mUpdateIntervalMS;
    }

    public int getMinUpdateIntervalMS()
    {
        return mMinUpdateIntervalMS;
    }

    public int getActionRadiusM()
    {
        return mActionRadiusM;
    }
}

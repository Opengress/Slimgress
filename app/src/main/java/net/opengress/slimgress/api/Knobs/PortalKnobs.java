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

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PortalKnobs extends Knobs
{
    public class Band
    {
        List<Integer> applicableLevels;

        public int getRemaining() {
            return remaining;
        }

        int remaining;
    }

    private final List<Band> mBands;
    private final JSONArray mResonatorEnergyLevels;
    private final boolean mCanPlayerRemoveMod;
    private final int mMaxModsPerPlayer;

    public PortalKnobs(JSONObject json) throws JSONException
    {
        super(json);

        JSONObject resonatorLimits = json.getJSONObject("resonatorLimits");
        JSONArray bands = resonatorLimits.getJSONArray("bands");
        mBands = new ArrayList<>();
        for (int i = 0; i < bands.length(); i++) {
            JSONObject band = bands.getJSONObject(i);
            Band newBand = new Band();
            newBand.applicableLevels = getIntArray(band, "applicableLevels");
            newBand.remaining = band.getInt("remaining");
            mBands.add(newBand);
        }

        mResonatorEnergyLevels = json.getJSONArray("resonatorEnergyLevels");

        mCanPlayerRemoveMod = json.getBoolean("canPlayerRemoveMod");
        mMaxModsPerPlayer = json.getInt("maxModsPerPlayer");
    }

    public Band getBandForLevel(int level)
    {
        for (Band band : mBands) {
            if (band.applicableLevels.contains(level))
                return band;
        }

        Log.w("PortalKnobs", "band not found for level: " + level);
        return null;
    }

    public List<Band> getBands()
    {
        return mBands;
    }

    public int getResonatorEnergyForLevel(int level) {
        return mResonatorEnergyLevels.optInt(level, 0);
    }

    public JSONArray getResonatorEnergyLevels() {
        return mResonatorEnergyLevels;
    }

    public boolean getCanPlayerRemoveMod()
    {
        return mCanPlayerRemoveMod;
    }

    public int getMaxModsPerPlayer()
    {
        return mMaxModsPerPlayer;
    }
}

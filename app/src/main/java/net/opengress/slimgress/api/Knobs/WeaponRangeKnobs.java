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

import android.util.SparseIntArray;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class WeaponRangeKnobs extends Knobs
{
    private final SparseIntArray mUltraStrikeDamageRangeMap;
    private final SparseIntArray mXmpDamageRangeMap;

    public WeaponRangeKnobs(JSONObject json) throws JSONException
    {
        super(json);

        JSONObject ultraStrikeDamageRangeMap = json.getJSONObject("ultraStrikeDamageRangeMap");
        mUltraStrikeDamageRangeMap = new SparseIntArray();
        Iterator<?> it1 = ultraStrikeDamageRangeMap.keys();
        while (it1.hasNext()) {
            String strKey = (String)it1.next();
            int key = Integer.parseInt(strKey);
            mUltraStrikeDamageRangeMap.put(key, ultraStrikeDamageRangeMap.getInt(strKey));
        }

        JSONObject xmpDamageRangeMap = json.getJSONObject("xmpDamageRangeMap");
        mXmpDamageRangeMap = new SparseIntArray();
        Iterator<?> it2 = xmpDamageRangeMap.keys();
        while (it2.hasNext()) {
            String strKey = (String)it2.next();
            int key = Integer.parseInt(strKey);
            mXmpDamageRangeMap.put(key, xmpDamageRangeMap.getInt(strKey));
        }
    }

    public SparseIntArray getUltraStrikeDamageRangeMap()
    {
        return mUltraStrikeDamageRangeMap;
    }

    public SparseIntArray getXmpDamageRangeMap()
    {
        return mXmpDamageRangeMap;
    }

    public int getUltraStrikeDamageRange(int level)
    {
        return mUltraStrikeDamageRangeMap.get(level);
    }

    public int getXmpDamageRange(int level)
    {
        return mXmpDamageRangeMap.get(level);
    }
}

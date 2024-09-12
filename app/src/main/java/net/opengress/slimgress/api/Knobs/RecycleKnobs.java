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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class RecycleKnobs extends Knobs
{
    private final Map<String, List<Integer>> mRecycleValuesMap;

    public RecycleKnobs(JSONObject json) throws JSONException
    {
        super(json);

        mRecycleValuesMap = new HashMap<>();
        JSONObject recycleValuesMap = json.getJSONObject("recycleValuesMap");
        Iterator<?> it = recycleValuesMap.keys();
        while (it.hasNext()) {
            String key = (String)it.next();
            mRecycleValuesMap.put(key, getIntArray(recycleValuesMap, key));
        }
    }

    public List<Integer> getRecycleValues(String key)
    {
        if (!mRecycleValuesMap.containsKey(key))
            Log.e("RecycleKnobs", "key not found in hash map: " + key);

        return mRecycleValuesMap.get(key);
    }
}
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
import java.util.List;

public abstract class Knobs
{
    public Knobs(JSONObject json) {
    }

    protected static List<Integer> getIntArray(JSONObject json, String key) throws JSONException
    {
        JSONArray jsonArray = json.getJSONArray(key);
        List<Integer> ret = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object obj = jsonArray.get(i);
            if (obj instanceof Integer)
                ret.add((Integer)obj);
        }
        return ret;
    }
}

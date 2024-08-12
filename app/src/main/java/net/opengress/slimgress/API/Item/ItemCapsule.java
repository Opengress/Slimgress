/*

 Slimgress: Opengress API for Android
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

package net.opengress.slimgress.API.Item;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ItemCapsule extends ItemBase {
    private String mDifferentiator;
    private int mCurrentCapacity;
    private int mCurrentCount;

    public ItemCapsule(JSONArray json) throws JSONException {
        super(ItemType.Capsule, json);

        JSONObject item = json.getJSONObject(2);
        JSONObject container = item.getJSONObject("container");
        JSONObject moniker = item.getJSONObject("moniker");
        // have to do something with this later
        JSONArray stackableItems = container.getJSONArray("stackableItems");

        mDifferentiator = moniker.getString("differentiator");
        mCurrentCapacity = container.getInt("currentCapacity");
        mCurrentCount = container.getInt("currentCount");
    }

    public static String getNameStatic() {
        return "CAPSULE";
    }

    public String getName() {
        return getNameStatic();
    }

    public String getDifferentiator() {
        return mDifferentiator;
    }

    public int getCurrentCapacity() {
        return mCurrentCapacity;
    }

    public int getCurrentCount() {
        return mCurrentCount;
    }

    // getAllItems?
    // remove(String guid)?
    // add (String guid)?

}

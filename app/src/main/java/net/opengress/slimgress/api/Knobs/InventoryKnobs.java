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

public class InventoryKnobs extends Knobs
{
    private final boolean mUseMax;
    private final boolean mUseLimit;
    private final int mMaxInventoryItems;

    public InventoryKnobs(JSONObject json) throws JSONException
    {
        super(json);

        mUseMax = json.getBoolean("useMax");
        mUseLimit = json.getBoolean("useLimit");
        mMaxInventoryItems = json.getInt("maxInventoryItems");
    }

    public boolean isUseMax()
    {
        return mUseMax;
    }

    public boolean isUseLimit()
    {
        return mUseLimit;
    }

    public int getMaxInventoryItems()
    {
        return mMaxInventoryItems;
    }
}

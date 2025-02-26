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

package net.opengress.slimgress.api.Item;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ItemFlipCard extends ItemBase
{
    public enum FlipCardType
    {
        Jarvis,
        Ada
    }

    private FlipCardType mVirusType;

    public ItemFlipCard(JSONArray json) throws JSONException
    {
        super(ItemType.FlipCard, json);

        JSONObject item = json.getJSONObject(2);
        JSONObject flipCard = item.getJSONObject("flipCard");

        if (flipCard.getString("flipCardType").equals("JARVIS"))
            mVirusType = FlipCardType.Jarvis;
        else if (flipCard.getString("flipCardType").equals("ADA"))
            mVirusType = FlipCardType.Ada;
        else
            System.out.println("unknown virus type");
    }

    @NonNull
    @Contract(pure = true)
    public static String getNameStatic()
    {
        return "FLIP_CARD";
    }

    public String getName()
    {
        return getNameStatic();
    }

    public FlipCardType getFlipCardType()
    {
        return mVirusType;
    }

    @Override
    public String getUsefulName() {
        return getDisplayName();
    }
}

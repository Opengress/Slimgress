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

package net.opengress.slimgress.api.Item;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ItemPlayerPowerup extends ItemBase {
    public enum PlayerPowerupType {
        DAP
    }

    private PlayerPowerupType mPlayerPowerupType;

    public ItemPlayerPowerup(JSONArray json) throws JSONException {
        super(ItemType.PlayerPowerup, json);

        JSONObject item = json.getJSONObject(2);
        JSONObject resource = item.getJSONObject("playerPowerupResource");

        if (resource.getString("playerPowerupEnum").equals("DAP")) {
            mPlayerPowerupType = PlayerPowerupType.DAP;
        } else {
            System.out.println("unknown player powerup type");
        }
    }

    public static String getNameStatic() {
        return "PLAYER_POWERUP";
    }

    public String getName() {
        return getNameStatic();
    }

    public PlayerPowerupType getPlayerPowerupType() {
        return mPlayerPowerupType;
    }
}

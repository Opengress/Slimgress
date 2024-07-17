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

package net.opengress.slimgress.API.Plext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Common.Team;

public class PlextPlayer extends PlextBase
{
    private final Location mLocation;
    private final Team mTeam;

    public PlextPlayer(JSONArray json) throws JSONException
    {
        super(PlextType.PlayerGenerated, json);

        JSONObject item = json.getJSONObject(2);

        mLocation = new Location(item.getJSONObject("locationE6"));

        JSONObject controllingTeam = item.optJSONObject("controllingTeam");
        if (controllingTeam != null)
            mTeam = new Team(controllingTeam);
        else
            mTeam = new Team("neutral");
    }

    public boolean isSecure()
    {
        for (Markup markup : getMarkups()) {
            if (markup.getType() == Markup.MarkupType.Secure)
                return true;
        }

        return false;
    }

    public Location getLocation()
    {
        return mLocation;
    }

    public Team getTeam()
    {
        return mTeam;
    }
}

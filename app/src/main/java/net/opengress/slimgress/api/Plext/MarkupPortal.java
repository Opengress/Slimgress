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

package net.opengress.slimgress.api.Plext;

import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Common.Team;

import org.json.JSONException;
import org.json.JSONObject;

public class MarkupPortal extends Markup
{
    private final String mGUID;
    private final Team mTeam;
    private final Location mLocation;
    private final String mAddress;
    private final String mName;

    public MarkupPortal(JSONObject json) throws JSONException
    {
        super(MarkupType.Portal, json);
        mGUID = json.getString("guid");
        mTeam = new Team(json.getString("team"));
        mLocation = new Location(json.getInt("latE6"), json.getInt("lngE6"));
        mAddress = json.getString("address");
        mName = json.getString("name");
    }

    public String getGUID()
    {
        return mGUID;
    }

    public Team getTeam()
    {
        return mTeam;
    }

    public Location getLocation()
    {
        return mLocation;
    }

    public String getAddress()
    {
        return mAddress;
    }

    public String getName()
    {
        return mName;
    }
}

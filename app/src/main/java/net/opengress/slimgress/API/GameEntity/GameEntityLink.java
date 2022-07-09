/*

 Slimgress: Ingress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>

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

package net.opengress.slimgress.API.GameEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Common.Team;

public class GameEntityLink extends GameEntityBase
{
    private final String mLinkOriginGuid;
    private final String mLinkDestinationGuid;
    private final Location mLinkOriginLocation;
    private final Location mLinkDestinationLocation;
    private final Team mLinkControllingTeam;

    GameEntityLink(JSONArray json) throws JSONException
    {
        super(GameEntityType.Link, json);

        JSONObject item = json.getJSONObject(2);

        JSONObject edge = item.getJSONObject("edge");

        mLinkOriginGuid = edge.getString("originPortalGuid");
        mLinkDestinationGuid = edge.getString("destinationPortalGuid");
        mLinkOriginLocation = new Location(edge.getJSONObject("originPortalLocation"));
        mLinkDestinationLocation = new Location(edge.getJSONObject("destinationPortalLocation"));
        mLinkControllingTeam = new Team(item.getJSONObject("controllingTeam"));
    }

    public String getLinkOriginGuid()
    {
        return mLinkOriginGuid;
    }

    public String getLinkDestinationGuid()
    {
        return mLinkDestinationGuid;
    }

    public Location getLinkOriginLocation()
    {
        return mLinkOriginLocation;
    }

    public Location getLinkDestinationLocation()
    {
        return mLinkDestinationLocation;
    }

    public Team getLinkControllingTeam()
    {
        return mLinkControllingTeam;
    }
}

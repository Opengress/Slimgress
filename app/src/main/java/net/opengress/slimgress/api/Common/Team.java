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

package net.opengress.slimgress.api.Common;

import androidx.annotation.NonNull;

import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Knobs.TeamKnobs;

import org.json.JSONException;
import org.json.JSONObject;

public class Team
{

    private final TeamKnobs.TeamType mTeam;

    public Team(JSONObject json, TeamKnobs teamKnobs) throws JSONException
    {
        if (!json.has("team"))
            throw new RuntimeException("invalid json object");

        mTeam = teamKnobs.fromString(json.getString("team"));
    }

    public Team(String teamString, TeamKnobs teamKnobs)
    {
        mTeam = teamKnobs.fromString(teamString);
    }

    public Team(JSONObject json) throws JSONException
    {
        if (!json.has("team"))
            throw new RuntimeException("invalid json object");

        mTeam = SlimgressApplication.getInstance().getGame().getKnobs().getTeamKnobs().fromString(json.getString("team"));
    }

    public Team(String teamString)
    {
        mTeam = SlimgressApplication.getInstance().getGame().getKnobs().getTeamKnobs().fromString(teamString);
    }

    public Team(TeamKnobs.TeamType teamType)
    {
        mTeam = teamType;
    }

    public TeamKnobs.TeamType getTeamType()
    {
        return mTeam;
    }

    @NonNull
    public String getID() {
        return mTeam.getId();
    }

    @NonNull
    public String toString()
    {
        return mTeam.getName();
    }

    @NonNull
    public Integer getColour()
    {
        return mTeam.getColour();
    }
}

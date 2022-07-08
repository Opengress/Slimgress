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

package com.norman0406.slimgress.API.Player;

import com.norman0406.slimgress.API.Knobs.PlayerLevelKnobs;
import com.norman0406.slimgress.API.Knobs.TeamKnobs;
import com.norman0406.slimgress.IngressApplication;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;
import java.util.Objects;

public class Agent extends PlayerEntity
{
    private final String mNickname;

    public Agent(JSONArray json, String nickname) throws JSONException
    {
        super(json);
        mNickname = nickname;
    }

    public Agent(JSONArray json, String nickname, TeamKnobs teamKnobs) throws JSONException
    {
        super(json, teamKnobs);
        mNickname = nickname;
    }

    public String getNickname()
    {
        return mNickname;
    }

    public int getLevel()
    {
        // TODO: more efficient?
        // also TODO: badges and stuff

        Map<String, PlayerLevelKnobs.PlayerLevel> playerLevels = IngressApplication.getInstance().getGame().getKnobs().getPlayerLevelKnobs().getPlayerLevelsMap();

        for (int i = playerLevels.size() - 1; i >= 0; i--) {
            if (this.getAp() >= Objects.requireNonNull(playerLevels.get(String.valueOf(i))).getApRequired())
                return i;
        }

        throw new IndexOutOfBoundsException("agent level could not be retrieved");
    }

    public int getEnergyMax()
    {
        return IngressApplication.getInstance().getGame().getKnobs().getPlayerLevelKnobs().getXmCapacityForLevel(getLevel());
    }
}

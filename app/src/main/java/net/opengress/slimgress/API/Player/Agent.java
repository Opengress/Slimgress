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

package net.opengress.slimgress.API.Player;

import net.opengress.slimgress.API.Knobs.PlayerLevelKnobs;
import net.opengress.slimgress.API.Knobs.TeamKnobs;
import net.opengress.slimgress.IngressApplication;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;
import java.util.Objects;

public class Agent extends PlayerEntity
{
    private String mNickname;

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

    public void setNickname(String nickname)
    {
        mNickname = nickname;
    }

    public int getLevel()
    {
        // TODO: more efficient?
        // also TODO: badges and stuff. levels currently capped at 8 because badges not implemented

        Map<String, PlayerLevelKnobs.PlayerLevel> playerLevels = IngressApplication.getInstance().getGame().getKnobs().getPlayerLevelKnobs().getPlayerLevelsMap();

        for (int i = playerLevels.size() - 1; i >= 0; i--) {
            if (this.getAp() >= Objects.requireNonNull(playerLevels.get(String.valueOf(i))).getApRequired())
                return Math.min(i, 8);
        }

        throw new IndexOutOfBoundsException("agent level could not be retrieved");
    }

    public int getEnergyMax()
    {
        return IngressApplication.getInstance().getGame().getKnobs().getPlayerLevelKnobs().getXmCapacityForLevel(getLevel());
    }

    public void addEnergy(int energyAmount) {
        setEnergy(Math.min(getEnergy() + energyAmount, getEnergyMax()));
    }
}

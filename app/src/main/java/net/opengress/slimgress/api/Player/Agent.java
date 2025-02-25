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

package net.opengress.slimgress.api.Player;

import static net.opengress.slimgress.ViewHelpers.TextType.XMGain;
import static net.opengress.slimgress.ViewHelpers.showFloatingText;

import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Knobs.TeamKnobs;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Locale;

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
        postUpdates();
    }

    public int getLevel()
    {
        return getVerifiedLevel();
//        // TODO: more efficient?
//        // also TODO: badges and stuff. levels currently capped at 8 because badges not implemented
//        // TODO: CHECK: is this right - what about levelUp? Seems the server DOES know your level.
//        //  Check ancient handshakes!
//
//        Map<String, PlayerLevelKnobs.PlayerLevel> playerLevels = SlimgressApplication.getInstance().getGame().getKnobs().getPlayerLevelKnobs().getPlayerLevelsMap();
//
//        for (int i = playerLevels.size() - 1; i >= 0; i--) {
//            if (this.getAp() >= Objects.requireNonNull(playerLevels.get(String.valueOf(i))).getApRequired())
//                return Math.min(i, 8);
//        }
//
//        throw new IndexOutOfBoundsException("agent level could not be retrieved");
    }

    public int getEnergyMax()
    {
        return SlimgressApplication.getInstance().getGame().getKnobs().getPlayerLevelKnobs().getXmCapacityForLevel(getLevel());
    }

    public void addAP(int AP) {
        if (AP == 0) {
            return;
        }
        setAP(getAp() + AP);
        postUpdates();
    }

    public void addEnergy(int energyAmount) {
        if (energyAmount == 0) {
            return;
        }
        showFloatingText(String.format(Locale.getDefault(), "+%dXM", energyAmount), XMGain);
        setEnergy(Math.min(getEnergy() + energyAmount, getEnergyMax()));
        postUpdates();
    }

    public void subtractEnergy(int energyAmount) {
        setEnergy(Math.max(getEnergy() - energyAmount, 0));
        postUpdates();
    }

    public void postUpdates() {
        // why not post this?
        SlimgressApplication.getInstance().getPlayerDataViewModel().postAgent(SlimgressApplication.getInstance().getGame().getAgent());
    }

    @Override
    public void update(PlayerEntity entity) {
        super.update(entity);
        postUpdates();
    }

    @Override
    public void setTeam(Team team) {
        super.setTeam(team);
        postUpdates();
    }
}

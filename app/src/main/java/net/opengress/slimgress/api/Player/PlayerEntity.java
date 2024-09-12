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

import net.opengress.slimgress.api.Common.EntityBase;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Knobs.TeamKnobs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PlayerEntity extends EntityBase
{
    /*
    Representing the three states XM_OK, XM_DEPLETED and XM_UNDEFINED
     */
    public enum EnergyState
    {
        OK,
        Depleted,
        Undefined
    }

    private Team mTeam;
    private int mAP;
    private int mEnergy;
    private EnergyState mEnergyState;
    // maybe called clientLevel in some versions. what does it do?
    private int mVerifiedLevel;
    private boolean mAllowNicknameEdit;
    private boolean mAllowFactionChoice;

    public PlayerEntity(JSONArray json) throws JSONException
    {
        super(json);

        JSONObject playerEntity = json.getJSONObject(2);
        mTeam = new Team(playerEntity.getJSONObject("controllingTeam"));

        JSONObject playerPersonal = playerEntity.optJSONObject("playerPersonal");
        if (playerPersonal != null) {
            mAP = Integer.parseInt(playerPersonal.getString("ap"));
            mEnergy = playerPersonal.getInt("energy");

            String energyStateString = playerPersonal.getString("energyState");
            if (energyStateString.equals("XM_OK")) {
                mEnergyState = EnergyState.OK;
            } else if (energyStateString.equals("XM_DEPLETED")) {
                mEnergyState = EnergyState.Depleted;
            } else {
                mEnergyState = EnergyState.Undefined;
                throw new RuntimeException("unknown energy state");
            }

            mVerifiedLevel = playerPersonal.getInt("verifiedLevel");
            mAllowNicknameEdit = playerPersonal.getBoolean("allowNicknameEdit");
            mAllowFactionChoice = playerPersonal.getBoolean("allowFactionChoice");
        }
    }

    public PlayerEntity(JSONArray json, TeamKnobs teamKnobs) throws JSONException
    {
        super(json);

        JSONObject playerEntity = json.getJSONObject(2);
        mTeam = new Team(playerEntity.getJSONObject("controllingTeam"), teamKnobs);

        JSONObject playerPersonal = playerEntity.optJSONObject("playerPersonal");
        if (playerPersonal != null) {
            mAP = Integer.parseInt(playerPersonal.getString("ap"));
            mEnergy = playerPersonal.getInt("energy");

            String energyStateString = playerPersonal.getString("energyState");
            if (energyStateString.equals("XM_OK")) {
                mEnergyState = EnergyState.OK;
            } else if (energyStateString.equals("XM_DEPLETED")) {
                mEnergyState = EnergyState.Depleted;
            } else {
                mEnergyState = EnergyState.Undefined;
                throw new RuntimeException("unknown energy state");
            }

            mVerifiedLevel = playerPersonal.getInt("verifiedLevel");
            mAllowNicknameEdit = playerPersonal.getBoolean("allowNicknameEdit");
            mAllowFactionChoice = playerPersonal.getBoolean("allowFactionChoice");
        }
    }

    public void update(PlayerEntity entity)
    {
        mTeam = entity.mTeam;
        mAP = entity.mAP;
        mEnergy = entity.mEnergy;
        mEnergyState = entity.mEnergyState;
        mVerifiedLevel = entity.mVerifiedLevel;
        mAllowNicknameEdit = entity.mAllowNicknameEdit;
        mAllowFactionChoice = entity.mAllowFactionChoice;
    }

    public Team getTeam()
    {
        return mTeam;
    }

    public int getAp()
    {
        return mAP;
    }

    public void setAP(int newAP) {
        mAP = newAP;
    }

    public int getEnergy()
    {
        return mEnergy;
    }

    public void setEnergy(int newEnergy) {
        mEnergy = newEnergy;
    }

    public EnergyState getEnergyState()
    {
        return mEnergyState;
    }

    public int getVerifiedLevel()
    {
        return mVerifiedLevel;
    }

    public boolean isAllowNicknameEdit()
    {
        return mAllowNicknameEdit;
    }

    public boolean isAllowFactionChoice()
    {
        return mAllowFactionChoice;
    }
}
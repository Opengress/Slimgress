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

package net.opengress.slimgress.api.Interface;

import org.json.JSONException;
import org.json.JSONObject;

public class APGain
{
    public enum Trigger
    {
        CapturedPortal,
        CreatedField,
        CreatedLink,
        DeployedMod,
        DeployedResonator,
        DestroyedField,
        DestroyedLink,
        DestroyedResonator,
        FullyDeployedPortal,
        HackingEnemyPortal,
        InvitedPlayerJoined,
        RechargeResonator,
        RedeemedAP,
        RemoteRechargeResonator,
        Unknown,
        UpgradeResonator
    }

    private final int mAmount;
    private final Trigger mTrigger;

    public APGain(JSONObject json) throws NumberFormatException, JSONException
    {
        mAmount = Integer.parseInt(json.getString("apGainAmount"));
        String trigger = json.getString("apTrigger");
        switch (trigger) {
            case "CAPTURED_PORTAL" -> mTrigger = Trigger.CapturedPortal;
            case "CREATED_A_PORTAL_REGION" -> mTrigger = Trigger.CreatedField;
            case "CREATED_PORTAL_LINK" -> mTrigger = Trigger.CreatedLink;
            case "DEPLOYED_RESONATOR" -> mTrigger = Trigger.DeployedResonator;
            case "DEPLOYED_RESONATOR_MOD" -> mTrigger = Trigger.DeployedMod;
            case "DESTROYED_A_PORTAL_LINK" -> mTrigger = Trigger.DestroyedLink;
            case "DESTROYED_A_RESONATOR" -> mTrigger = Trigger.DestroyedResonator;
            case "DESTROYED_PORTAL_REGION" -> mTrigger = Trigger.DestroyedField;
            case "HACKING_ENEMY_PORTAL" -> mTrigger = Trigger.HackingEnemyPortal;
            case "INVITED_PLAYER_JOINED" -> mTrigger = Trigger.InvitedPlayerJoined;
            case "PORTAL_FULLY_POPULATED_WITH_RESONATORS" -> mTrigger = Trigger.FullyDeployedPortal;
            case "RECHARGE_RESONATOR" -> mTrigger = Trigger.RechargeResonator;
            case "REDEEMED_AP" -> mTrigger = Trigger.RedeemedAP;
            case "REMOTE_RECHARGE_RESONATOR" -> mTrigger = Trigger.RemoteRechargeResonator;
            case "UPGRADE_SOMEONE_ELSES_RESONATOR" -> mTrigger = Trigger.UpgradeResonator;
            default -> mTrigger = Trigger.Unknown;
        }
    }

    public int getAmount()
    {
        return mAmount;
    }

    public Trigger getTrigger()
    {
        return mTrigger;
    }
}

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

package net.opengress.slimgress.API.Interface;

import org.json.JSONException;
import org.json.JSONObject;

public class APGain
{
    public enum Trigger
    {
        Unknown,
        DeployedResonator,
        CapturedPortal,
        CreatedLink,
        CreatedField,
        DestroyedResonator,
        DestroyedLink,
        DestroyedField,
        DeployedMod,
        FullyDeployedPortal,
        HackingEnemyPortal,
        RedeemedAP,
        RechargeResonator,
        RemoteRechargeResonator,
        InvitedPlayerJoined
    }

    private final int mAmount;
    private final Trigger mTrigger;

    public APGain(JSONObject json) throws NumberFormatException, JSONException
    {
        mAmount = Integer.parseInt(json.getString("apGainAmount"));
        String trigger = json.getString("apTrigger");
        switch (trigger) {
            case "DEPLOYED_RESONATOR" -> mTrigger = Trigger.DeployedResonator;
            case "CAPTURED_PORTAL" -> mTrigger = Trigger.CapturedPortal;
            case "CREATED_PORTAL_LINK" -> mTrigger = Trigger.CreatedLink;
            case "CREATED_A_PORTAL_REGION" -> mTrigger = Trigger.CreatedField;
            case "DESTROYED_A_RESONATOR" -> mTrigger = Trigger.DestroyedResonator;
            case "DESTROYED_A_PORTAL_LINK" -> mTrigger = Trigger.DestroyedLink;
            case "DESTROYED_PORTAL_REGION" -> mTrigger = Trigger.DestroyedField;
            case "DEPLOYED_RESONATOR_MOD" -> mTrigger = Trigger.DeployedMod;
            case "PORTAL_FULLY_POPULATED_WITH_RESONATORS" -> mTrigger = Trigger.FullyDeployedPortal;
            case "HACKING_ENEMY_PORTAL" -> mTrigger = Trigger.HackingEnemyPortal;
            case "REDEEMED_AP" -> mTrigger = Trigger.RedeemedAP;
            case "RECHARGE_RESONATOR" -> mTrigger = Trigger.RechargeResonator;
            case "REMOTE_RECHARGE_RESONATOR" -> mTrigger = Trigger.RemoteRechargeResonator;
            case "INVITED_PLAYER_JOINED" -> mTrigger = Trigger.InvitedPlayerJoined;
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

/**********************************************************************

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

package com.norman0406.slimgress.API.Interface;

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
        FullyDevloyedPortal,
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
            case "DEPLOYED_RESONATOR":
                mTrigger = Trigger.DeployedResonator;
                break;
            case "CAPTURED_PORTAL":
                mTrigger = Trigger.CapturedPortal;
                break;
            case "CREATED_PORTAL_LINK":
                mTrigger = Trigger.CreatedLink;
                break;
            case "CREATED_A_PORTAL_REGION":
                mTrigger = Trigger.CreatedField;
                break;
            case "DESTROYED_A_RESONATOR":
                mTrigger = Trigger.DestroyedResonator;
                break;
            case "DESTROYED_A_PORTAL_LINK":
                mTrigger = Trigger.DestroyedLink;
                break;
            case "DESTROYED_PORTAL_REGION":
                mTrigger = Trigger.DestroyedField;
                break;
            case "DEPLOYED_RESONATOR_MOD":
                mTrigger = Trigger.DeployedMod;
                break;
            case "PORTAL_FULLY_POPULATED_WITH_RESONATORS":
                mTrigger = Trigger.FullyDevloyedPortal;
                break;
            case "HACKING_ENEMY_PORTAL":
                mTrigger = Trigger.HackingEnemyPortal;
                break;
            case "REDEEMED_AP":
                mTrigger = Trigger.RedeemedAP;
                break;
            case "RECHARGE_RESONATOR":
                mTrigger = Trigger.RechargeResonator;
                break;
            case "REMOTE_RECHARGE_RESONATOR":
                mTrigger = Trigger.RemoteRechargeResonator;
                break;
            case "INVITED_PLAYER_JOINED":
                mTrigger = Trigger.InvitedPlayerJoined;
                break;
            default:
                mTrigger = Trigger.Unknown;
                break;
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

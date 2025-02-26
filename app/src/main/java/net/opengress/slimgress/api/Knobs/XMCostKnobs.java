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

package net.opengress.slimgress.api.Knobs;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// TODO: make getXByLevel accept the level (maybe)
public class XMCostKnobs extends Knobs
{
    // these are annoying: passing in the level you're interested in gives an off-by-one error.
    // maybe i should fix that in more sophisticated getters.

    //    @Deprecated
//    private final List<Integer> mHeatsinkDeployCostByLevel;
    private final List<Integer> mFlipCardCostByLevel;
    //    @Deprecated
//    private final List<Integer> mTurretDeployCostByLevel;
    private final List<Integer> mPortalHackNeutralCostByLevel;
    //    @Deprecated
//    private final List<Integer> mShieldDeployCostByLevel;
    private final List<Integer> mXmpFiringCostByLevel;
    private final List<Integer> mResonatorUpgradeCostByLevel;
    private final List<Integer> mPortalHackFriendlyCostByLevel;
    //    @Deprecated
//    private final List<Integer> mMultihackDeployCostByLevel;
    private final List<Integer> mPortalHackEnemyCostByLevel;
    private final List<Integer> mResonatorDeployCostByLevel;
    //    @Deprecated
//    private final List<Integer> mForceAmplifierDeployCostByLevel;
//    @Deprecated
//    private final List<Integer> mLinkAmplifierDeployCostByLevel;
    private final Map<String, List<Integer>> mPortalModByLevel;
    private final int mCreateLinkCost;

    public XMCostKnobs(JSONObject json) throws JSONException
    {
        super(json);

        mFlipCardCostByLevel = getIntArray(json, "flipCardCostByLevel");
//        mForceAmplifierDeployCostByLevel = getIntArray(json, "forceAmplifierDeployCostByLevel");
//        mHeatsinkDeployCostByLevel = getIntArray(json, "heatsinkDeployCostByLevel");
//        mLinkAmplifierDeployCostByLevel = getIntArray(json, "linkAmplifierDeployCostByLevel");
//        mMultihackDeployCostByLevel = getIntArray(json, "multihackDeployCostByLevel");
        mPortalHackEnemyCostByLevel = getIntArray(json, "portalHackEnemyCostByLevel");
        mPortalHackFriendlyCostByLevel = getIntArray(json, "portalHackFriendlyCostByLevel");
        mPortalHackNeutralCostByLevel = getIntArray(json, "portalHackNeutralCostByLevel");
        mResonatorDeployCostByLevel = getIntArray(json, "resonatorDeployCostByLevel");
        mResonatorUpgradeCostByLevel = getIntArray(json, "resonatorUpgradeCostByLevel");
//        mShieldDeployCostByLevel = getIntArray(json, "shieldDeployCostByLevel");
//        mTurretDeployCostByLevel = getIntArray(json, "turretDeployCostByLevel");
        mXmpFiringCostByLevel = getIntArray(json, "xmpFiringCostByLevel");
        mCreateLinkCost = json.optInt("createLinkCost", 250);

        mPortalModByLevel = new HashMap<>();
        JSONObject portalModByLevel = json.getJSONObject("portalModByLevel");
        Iterator<?> it = portalModByLevel.keys();
        while (it.hasNext()) {
            String key = (String)it.next();
            mPortalModByLevel.put(key, getIntArray(portalModByLevel, key));
        }
    }

    public List<Integer> getFlipCardCostByLevel()
    {
        return mFlipCardCostByLevel;
    }

    public List<Integer> getPortalHackNeutralCostByLevel()
    {
        return mPortalHackNeutralCostByLevel;
    }

    public List<Integer> getXmpFiringCostByLevel()
    {
        return mXmpFiringCostByLevel;
    }

    public List<Integer> getResonatorUpgradeCostByLevel()
    {
        return mResonatorUpgradeCostByLevel;
    }

    public List<Integer> getPortalHackFriendlyCostByLevel()
    {
        return mPortalHackFriendlyCostByLevel;
    }

    public List<Integer> getPortalHackEnemyCostByLevel()
    {
        return mPortalHackEnemyCostByLevel;
    }

    public List<Integer> getResonatorDeployCostByLevel()
    {
        return mResonatorDeployCostByLevel;
    }

    public List<Integer> getPortalModByLevel(String key)
    {
        if (!mPortalModByLevel.containsKey(key))
            Log.e("XMCostKnobs", "key not found in hash map: " + key);

        return mPortalModByLevel.get(key);
    }

    public int getLinkCreationCost() {
        return mCreateLinkCost;
    }
}

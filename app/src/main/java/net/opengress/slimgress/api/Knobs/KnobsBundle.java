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

import org.json.JSONException;
import org.json.JSONObject;

public class KnobsBundle
{
    private final long mSyncTimestamp;
    private final ClientFeatureKnobs mClientFeatureKnobs;
    private final InventoryKnobs mInventoryKnobs;
    private final PortalKnobs mPortalKnobs;
    private final RecycleKnobs mRecycleKnobs;
    private final WeaponRangeKnobs mWeaponRangeKnobs;
    private final XMCostKnobs mXMCostKnobs;
    private final ScannerKnobs mScannerKnobs;
    private final PortalModSharedKnobs mPortalModSharedKnobs;
    private final NearbyPortalKnobs mNearbyPortalKnobs;
    private final PlayerLevelKnobs mPlayerLevelKnobs;
    private final TeamKnobs mTeamKnobs;

    public KnobsBundle(JSONObject json) throws JSONException
    {
        mSyncTimestamp = Long.parseLong(json.getString("syncTimestamp"));

        JSONObject bundleMap = json.getJSONObject("bundleMap");

        mClientFeatureKnobs = new ClientFeatureKnobs(bundleMap.getJSONObject("ClientFeatureKnobs"));
        // smartnotificationclient knobs
        // glyphcommandclient knobs
        mInventoryKnobs = new InventoryKnobs(bundleMap.getJSONObject("InventoryKnobs"));
        mPortalKnobs = new PortalKnobs(bundleMap.getJSONObject("PortalKnobs"));
        mRecycleKnobs = new RecycleKnobs(bundleMap.getJSONObject("recycleKnobs"));
        mWeaponRangeKnobs = new WeaponRangeKnobs(bundleMap.getJSONObject("WeaponRangeKnobs"));
        mXMCostKnobs = new XMCostKnobs(bundleMap.getJSONObject("XmCostKnobs"));
        mScannerKnobs = new ScannerKnobs(bundleMap.getJSONObject("ScannerKnobs"));
        mPortalModSharedKnobs = new PortalModSharedKnobs(bundleMap.getJSONObject("PortalModSharedKnobs"));
        mNearbyPortalKnobs = new NearbyPortalKnobs(bundleMap.getJSONObject("NearbyPortalKnobs"));
        mPlayerLevelKnobs = new PlayerLevelKnobs(bundleMap.getJSONObject("PlayerLevelKnobs"));
        mTeamKnobs = new TeamKnobs(bundleMap.getJSONObject("TeamKnobs"));

    }

    public long getSyncTimestamp()
    {
        return mSyncTimestamp;
    }

    public ClientFeatureKnobs getClientFeatureKnobs()
    {
        return mClientFeatureKnobs;
    }

    public InventoryKnobs getInventoryKnobs()
    {
        return mInventoryKnobs;
    }

    public PortalKnobs getPortalKnobs()
    {
        return mPortalKnobs;
    }

    public RecycleKnobs getRecycleKnobs()
    {
        return mRecycleKnobs;
    }

    public WeaponRangeKnobs getWeaponRangeKnobs()
    {
        return mWeaponRangeKnobs;
    }

    public XMCostKnobs getXMCostKnobs()
    {
        return mXMCostKnobs;
    }

    public ScannerKnobs getScannerKnobs()
    {
        return mScannerKnobs;
    }

    public PortalModSharedKnobs getPortalModSharedKnobs()
    {
        return mPortalModSharedKnobs;
    }

    public NearbyPortalKnobs getNearbyPortalKnobs()
    {
        return mNearbyPortalKnobs;
    }

    public PlayerLevelKnobs getPlayerLevelKnobs()
    {
        return mPlayerLevelKnobs;
    }

    public TeamKnobs getTeamKnobs()
    {
        return mTeamKnobs;
    }
}

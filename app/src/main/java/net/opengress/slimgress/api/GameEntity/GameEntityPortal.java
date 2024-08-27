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

package net.opengress.slimgress.api.GameEntity;

import static net.opengress.slimgress.ViewHelpers.getBearingFromSlot;

import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Knobs.PortalKnobs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.util.LinkedList;
import java.util.List;

public class GameEntityPortal extends GameEntityBase
{
    public class LinkedEdge
    {
        public String edgeGuid;
        public String otherPortalGuid;
        public boolean isOrigin;
    }

    public class LinkedMod
    {
        // TODO: UNDONE: link with ItemMod?

        public String installingUser;
        public String displayName;
    }

    public class LinkedResonator
    {
        public int distanceToPortal;
        public int energyTotal;
        public int slot;
        public String id;
        public String ownerGuid;
        public int level;
        public int getMaxEnergy() {
            return mPortalKnobs.getResonatorEnergyForLevel(level);
        }

        public GeoPoint getResoCoordinates() {
            int angle = getBearingFromSlot(slot);
            net.opengress.slimgress.api.Common.Location location = getPortalLocation();
            return location.getLatLng().destinationPoint(distanceToPortal, angle);
        }
    }

    private final Location mPortalLocation;
    private final Team mPortalTeam;
    private final String mPortalTitle;
    private final String mPortalAddress;
    private final String mPortalAttribution;
    private final String mPortalAttributionLink;
    private String mPortalImageUrl;
    private final List<LinkedEdge> mPortalEdges;
    private final List<LinkedMod> mPortalMods;
    private final List<LinkedResonator> mPortalResonators;
    private final PortalKnobs mPortalKnobs = SlimgressApplication.getInstance().getGame().getKnobs().getPortalKnobs();

    GameEntityPortal(JSONArray json) throws JSONException
    {
        super(GameEntityType.Portal, json);

        JSONObject item = json.getJSONObject(2);

        mPortalTeam = new Team(item.getJSONObject("controllingTeam"));
        mPortalLocation = new Location(item.getJSONObject("locationE6"));

        JSONObject imageUrl = item.optJSONObject("imageByUrl");
        if (imageUrl != null)
            mPortalImageUrl = imageUrl.getString("imageUrl");

        JSONObject portalV2 = item.getJSONObject("portalV2");

        // get edges
        mPortalEdges = new LinkedList<>();
        JSONArray portalEdges = portalV2.getJSONArray("linkedEdges");
        for (int i = 0; i < portalEdges.length(); i++) {
            JSONObject edge = portalEdges.getJSONObject(i);

            LinkedEdge newEdge = new LinkedEdge();
            newEdge.edgeGuid = edge.getString("edgeGuid");
            newEdge.otherPortalGuid = edge.getString("otherPortalGuid");
            newEdge.isOrigin = edge.getBoolean("isOrigin");
            mPortalEdges.add(newEdge);
        }

        // get mods
        mPortalMods = new LinkedList<>();
        JSONArray portalMods = portalV2.getJSONArray("linkedModArray");
        for (int i = 0; i < portalMods.length(); i++) {
            JSONObject mod = portalMods.optJSONObject(i);

            if (mod != null) {
                LinkedMod newMod = new LinkedMod();
                newMod.installingUser = mod.getString("installingUser");
                newMod.displayName = mod.getString("displayName");

                // TODO: UNDONE

                mPortalMods.add(newMod);
            }
            else {
                // mod == null means the slot is unused
                mPortalMods.add(null);
            }
        }

        // get description
//        JSONObject descriptiveText = portalV2.getJSONObject("descriptiveText");
        JSONObject descriptiveText = item.getJSONObject("descriptiveText").getJSONObject("map");
        mPortalTitle = descriptiveText.getString("TITLE");
        mPortalAddress = descriptiveText.optString("ADDRESS");
        mPortalAttribution = descriptiveText.optString("ATTRIBUTION");
        mPortalAttributionLink = descriptiveText.optString("ATTRIBUTION_LINK");

        // get resonators
        mPortalResonators = new LinkedList<>();
        JSONObject resonatorArray = item.getJSONObject("resonatorArray");
        JSONArray resonators = resonatorArray.getJSONArray("resonators");
        for (int i = 0; i < resonators.length(); i++) {
            JSONObject resonator = resonators.optJSONObject(i);

            if (resonator != null) {
                LinkedResonator newResonator = new LinkedResonator();
                newResonator.level = resonator.getInt("level");
                newResonator.distanceToPortal = resonator.getInt("distanceToPortal");
                newResonator.ownerGuid = resonator.getString("ownerGuid");
                newResonator.energyTotal = resonator.getInt("energyTotal");
                newResonator.slot = resonator.getInt("slot");
                newResonator.id = resonator.getString("id");

                mPortalResonators.add(newResonator);
            }
            else {
                // resonator == null means the slot is unused .... do i want this? slot property...
                mPortalResonators.add(null);
            }
        }
    }

    public int getPortalEnergy()
    {
        // TODO: don't recalculate every time
        int energy = 0;
        for (LinkedResonator resonator : mPortalResonators) {
            if (resonator != null)
                energy += resonator.energyTotal;
        }
        return energy;
    }

    public int getPortalMaxEnergy()
    {
        // TODO: don't recalculate every time
        int energy = 0;
        for (LinkedResonator resonator : mPortalResonators) {
            if (resonator != null)
                energy += resonator.getMaxEnergy();
        }
        return energy;
    }

    public int getPortalLevel()
    {
        // TODO: don't recalculate every time...
        int level = 0;
        for (LinkedResonator resonator : mPortalResonators) {
            if (resonator != null) {
                level += resonator.level;
            }
        }

        level /= 8;

        return level == 0 ? 1 : level;
    }

    public int getPortalLinkRange() {
        // TODO: consider mods like link amps
        // this number could change one day?
        if (mPortalResonators.size() < 8) {
            return 0;
        }
        return (int) Math.pow(getPortalLevel(), 4) * 160;
    }

    public Location getPortalLocation()
    {
        return mPortalLocation;
    }

    public Team getPortalTeam()
    {
        return mPortalTeam;
    }

    public String getPortalTitle()
    {
        return mPortalTitle;
    }

    public String getPortalAddress()
    {
        return mPortalAddress;
    }

    public String getPortalAttribution()
    {
        return mPortalAttribution;
    }

    public String getPortalAttributionLink()
    {
        return mPortalAttributionLink;
    }

    public String getPortalImageUrl()
    {
        return mPortalImageUrl;
    }

    public List<LinkedEdge> getPortalEdges()
    {
        return mPortalEdges;
    }

    public List<LinkedMod> getPortalMods()
    {
        return mPortalMods;
    }

    public List<LinkedResonator> getPortalResonators()
    {
        return mPortalResonators;
    }

    public int getPortalResonatorCount() {
        int resos = 0;
        for (LinkedResonator reso : mPortalResonators) {
            if (reso != null) {
                resos++;
            }
        }
        return resos;
    }

    public LinkedResonator getPortalResonator(int slot) {
        for (LinkedResonator reso : mPortalResonators) {
            if (reso != null && reso.slot == slot) {
                return reso;
            }
        }
        return null;
    }
}

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

import static com.google.common.geometry.S2LatLng.EARTH_RADIUS_METERS;
import static net.opengress.slimgress.ViewHelpers.getBearingFromSlot;
import static net.opengress.slimgress.api.Item.ItemBase.ItemType;
import static net.opengress.slimgress.api.Item.ItemBase.Rarity;

import com.google.common.geometry.S2LatLng;

import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Item.ItemModForceAmp;
import net.opengress.slimgress.api.Item.ItemModHeatsink;
import net.opengress.slimgress.api.Item.ItemModLinkAmp;
import net.opengress.slimgress.api.Item.ItemModMultihack;
import net.opengress.slimgress.api.Item.ItemModShield;
import net.opengress.slimgress.api.Item.ItemModTurret;
import net.opengress.slimgress.api.Knobs.PortalKnobs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.geometry.LatLng;

import java.util.HashMap;
import java.util.Iterator;
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
        public HashMap<String, String> stats = new HashMap<>();
        public Rarity rarity;
        public String displayName;
        public ItemType type;
//        public int slot;
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

        public Location getResoLocation() {
            int angle = getBearingFromSlot(slot);
            return destinationPoint(getPortalLocation(), distanceToPortal, angle);
        }

        public LatLng getResoLatLng() {
            int angle = getBearingFromSlot(slot);
            return destinationPoint(getPortalLocation().getLatLng(), distanceToPortal, angle);
        }

        public Location getResoS2LatLng() {
            int angle = getBearingFromSlot(slot);
            return mPortalLocation.destinationPoint(distanceToPortal, angle);
        }

        // Helper method to calculate the destination point using S2LatLng directly
        public LatLng destinationPoint(LatLng start, double distance, double bearing) {
            double distanceRadians = distance / EARTH_RADIUS_METERS;  // Convert distance from meters to radians
            double bearingRadians = Math.toRadians(bearing);  // Convert bearing to radians

            // Spherical trigonometry to calculate the new point
            double lat1 = Math.toRadians(start.getLatitude());
            double lon1 = Math.toRadians(start.getLongitude());

            // Calculate new latitude
            double newLat = Math.asin(Math.sin(lat1) * Math.cos(distanceRadians) +
                    Math.cos(lat1) * Math.sin(distanceRadians) * Math.cos(bearingRadians));

            // Calculate new longitude
            double newLon = lon1 + Math.atan2(Math.sin(bearingRadians) * Math.sin(distanceRadians) * Math.cos(lat1),
                    Math.cos(distanceRadians) - Math.sin(lat1) * Math.sin(newLat));

            // Return the result as an S2LatLng
            return new LatLng(Math.toDegrees(newLat), Math.toDegrees(newLon));
        }

        // Helper method to calculate the destination point using S2LatLng directly
        public Location destinationPoint(Location start, double distance, double bearing) {
            double distanceRadians = distance / EARTH_RADIUS_METERS;  // Convert distance from meters to radians
            double bearingRadians = Math.toRadians(bearing);  // Convert bearing to radians

            // Spherical trigonometry to calculate the new point
            double lat1 = Math.toRadians(start.getLatitude());
            double lon1 = Math.toRadians(start.getLongitude());

            // Calculate new latitude
            double newLat = Math.asin(Math.sin(lat1) * Math.cos(distanceRadians) +
                    Math.cos(lat1) * Math.sin(distanceRadians) * Math.cos(bearingRadians));

            // Calculate new longitude
            double newLon = lon1 + Math.atan2(Math.sin(bearingRadians) * Math.sin(distanceRadians) * Math.cos(lat1),
                    Math.cos(distanceRadians) - Math.sin(lat1) * Math.sin(newLat));

            // Return the result as an S2LatLng
            return new Location(Math.toDegrees(newLat), Math.toDegrees(newLon));
        }

        // Helper method to calculate the destination point using S2LatLng directly
        public S2LatLng destinationPoint(S2LatLng start, double distance, double bearing) {
            double distanceRadians = distance / EARTH_RADIUS_METERS;  // Convert distance from meters to radians
            double bearingRadians = Math.toRadians(bearing);  // Convert bearing to radians

            // Spherical trigonometry to calculate the new point
            double lat1 = start.lat().radians();
            double lon1 = start.lng().radians();

            // Calculate new latitude
            double newLat = Math.asin(Math.sin(lat1) * Math.cos(distanceRadians) +
                    Math.cos(lat1) * Math.sin(distanceRadians) * Math.cos(bearingRadians));

            // Calculate new longitude
            double newLon = lon1 + Math.atan2(Math.sin(bearingRadians) * Math.sin(distanceRadians) * Math.cos(lat1),
                    Math.cos(distanceRadians) - Math.sin(lat1) * Math.sin(newLat));

            // Return the result as an S2LatLng
            return S2LatLng.fromRadians(newLat, newLon);
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
                switch (mod.getString("rarity")) {
                    case "VERY_COMMON" -> newMod.rarity = Rarity.VeryCommon;
                    case "COMMON" -> newMod.rarity = Rarity.Common;
                    case "LESS_COMMON" -> newMod.rarity = Rarity.LessCommon;
                    case "RARE" -> newMod.rarity = Rarity.Rare;
                    case "VERY_RARE" -> newMod.rarity = Rarity.VeryRare;
                    case "EXTREMELY_RARE" -> newMod.rarity = Rarity.ExtraRare;
                }
                String type = mod.getString("type");
                if (type.equals(ItemModShield.getNameStatic())) {
                    newMod.type = ItemType.ModShield;
                } else if (type.equals(ItemModForceAmp.getNameStatic())) {
                    newMod.type = ItemType.ModForceAmp;
                } else if (type.equals(ItemModHeatsink.getNameStatic())) {
                    newMod.type = ItemType.ModHeatsink;
                } else if (type.equals(ItemModLinkAmp.getNameStatic())) {
                    newMod.type = ItemType.ModLinkAmp;
                } else if (type.equals(ItemModTurret.getNameStatic())) {
                    newMod.type = ItemType.ModTurret;
                } else if (type.equals(ItemModMultihack.getNameStatic())) {
                    newMod.type = ItemType.ModMultihack;
                }
//                newMod.slot = i;
                var stats = mod.getJSONObject("stats");
                for (Iterator<String> it = stats.keys(); it.hasNext(); ) {
                    var key = it.next();
                    newMod.stats.put(key, stats.getString(key));
                }

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
        return (int) Math.pow(getPortalLevel(), 4) * 160 * getLinkRangeMultiplier();
    }

    public int getPortalMitigation() {
        // TODO consider links, don't recalculate every time
        int mitigation = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("MITIGATION")) {
                mitigation += Integer.parseInt(mod.stats.get("MITIGATION"));
            }
        }
        return Math.min(mitigation, 95);
    }

    public int getHacksUntilBurnout() {
        // TODO don't recalculate every time
        int hacks = 4;
        int multihacks = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("BURNOUT_INSULATION")) {
                int multiplier = getDiminishingValue("BURNOUT_INSULATION", multihacks) / 1000;
                hacks += multiplier * Integer.parseInt(mod.stats.get("BURNOUT_INSULATION"));
                multihacks++;
            }
        }
        return hacks;
    }


    public int getCooldownSecs() {
        // TODO don't recalculate every time
        float hack_speed = 0;
        int heatsinks = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("HACK_SPEED")) {
                int multiplier = getDiminishingValue("HACK_SPEED", heatsinks);
                hack_speed += multiplier * Float.parseFloat(mod.stats.get("HACK_SPEED")) / 1000000000;
                heatsinks++;
            }
        }
        return Math.round(300 * (1 - hack_speed));
    }

    public int getForceAmplification() {
        // TODO don't recalculate every time
        int force_amplification = 0;
        int forceAmps = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("FORCE_AMPLIFIER")) {
                int multiplier = getDiminishingValue("FORCE_AMPLIFIER", forceAmps) / 1000;
                force_amplification += multiplier * Integer.parseInt(mod.stats.get("FORCE_AMPLIFIER")) / 1000;
                forceAmps++;
            }
        }
        return force_amplification;
    }

    public int getAttackFrequency() {
        // TODO don't recalculate every time
        int attackFrequency = 0;
        int turrets = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("ATTACK_FREQUENCY")) {
                int multiplier = getDiminishingValue("ATTACK_FREQUENCY", turrets) / 1000;
                attackFrequency += multiplier * Integer.parseInt(mod.stats.get("ATTACK_FREQUENCY")) / 10;
                turrets++;
            }
        }
        return attackFrequency;
    }

    public int getHitBonus() {
        // TODO don't recalculate every time
        int hitBonus = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("HIT_BONUS")) {
                hitBonus += Integer.parseInt(mod.stats.get("HIT_BONUS")) / 10000;
            }
        }
        return hitBonus;
    }

    public int getLinkRangeMultiplier() {
        // TODO don't recalculate every time
        int linkRangeMultiplier = 0;
        int linkAmps = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("LINK_RANGE_MULTIPLIER")) {
                int multiplier = getDiminishingValue("LINK_RANGE_MULTIPLIER", linkAmps) / 1000;
                linkRangeMultiplier += multiplier * Integer.parseInt(mod.stats.get("LINK_RANGE_MULTIPLIER")) / 1000;
                linkAmps++;
            }
        }
        if (linkRangeMultiplier == 0) {
            linkRangeMultiplier = 1;
        }
        return linkRangeMultiplier;
    }

    public int getOutgoingLinksBonus() {
        // TODO don't recalculate every time
        int outgoingLinksBonus = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("OUTGOING_LINKS_BONUS")) {
                outgoingLinksBonus += Integer.parseInt(mod.stats.get("OUTGOING_LINKS_BONUS"));
            }
        }
        return outgoingLinksBonus;
    }

    public float getLinkDefenseBoost() {
        // TODO don't recalculate every time
        float linkDefenseBoost = 0;
        for (LinkedMod mod : getPortalMods()) {
            if (mod != null && mod.stats.containsKey("LINK_DEFENSE_BOOST")) {
                linkDefenseBoost += Float.parseFloat(mod.stats.get("LINK_DEFENSE_BOOST")) / 1000;
            }
        }
        return linkDefenseBoost;
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

    private int getDiminishingValue(String key, int index) {
        return SlimgressApplication.getInstance().getGame().getKnobs().getPortalModSharedKnobs().getDiminishingValues(key).get(index);
    }

    private int getDirectValue(String key, int index) {
        return SlimgressApplication.getInstance().getGame().getKnobs().getPortalModSharedKnobs().getDirectValues(key).get(index);
    }
}

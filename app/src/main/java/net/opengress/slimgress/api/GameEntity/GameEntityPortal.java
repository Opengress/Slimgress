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

import androidx.annotation.NonNull;

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
import net.opengress.slimgress.api.Plext.MarkupPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.geometry.LatLng;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class GameEntityPortal extends GameEntityBase implements Serializable {
    public static class LinkedEdge
    {
        public String edgeGuid;
        public String otherPortalGuid;
        public boolean isOrigin;
    }

    public static class LinkedMod
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
        public LatLng destinationPoint(@NonNull LatLng start, double distance, double bearing) {
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
        public Location destinationPoint(@NonNull Location start, double distance, double bearing) {
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
        public S2LatLng destinationPoint(@NonNull S2LatLng start, double distance, double bearing) {
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

    private Location mPortalLocation;
    private Team mPortalTeam;
    private String mPortalTitle;
    private String mPortalDescription;
    private String mPortalAddress;
    private String mPortalAttribution;
    private String mPortalAttributionLink;
    private String mPortalImageUrl;
    private MarkupPlayer mDiscoverer;
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
        if (imageUrl != null) {
            mPortalImageUrl = imageUrl.getString("imageUrl");
        }

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

        // get discoverer - this will need expanded once PhotoStreams come in
        JSONObject discoverer = item.optJSONObject("discoverer");
        if (discoverer != null) {
            mDiscoverer = new MarkupPlayer(discoverer.getJSONObject("playerMarkupArgSet"));
        }

        // get description
//        JSONObject descriptiveText = portalV2.getJSONObject("descriptiveText");
        JSONObject descriptiveText = item.getJSONObject("descriptiveText").getJSONObject("map");
        mPortalTitle = descriptiveText.getString("TITLE");
        // frustratingly, this is probably present IFF the portal HAS a description
        mPortalDescription = descriptiveText.optString("DESCRIPTION");
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

    public int getLinkCapacity() {
        // FIXME hardcoded
        return 8 + getOutgoingLinksBonus();
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

    public String getPortalDescription() {
        return mPortalDescription;
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

    public MarkupPlayer getDiscoverer() {
        return mDiscoverer;
    }

    private int getDiminishingValue(String key, int index) {
        return SlimgressApplication.getInstance().getGame().getKnobs().getPortalModSharedKnobs().getDiminishingValues(key).get(index);
    }

    private int getDirectValue(String key, int index) {
        return SlimgressApplication.getInstance().getGame().getKnobs().getPortalModSharedKnobs().getDirectValues(key).get(index);
    }

    @Override
    public void updateFrom(GameEntityBase other) {
        super.updateFrom(other);
        if (other == null) {
            return;
        }
        GameEntityPortal otherPortal = (GameEntityPortal) other;

        // Update top-level fields (now that they're not final, we can reassign them)
        this.mPortalImageUrl = otherPortal.mPortalImageUrl;
        this.mPortalLocation = otherPortal.mPortalLocation;
        this.mPortalTeam = otherPortal.mPortalTeam;
        this.mPortalTitle = otherPortal.mPortalTitle;
        this.mPortalDescription = otherPortal.mPortalDescription;
        this.mPortalAddress = otherPortal.mPortalAddress;
        this.mPortalAttribution = otherPortal.mPortalAttribution;
        this.mPortalAttributionLink = otherPortal.mPortalAttributionLink;
        this.mDiscoverer = otherPortal.mDiscoverer;

        // --- Update Edges ---
        // We will match edges by their edgeGuid.
        // 1. Build a map of existing edges by edgeGuid for easy lookup.
        Map<String, LinkedEdge> currentEdgesMap = new HashMap<>();
        for (LinkedEdge edge : this.mPortalEdges) {
            currentEdgesMap.put(edge.edgeGuid, edge);
        }

        // 2. Process the new edges. Update existing ones or add new ones.
        //    Keep track of which edges from 'other' are processed.
        Map<String, LinkedEdge> newEdgesMap = new HashMap<>();
        for (LinkedEdge otherEdge : otherPortal.mPortalEdges) {
            LinkedEdge currentEdge = currentEdgesMap.get(otherEdge.edgeGuid);
            if (currentEdge == null) {
                // This is a new edge; add it
                LinkedEdge newEdge = new LinkedEdge();
                newEdge.edgeGuid = otherEdge.edgeGuid;
                newEdge.otherPortalGuid = otherEdge.otherPortalGuid;
                newEdge.isOrigin = otherEdge.isOrigin;
                newEdgesMap.put(newEdge.edgeGuid, newEdge);
            } else {
                // Update existing edge
                currentEdge.otherPortalGuid = otherEdge.otherPortalGuid;
                currentEdge.isOrigin = otherEdge.isOrigin;
                newEdgesMap.put(currentEdge.edgeGuid, currentEdge);
            }
        }

        // 3. Replace mPortalEdges with the updated collection from newEdgesMap
        this.mPortalEdges.clear();
        this.mPortalEdges.addAll(newEdgesMap.values());

        // --- Update Mods ---
        // We assume the number and order of mods match (e.g., always 4 slots).
        // For each slot:
        // - If 'other' has a null mod at this slot and we currently have one, remove it (set to null).
        // - If 'other' has a mod and we currently have one, update fields in place.
        // - If 'other' has a mod but we have null, create a new mod.
        for (int i = 0; i < otherPortal.mPortalMods.size(); i++) {
            LinkedMod otherMod = otherPortal.mPortalMods.get(i);
            LinkedMod currentMod = this.mPortalMods.get(i);

            if (otherMod == null) {
                // If other has no mod here, remove it if we have one
                if (currentMod != null) {
                    this.mPortalMods.set(i, null);
                }
            } else {
                // otherMod is present
                if (currentMod == null) {
                    // Create a new mod
                    LinkedMod newMod = new LinkedMod();
                    newMod.installingUser = otherMod.installingUser;
                    newMod.rarity = otherMod.rarity;
                    newMod.displayName = otherMod.displayName;
                    newMod.type = otherMod.type;
                    newMod.stats = new HashMap<>(otherMod.stats);
                    this.mPortalMods.set(i, newMod);
                } else {
                    // Update existing mod
                    currentMod.installingUser = otherMod.installingUser;
                    currentMod.rarity = otherMod.rarity;
                    currentMod.displayName = otherMod.displayName;
                    currentMod.type = otherMod.type;
                    // Replace current stats with new stats
                    currentMod.stats.clear();
                    currentMod.stats.putAll(otherMod.stats);
                }
            }
        }

        // If other has fewer mods than we currently do (unlikely if indexing is fixed),
        // we would remove the extras. Usually these arrays are fixed length, but if not:
        if (otherPortal.mPortalMods.size() < this.mPortalMods.size()) {
            // remove trailing mods not present in 'other'
            this.mPortalMods.subList(otherPortal.mPortalMods.size(), this.mPortalMods.size()).clear();
        }
        // If other has more mods than we do, add them (if that scenario is possible)
        while (otherPortal.mPortalMods.size() > this.mPortalMods.size()) {
            // Add any new mods
            LinkedMod otherMod = otherPortal.mPortalMods.get(this.mPortalMods.size());
            if (otherMod == null) {
                this.mPortalMods.add(null);
            } else {
                LinkedMod newMod = new LinkedMod();
                newMod.installingUser = otherMod.installingUser;
                newMod.rarity = otherMod.rarity;
                newMod.displayName = otherMod.displayName;
                newMod.type = otherMod.type;
                newMod.stats = new HashMap<>(otherMod.stats);
                this.mPortalMods.add(newMod);
            }
        }

        // --- Update Resonators ---
        // We assume resonators are also slot-based (commonly 8 slots).
        // Similar approach to mods:
        for (int i = 0; i < otherPortal.mPortalResonators.size(); i++) {
            LinkedResonator otherReso = otherPortal.mPortalResonators.get(i);
            LinkedResonator currentReso = this.mPortalResonators.get(i);

            if (otherReso == null) {
                // Remove if we have one
                if (currentReso != null) {
                    this.mPortalResonators.set(i, null);
                }
            } else {
                if (currentReso == null) {
                    // Create a new resonator
                    LinkedResonator newReso = new LinkedResonator();
                    newReso.distanceToPortal = otherReso.distanceToPortal;
                    newReso.energyTotal = otherReso.energyTotal;
                    newReso.slot = otherReso.slot;
                    newReso.id = otherReso.id;
                    newReso.ownerGuid = otherReso.ownerGuid;
                    newReso.level = otherReso.level;
                    this.mPortalResonators.set(i, newReso);
                } else {
                    // Update existing resonator
                    currentReso.distanceToPortal = otherReso.distanceToPortal;
                    currentReso.energyTotal = otherReso.energyTotal;
                    currentReso.slot = otherReso.slot;
                    currentReso.id = otherReso.id;
                    currentReso.ownerGuid = otherReso.ownerGuid;
                    currentReso.level = otherReso.level;
                }
            }
        }

        // If other has fewer resonators than we currently do (again, unlikely if indexing is fixed):
        if (otherPortal.mPortalResonators.size() < this.mPortalResonators.size()) {
            this.mPortalResonators.subList(otherPortal.mPortalResonators.size(), this.mPortalResonators.size()).clear();
        }
        // If other has more resonators:
        while (otherPortal.mPortalResonators.size() > this.mPortalResonators.size()) {
            LinkedResonator otherReso = otherPortal.mPortalResonators.get(this.mPortalResonators.size());
            if (otherReso == null) {
                this.mPortalResonators.add(null);
            } else {
                LinkedResonator newReso = new LinkedResonator();
                newReso.distanceToPortal = otherReso.distanceToPortal;
                newReso.energyTotal = otherReso.energyTotal;
                newReso.slot = otherReso.slot;
                newReso.id = otherReso.id;
                newReso.ownerGuid = otherReso.ownerGuid;
                newReso.level = otherReso.level;
                this.mPortalResonators.add(newReso);
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Portal Title: ").append(mPortalTitle).append("\n")
                .append("Description: ").append(mPortalDescription).append("\n")
                .append("Location: ").append(mPortalLocation).append("\n")
                .append("Discoverer: ").append(mDiscoverer).append("\n")
                .append("Owner: ").append(getOwnerGuid()).append("\n")
                .append("Team: ").append(mPortalTeam).append("\n")
                .append("Level: ").append(getPortalLevel()).append("\n")
                .append("Energy: ").append(getPortalEnergy()).append("/")
                .append(getPortalMaxEnergy()).append("\n")
                .append("Address: ").append(mPortalAddress).append("\n")
                .append("Image URL: ").append(mPortalImageUrl).append("\n")
                .append("Resonators:\n");

        for (int i = 0; i < mPortalResonators.size(); i++) {
            LinkedResonator reso = mPortalResonators.get(i);
            if (reso != null) {
                builder.append("  - Slot ").append(reso.slot)
                        .append(": Level ").append(reso.level)
                        .append(", Energy ").append(reso.energyTotal).append("/")
                        .append(reso.getMaxEnergy())
                        .append(", Owner: ").append(reso.ownerGuid).append("\n");
            } else {
                builder.append("  - Slot ").append(i).append(": Empty\n");
            }
        }

        builder.append("Mods:\n");
        for (int i = 0; i < mPortalMods.size(); i++) {
            LinkedMod mod = mPortalMods.get(i);
            if (mod != null) {
                builder.append("  - ").append(mod.displayName)
                        .append(" (").append(mod.rarity).append("): ")
                        .append(mod.stats).append("\n");
            } else {
                builder.append("  - Slot ").append(i).append(": Empty\n");
            }
        }

        builder.append("Links:\n");
        for (LinkedEdge edge : mPortalEdges) {
            builder.append("  - Edge GUID: ").append(edge.edgeGuid)
                    .append(", Other Portal GUID: ").append(edge.otherPortalGuid)
                    .append(", Is Origin: ").append(edge.isOrigin).append("\n");
        }

        return builder.toString();
    }

}

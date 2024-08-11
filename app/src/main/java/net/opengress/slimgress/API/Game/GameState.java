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

package net.opengress.slimgress.API.Game;

import static net.opengress.slimgress.API.Interface.Handshake.PregameStatus;
import static net.opengress.slimgress.API.Interface.Handshake.PregameStatus.ClientMustUpgrade;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Common.Team;
import net.opengress.slimgress.API.Common.Utils;
import net.opengress.slimgress.API.GameEntity.GameEntityBase;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Interface.GameBasket;
import net.opengress.slimgress.API.Interface.Handshake;
import net.opengress.slimgress.API.Interface.Interface;
import net.opengress.slimgress.API.Interface.RequestResult;
import net.opengress.slimgress.API.Item.ItemBase;
import net.opengress.slimgress.API.Item.ItemFlipCard;
import net.opengress.slimgress.API.Item.ItemMod;
import net.opengress.slimgress.API.Item.ItemPortalKey;
import net.opengress.slimgress.API.Item.ItemPowerCube;
import net.opengress.slimgress.API.Item.ItemResonator;
import net.opengress.slimgress.API.Item.ItemWeapon;
import net.opengress.slimgress.API.Knobs.KnobsBundle;
import net.opengress.slimgress.API.Player.Agent;
import net.opengress.slimgress.API.Player.PlayerEntity;
import net.opengress.slimgress.API.Plext.PlextBase;
import net.opengress.slimgress.SlimgressApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GameState
{
    private final Interface mInterface;
    private Handshake mHandshake;
    private KnobsBundle mKnobs;
    private final Inventory mInventory;
    private final World mWorld;
    private Agent mAgent;
    private final List<PlextBase> mPlexts;
    // todo: actually use this, and consider whether it needs split for globs/agent/etc
    private String mLastSyncTimestamp;
    private Location mLocation;
    private GameEntityPortal mPortal;
    private final HashMap<String, String> mAgentNames = new HashMap<>();

    public GameState()
    {
        mInterface = new Interface();
        mInventory = new Inventory();
        mWorld = new World();
        mPlexts = new LinkedList<>();
        mLastSyncTimestamp = "0";
    }

    public void clear()
    {
        mInventory.clear();
        mWorld.clear();
        mPlexts.clear();
        mLastSyncTimestamp = "0";
    }

    public void updateLocation(Location location)
    {
        mLocation = location;
    }

    public final Location getLocation()
    {
        return mLocation;
    }

    private synchronized void processGameBasket(GameBasket gameBasket)
    {
        SlimgressApplication app = SlimgressApplication.getInstance();
        if (gameBasket == null) {
            Log.w("Game", "game basket is invalid");
        } else {
            Log.d("Game", "processing game basket");
            // really we should get info back about what changed
            mInventory.processGameBasket(gameBasket);
            mWorld.processGameBasket(gameBasket);
            if (!gameBasket.getInventory().isEmpty() || !gameBasket.getDeletedEntityGuids().isEmpty()) {
                app.getInventoryViewModel().postInventory(mInventory);
            }
            if (!gameBasket.getDeletedEntityGuids().isEmpty()) {
                app.getDeletedEntityGuidsModel().postDeletedEntityGuids(gameBasket.getDeletedEntityGuids());
            }
            if (!gameBasket.getAPGains().isEmpty()) {
                // FIXME i ... don't need to add this to player data manually, do i??? yes
                app.getAPGainsModel().postAPGains(gameBasket.getAPGains());
                for (var gain : gameBasket.getAPGains()) {
                    app.getCommsViewModel().addMessage(PlextBase.createByAPGain(gain), "INFO");
                    mAgent.addAP(gain.getAmount());
                }
            }
            if (!gameBasket.getPlayerDamages().isEmpty()) {
                // FIXME i ... don't need to add this to player data manually, do i??? yes
                app.getPlayerDamagesModel().postPlayerDamages(gameBasket.getPlayerDamages());
                for (var dam : gameBasket.getPlayerDamages()) {
                    app.getCommsViewModel().addMessage(PlextBase.createByPlayerDamage(dam), "INFO");
                    mAgent.subtractEnergy(dam.getAmount());
                }
            }

            // update player data
            PlayerEntity playerEntity = gameBasket.getPlayerEntity();
            if (playerEntity != null && mAgent != null) {
                mAgent.update(playerEntity);
                app.getPlayerDataViewModel().postAgent(mAgent);
            }
        }
    }

    public synchronized void checkInterface()
    {
        // check
        if (mHandshake == null || !mHandshake.isValid())
            throw new RuntimeException("invalid handshake data");

        // get agent
        if (mAgent == null)
            mAgent = mHandshake.getAgent();
    }

    public Interface.AuthSuccess intAuthenticate(String session_name, String session_id)
    {
        return mInterface.authenticate(session_name, session_id);
    }

    public synchronized void intHandshake(final Handler handler)
    {
        mInterface.handshake(handshake -> {
            mHandshake = handshake;
            mKnobs = mHandshake.getKnobs();
            boolean handshakeValid = mHandshake.isValid();
            PregameStatus status = mHandshake.getPregameStatus();

            Message msg = new Message();
            Bundle bundle = new Bundle();
            bundle.putBoolean("Successful", handshakeValid);

            if (!handshakeValid) {
                String errString;
                if (status == ClientMustUpgrade)
                    errString = "Client must upgrade";
                else if (Objects.equals(mHandshake.getServerVersion(), ""))
                    errString = "Server returned incorrect handshake response";
                else if (mHandshake.getAgent() == null)
                    errString = "Invalid agent data";
                else
                    errString = "Unknown error";

                bundle.putString("Error", errString);
            }

            msg.setData(bundle);
            handler.sendMessage(msg);
        });
    }

    public void intGetInventory(final Handler handler)
    {
        try {
            checkInterface();

            // create params
            JSONObject params = new JSONObject();
            params.put("lastQueryTimestamp", mLastSyncTimestamp);

            // request basket
            mInterface.request(mHandshake, "playerUndecorated/getInventory", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intGetObjectsInCells(final Location location, final Handler handler)
    {
        try {
            checkInterface();

            // get cell ids for surrounding area
            String[] cellIds = Utils.getCellIdsFromLocationArea(location, 500 * 500 * Math.PI, 16, 16);

            // create cells
            JSONArray cellsAsHex = new JSONArray();
            for (String cellId : cellIds) {
                cellsAsHex.put(cellId);
            }

            // create dates (timestamps?)
            JSONArray dates = new JSONArray();
            for (int i = 0; i < cellsAsHex.length(); i++)
                dates.put(0);

            // create params
            JSONObject params = new JSONObject();
            params.put("cellsAsHex", cellsAsHex);
            params.put("dates", dates);

            // request basket
            mInterface.request(mHandshake, "gameplay/getObjectsInCells", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intLoadCommunication(final double radiusKM, final boolean factionOnly, final Handler handler)
    {
        try {
            checkInterface();

            final double earthKM = 2 * Math.PI * 6371;	// circumference

            S2LatLng center = S2LatLng.fromE6(mLocation.getLatitude(), mLocation.getLongitude());
            S2LatLng size = S2LatLng.fromRadians((Math.PI / earthKM) * radiusKM,  (2 * Math.PI / earthKM) * radiusKM);
            S2LatLngRect region = S2LatLngRect.fromCenterSize(center, size);

            // get cell ids for area
            String[] cellIds = Utils.getCellIdsFromRegion(region, 8, 12);

            // create cells
            JSONArray cellsAsHex = new JSONArray();
            for (String cellId : cellIds) cellsAsHex.put(cellId);

            // create params
            JSONObject params = new JSONObject();
            params.put("cellsAsHex", cellsAsHex);
            params.put("minTimestampMs", -1);
            params.put("maxTimestampMs", -1);
            params.put("desiredNumItems", 50);
            params.put("factionOnly", factionOnly);
            params.put("ascendingTimestampOrder", false);

            mInterface.request(mHandshake, "playerUndecorated/getPaginatedPlexts", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONArray result) {
                    try {
                        // add plexts
                        for (int i = 0; i < result.length(); i++) {
                            PlextBase newPlext = PlextBase.createByJSON(result.getJSONArray(i));
                            mPlexts.add(newPlext);
                        }
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intSendMessage(final String message, final boolean factionOnly, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("message", message);
            params.put("factionOnly", factionOnly);

            mInterface.request(mHandshake, "player/say", mLocation, params, new RequestResult(handler));
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intGetGameScore(final Handler handler)
    {
        try {
            checkInterface();

            mInterface.request(mHandshake, "playerUndecorated/getGameScore", null, null, new RequestResult(handler) {
                @Override
                public void handleResult(JSONObject result) {
                    try {
                        getData().putInt("ResistanceScore", result.getInt("resistanceScore"));
                        getData().putInt("EnlightenedScore", result.getInt("alienScore"));
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intRedeemReward(String passcode, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray passcodes = new JSONArray();
            passcodes.put(passcode);
            params.put("params", passcodes);

            mInterface.request(mHandshake, "playerUndecorated/redeemReward", null, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    switch (error) {
                        case "INVALID_PASSCODE" -> super.handleError("Passcode invalid");
                        case "ALREADY_REDEEMED" -> super.handleError("Passcode already redemmed");
                        case "ALREADY_REDEEMED_BY_PLAYER" ->
                                super.handleError("Passcode already redemmed by you");
                        default -> super.handleError("Unknown error: " + error);
                    }

                    super.handleError(error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });

        }
        catch (InterruptedException | JSONException e) {
            e.printStackTrace();
        }
    }

    public void intGetNumberOfInvites(final Handler handler)
    {
        try {
            checkInterface();

            mInterface.request(mHandshake, "playerUndecorated/getInviteInfo", null, null, new RequestResult(handler) {
                @Override
                public void handleResult(JSONObject result) {
                    try {
                        getData().putInt("NumInvites", result.getInt("numAvailableInvites"));
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intInviteUser(final String email, final String customMessage, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("customMessage", Objects.requireNonNullElse(customMessage, ""));
            params.put("inviteeEmailAddress", email);

            mInterface.request(mHandshake, "playerUndecorated/inviteViaEmail", null, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONObject result) {
                    try {
                        getData().putInt("NumInvites", result.getInt("numAvailableInvites"));
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intValidateNickname(final String nickname, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray nicknames = new JSONArray();
            nicknames.put(nickname);
            params.put("params", nicknames);

            mInterface.request(mHandshake, "playerUndecorated/validateNickname", null, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    switch (error) {
                        case "INVALID_CHARACTERS" ->
                                super.handleError("Agent name contains invalid characters.");
                        case "TOO_SHORT" -> super.handleError("Agent name is too short.");
                        case "TOO_LONG" -> super.handleError("Agent name is too long.");
                        case "BAD_WORDS" -> super.handleError("Agent name contains bad words.");
                        case "NOT_UNIQUE" -> super.handleError("Agent name is already in use.");
                        case "CANNOT_EDIT" -> super.handleError("Cannot edit agent name.");
                        default -> super.handleError("Unknown error: " + error);
                    }
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intPersistNickname(final String nickname, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray nicknames = new JSONArray();
            nicknames.put(nickname);
            params.put("params", nicknames);

            mInterface.request(mHandshake, "playerUndecorated/persistNickname", null, params, new RequestResult(handler));
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intChooseFaction(final Team team, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray factions = new JSONArray();
            factions.put(team.toString());
            params.put("params", factions);

            mInterface.request(mHandshake, "playerUndecorated/chooseFaction", null, params, new RequestResult(handler));
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intGetNicknameFromUserGUID(String guid, final Handler handler)
    {
        String[] guids = { guid };
        intGetNicknamesFromUserGUIDs(guids, handler);
    }

    public void intGetNicknamesFromUserGUIDs(final String[] guids, final Handler handler)
    {
        try {
            checkInterface();

            // create params (don't know why there are two nested arrays)
            JSONObject params = new JSONObject();

            JSONArray playerGuids = new JSONArray();
            for (String guid : guids)
                playerGuids.put(guid);

            JSONArray playerIds = new JSONArray();
            playerIds.put(playerGuids);
            params.put("params", playerIds);

            mInterface.request(mHandshake, "playerUndecorated/getNickNamesFromPlayerIds", null, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONArray result) {
                    try {
                        // retrieve nicknames for user ids
                        if (result != null && result.length() > 0) {
                            for (int i = 0; i < result.length(); i++) {
                                if (!result.isNull(i))
                                    getData().putString(guids[i], result.getString(i));
                            }
                        }
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intFireWeapon(ItemWeapon weapon, final Handler handler)
    {
        // FIXME: need to specify weapon's firepower/boost now
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", weapon.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/fireUntargetedRadialWeapon", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // PLAYER_DEPLETED, WEAPON_DOES_NOT_EXIST
                    super.handleError(error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intHackPortal(GameEntityPortal portal, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", portal.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/collectItemsFromPortal", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {

                    // this method often wants to display time deltas.
                    // it's copying an implementation where the deltas are displayed in seconds.
                    // it may be more sensible and user friendly to format as maybe HH:mm:ss
                    // there are a few approaches here worth considering:
                    // https://stackoverflow.com/questions/9214786/how-to-convert-the-seconds-in-this-format-hhmmss
                    // some may be obvious.
                    // i really like xeruf's solution: it makes "5 minutes and 20 seconds" possible

                    String pretty_error = switch (error.replaceAll("\\d", "")) {
                        case "TOO_SOON_BIG" ->
                            // issue: this only makes sense as a separate thing if it's a default.
                            // it might not be such a default
                                "Portal running hot! Unsafe to acquire items. Estimated time to cooldown: 300 seconds";
                        case "TOO_SOON_", "TOO_SOON__SECS" -> {
                            // TODO: maybe format this as "x minutes, x seconds"
                            String seconds = error.replaceAll("[^\\d]", "");
                            yield "Portal running hot! Unsafe to acquire items. Estimated time to cooldown: " + seconds + " seconds";
                        }
                        case "TOO_OFTEN" ->
                                "Portal burned out! It may take significant time for the Portal to reset";
                        case "TOO_OFTEN_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String ends = error.substring(error.lastIndexOf("_") + 1);
                            yield "Portal burned out! Estimated time for the Portal to reset: " + ends + " seconds"; // new and unimplemented
                        }
                        case "OUT_OF_RANGE" -> "Portal is out of range";
                        case "NEED_MORE_ENERGY" -> "You don't have enough XM";
                        case "SERVER_ERROR" -> "Server error";
                        case "SPEED_LOCKED" -> // new!
                                "You are moving too fast";
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            yield "You are moving too fast! You will be ready to play in " + t + "seconds"; // new!
                        }
                        case "INVENTORY_FULL" -> // new!
                                "Too many items in Inventory. Your Inventory can have no more than 2000 items";
                        default -> {
//                            pretty_error = "An unknown error occurred";
                            Log.d("GameState/Hack", error);
                            yield "Hack acquired no items";
                        }
                    };
                    super.handleError(pretty_error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    // TODO: consider, maybe updating the bundle using inventory info from this basket
                    processGameBasket(gameBasket);
                    initBundle();
                    HashMap<String, ItemBase> items = new HashMap<>();
                    for (ItemBase item: gameBasket.getInventory()) {
                        items.put(item.getEntityGuid(), item);
                        getData().putSerializable("items", items);
                    }
                }

                @Override
                public void handleResult(JSONObject result) {
                    try {
                        initBundle();
                        ArrayList<String> items = new ArrayList<>();
                        ArrayList<String> bonusItems = new ArrayList<>();
                        JSONArray guids = result.getJSONObject("items").getJSONArray("addedGuids");
                        // glyphs aren't supported yet and this may need to be moved to another method
                        JSONObject glyphResponse = result.optJSONObject("glyphResponse");
                        if (glyphResponse != null) {
                            JSONArray bonusGuids = glyphResponse.getJSONArray("bonusGuids");
                            for (int x = 0; x < bonusGuids.length(); x++) {
                                bonusItems.add(bonusGuids.getString(x));
                            }
                            getData().putStringArrayList("bonusGuids", bonusItems);
                        }
                        for (int x=0; x < guids.length(); x++) {
                            items.add(guids.getString(x));
                        }
                        getData().putStringArrayList("guids", items);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intDeployResonator(ItemResonator resonator, GameEntityPortal portal, int slot, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", portal.getEntityGuid());
            params.put("preferredSlot", slot);

            JSONArray resonators = new JSONArray();
            resonators.put(resonator.getEntityGuid());
            params.put("itemGuids", resonators);

            mInterface.request(mHandshake, "gameplay/deployResonatorV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // PORTAL_OUT_OF_RANGE, TOO_MANY_RESONATORS_FOR_LEVEL_BY_USER, PORTAL_AT_MAX_RESONATORS, ITEM_DOES_NOT_EXIST, SERVER_ERROR
                    String pretty_error = switch (error.replaceAll("\\d", "")) {
                        case "PORTAL_OUT_OF_RANGE" -> "Portal is out of range";
                        case "PLAYER_LIMIT_REACHED", "TOO_MANY_RESONATORS_FOR_LEVEL_BY_USER" ->
                            // You already have the maximum number of resonators of that level on the portal
                                "Too many resonators with same level by you";
                        case "PORTAL_AT_MAX_RESONATORS" ->
                            // Portal is already fully deployed
                                "Portal already has all resonators";
                        case "ITEM_DOES_NOT_EXIST" ->
                                "The resonator you tried to deploy is not in your inventory";
                        case "SERVER_ERROR" -> "Server error";
                        case "SPEED_LOCKED" -> // new!
                                "You are moving too fast";
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            yield "You are moving too fast! You will be ready to play in " + t + "seconds"; // new!
                        }
                        default -> "Deployment failed: unknown error: " + error;
                    };
                    // some of these error message strings might be a bit clumsy
                    super.handleError(pretty_error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    super.handleGameBasket(gameBasket);
                }

                @Override
                public void finished() {
                    Map<String, GameEntityBase> entities = getWorld().getGameEntities();
                    Set<String> keys = entities.keySet();
                    for (String key : keys) {
                        final GameEntityBase entity = entities.get(key);
                        assert entity != null;
                        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal) {
                                if (Objects.equals(entity.getEntityGuid(), portal.getEntityGuid())) {
                                    setCurrentPortal((GameEntityPortal) entity);
                                    break;
                                }
                            }
                    }
                    super.finished();
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intUpgradeResonator(ItemResonator resonator, GameEntityPortal portal, int slot, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("emitterGuid", resonator.getEntityGuid());
            params.put("portalGuid", portal.getEntityGuid());
            params.put("resonatorSlotToUpgrade", slot);

            mInterface.request(mHandshake, "gameplay/upgradeResonatorV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // PORTAL_OUT_OF_RANGE, CAN_ONLY_UPGRADE_TO_HIGHER_LEVEL, TOO_MANY_RESONATORS_FOR_LEVEL_BY_USER
                    String pretty_error = switch (error.replaceAll("\\d", "")) {
                        case "OUT_OF_RANGE", "PORTAL_OUT_OF_RANGE" -> "Portal is out of range";
                        case "PLAYER_LIMIT_REACHED", "TOO_MANY_RESONATORS_FOR_LEVEL_BY_USER" ->
                            // You already have the maximum number of resonators of that level on the portal
                                "Too many resonators with same level by you";
                        case "CAN_ONLY_UPGRADE_TO_HIGHER_LEVEL" ->
                            // Resonator is already upgraded
                                "You can't upgrade that resonator as it's already upgraded";
                        case "ITEM_DOES_NOT_EXIST" -> // new!
                                "The resonator you tried to deploy is not in your inventory";
                        case "SERVER_ERROR" -> // new!
                                "Server error";
                        case "SPEED_LOCKED" -> // new!
                                "You are moving too fast";
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            yield "You are moving too fast! You will be ready to play in " + t + "seconds"; // new!
                        }
                        default -> "Upgrade failed: unknown error: " + error;
                    };
                    // some of these error message strings might be a bit clumsy
                    super.handleError(pretty_error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    super.handleGameBasket(gameBasket);
                }

                @Override
                public void finished() {
                    Map<String, GameEntityBase> entities = getWorld().getGameEntities();
                    Set<String> keys = entities.keySet();
                    for (String key : keys) {
                        final GameEntityBase entity = entities.get(key);
                        assert entity != null;
                        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal) {
                            if (Objects.equals(entity.getEntityGuid(), portal.getEntityGuid())) {
                                setCurrentPortal((GameEntityPortal) entity);
                                break;
                            }
                        }
                    }
                    super.finished();
                }

            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intAddMod(ItemMod mod, GameEntityPortal portal, int slot, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("modResourceGuid", mod.getEntityGuid());
            params.put("modableGuid", portal.getEntityGuid());
            params.put("index", slot);

            mInterface.request(mHandshake, "gameplay/addMod", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // PORTAL_OUT_OF_RANGE, OUT_OF_RANGE, PLAYER_LIMIT_REACHED, ITEM_DOES_NOT_EXIST (there must be others)
                    super.handleError(error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intRemoveMod(GameEntityPortal portal, int slot, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("modableGuid", portal.getEntityGuid());
            params.put("index", slot);

            mInterface.request(mHandshake, "gameplay/removeMod", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // PORTAL_OUT_OF_RANGE, (there must be others)
                    super.handleError(error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intDropItem(ItemBase item, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", item.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/dropItem", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    getData().putStringArray("dropped", gameBasket.getDeletedEntityGuids().toArray(new String[0]));
                }

                @Override
                public void handleError(String error) {
                    switch (error) {
                        // based on Recycling, probably DOES_NOT_EXIST
                        // however, we return ITEM_DOES_NOT_EXIST for consistency
                        case "DOES_NOT_EXIST", "ITEM_DOES_NOT_EXIST", "RESOURCE_NOT_AVAILABLE" ->
                                super.handleError("Item is not in your inventory.");
                        case "SPEED_LOCKED" -> // new!
                                super.handleError("You are moving too fast");
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            super.handleError("You are moving too fast! You will be ready to play in " + t + "seconds"); // new!
                        }
                        default -> super.handleError("Unknown error: " + error);
                    }
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intPickupItem(String guid, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", guid);

            mInterface.request(mHandshake, "gameplay/pickUp", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // RESOURCE_NOT_AVAILABLE
                    super.handleError(error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intRecycleItem(ItemBase item, final Handler handler)
    {
        // TODO: Bulk recycling
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", item.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/recycleItem", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // DOES_NOT_EXIST
                    switch (error) {
                        case "DOES_NOT_EXIST", "ITEM_DOES_NOT_EXIST", "RESOURCE_NOT_AVAILABLE" ->
                                super.handleError("Item is not in your inventory.");
                        case "SPEED_LOCKED" -> // new!
                                super.handleError("You are moving too fast");
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            super.handleError("You are moving too fast! You will be ready to play in " + t + "seconds"); // new!
                        }
                        default -> super.handleError("Unknown error: " + error);
                    }
                    super.handleError(error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    getData().putStringArray("recycled", gameBasket.getDeletedEntityGuids().toArray(new String[0]));
                }

                @Override
                public void handleResult(String result) {
                    mAgent.addEnergy(Integer.parseInt(result));
                    getData().putString("result", result);
                    super.handleResult(result);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intUsePowerCube(ItemPowerCube powerCube, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", powerCube.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/dischargePowerCube", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }

                @Override
                public void handleResult(String result) {
                    // TODO: result contains the gained xm value, put into msg
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intRechargePortal(GameEntityPortal portal, int[] slots, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", portal.getEntityGuid());

            JSONArray resonatorSlots = new JSONArray();
            for (int slot : slots) {
                resonatorSlots.put(slot);
            }
            params.put("resonatorSlots", resonatorSlots);

            mInterface.request(mHandshake, "gameplay/rechargeResonatorsV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intRemoteRechargePortal(GameEntityPortal portal, ItemPortalKey key, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", portal.getEntityGuid());
            params.put("portalKeyGuid", key.getEntityGuid());

            JSONArray resonatorSlots = new JSONArray();
            for (int i = 0; i < 8; i++)
                resonatorSlots.put(i);
            params.put("resonatorSlots", resonatorSlots);

            mInterface.request(mHandshake, "gameplay/remoteRechargeResonatorsV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intQueryLinkablilityForPortal(GameEntityPortal portal, ItemPortalKey key, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("originPortalGuid", portal.getEntityGuid());

            JSONArray queryForPortals = new JSONArray();
            queryForPortals.put(key.getEntityGuid());
            params.put("portalLinkKeyGuidSet", queryForPortals);

            mInterface.request(mHandshake, "gameplay/getLinkabilityImpediment", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONObject result) {
                    // TODO: don't know the result yet
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intLinkPortal(GameEntityPortal portal, ItemPortalKey toKey, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("originPortalGuid", portal.getEntityGuid());
            params.put("destinationPortalGuid", toKey.getPortalGuid());
            params.put("linkKeyGuid", toKey.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/createLink", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intSetNotificationSettings(final Handler handler)
    {
        Log.w("Game", "intSetNotificationSettings not yet implemented");
    }

    public void intGetModifiedEntity(String guid, final Handler handler)
    {
        String[] guids = {guid};
        intGetModifiedEntitiesByGuid(guids, handler);
    }

    public void intGetModifiedEntitiesByGuid(String[] guids, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();

            JSONArray entityGuids = new JSONArray();
            for (String guid : guids)
                entityGuids.put(guid);
            params.put("params", entityGuids);

            // request basket
            mInterface.request(mHandshake, "gameplay/getModifiedEntitiesByGuid", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        } catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intFlipPortal(GameEntityPortal portal, ItemFlipCard flipCard, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", portal.getEntityGuid());
            params.put("resourceGuid", flipCard.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/flipPortal", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intSetPortalDetailsForCuration(final Handler handler)
    {
        Log.w("Game", "intSetPortalDetailsForCuration not yet implemented");
    }

    public void intGetUploadUrl(final Handler handler)
    {
        try {
            checkInterface();

            mInterface.request(mHandshake, "playerUndecorated/getUploadUrl", null, null, new RequestResult(handler) {
                @Override
                public void handleResult(String result) {
                    getData().putString("Url", result);
                }
            });
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void intUploadPortalPhotoByUrl(String requestId, String imageUrl, final Handler handler)
    {
        Log.w("Game", "intUploadPortalPhotoByUrl not yet implemented");
    }

    public void intUploadPortalImage(final Handler handler)
    {
        Log.w("Game", "intUploadPortalImage not yet implemented");
    }

    public void intFindNearbyPortals(int maxPortals, final Handler handler)
    {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("maxPortals", maxPortals);

            mInterface.request(mHandshake, "gameplay/findNearbyPortals", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONArray result) {
                    // TODO: UNDONE
                }
            });
        }
        catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Handshake getHandshake()
    {
        return mHandshake;
    }

    public void invalidateHandshake()
    {
        mHandshake = null;
    }

    public KnobsBundle getKnobs()
    {
        return mKnobs;
    }

    public synchronized World getWorld()
    {
        return mWorld;
    }

    public synchronized Inventory getInventory()
    {
        return mInventory;
    }

    public synchronized Agent getAgent()
    {
        checkInterface();
        return mAgent;
    }

    public synchronized List<PlextBase> getPlexts()
    {
        return mPlexts;
    }

    public void setCurrentPortal(GameEntityPortal portal) {
        mPortal = portal;
    }

    public GameEntityPortal getCurrentPortal() {
        return mPortal;
    }

    public void setSlurpableXMParticles(ArrayList<String> slurpableParticles) {
        mInterface.setSlurpableParticles(slurpableParticles);
        for (var particle : slurpableParticles) {
            mWorld.getXMParticles().remove(Long.parseLong(particle.substring(0, 16), 16));
        }
    }

    public void setAgentNames(HashMap<String, String> agentNames) {
        mAgentNames.putAll(agentNames);
    }

    public String getAgentName(String guid) {
        return mAgentNames.get(guid);
    }

    public HashMap<String, String> getAgentNames() {
        return mAgentNames;
    }

    public List<String> checkAgentNames(HashSet<String> guids) {
        List<String> rejects = new ArrayList<>();
        for (String guid : guids) {
            if (!mAgentNames.containsKey(guid)) {
                rejects.add(guid);
            }
        }
        return rejects;
    }

}

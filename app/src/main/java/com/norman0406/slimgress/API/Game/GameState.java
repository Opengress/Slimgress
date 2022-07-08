/*

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

package com.norman0406.slimgress.API.Game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2LatLngRect;
import com.norman0406.slimgress.API.Common.Location;
import com.norman0406.slimgress.API.Common.Team;
import com.norman0406.slimgress.API.Common.Utils;
import com.norman0406.slimgress.API.Plext.PlextBase;
import com.norman0406.slimgress.API.Interface.*;
import com.norman0406.slimgress.API.GameEntity.*;
import com.norman0406.slimgress.API.Item.*;
import com.norman0406.slimgress.API.Knobs.KnobsBundle;
import com.norman0406.slimgress.API.Player.*;

public class GameState
{
    private final Interface mInterface;
    private Handshake mHandshake;
    private KnobsBundle mKnobs;
    private final Inventory mInventory;
    private final World mWorld;
    private Agent mAgent;
    private final List<PlextBase> mPlexts;
    private String mLastSyncTimestamp;
    private Location mLocation;
    private GameEntityPortal mPortal;

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
        if (gameBasket == null)
            Log.w("Game", "game basket is invalid");
        else {
            Log.w("Game", "processing game basket");
            mInventory.processGameBasket(gameBasket);
            mWorld.processGameBasket(gameBasket);

            // update player data
            PlayerEntity playerEntity = gameBasket.getPlayerEntity();
            if (playerEntity != null && mAgent != null)
                mAgent.update(playerEntity);
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

            Message msg = new Message();
            Bundle bundle = new Bundle();
            bundle.putBoolean("Successful", mHandshake.isValid());

            if (!mHandshake.isValid()) {
                String errString;
                if (mHandshake.getPregameStatus() == Handshake.PregameStatus.ClientMustUpgrade)
                    errString = "Client must upgrade";
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

    public void intGetObjectsInCells(final S2LatLngRect region, final Handler handler)
    {
        try {
            checkInterface();

            // get cell ids for surrounding area
            String[] cellIds = Utils.getCellIdsFromRegion(region, 16, 16);

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
                        case "INVALID_PASSCODE":
                            super.handleError("Passcode invalid");
                            break;
                        case "ALREADY_REDEEMED":
                            super.handleError("Passcode already redemmed");
                            break;
                        case "ALREADY_REDEEMED_BY_PLAYER":
                            super.handleError("Passcode already redemmed by you");
                            break;
                        default:
                            super.handleError("Unknown error: " + error);
                            break;
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
            if (customMessage == null)
                params.put("customMessage", "");
            else
                params.put("customMessage", customMessage);
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
                        case "INVALID_CHARACTERS":
                            super.handleError("Nickname contains invalid characters");
                            break;
                        case "TOO_SHORT":
                            super.handleError("Nickname is too short");
                            break;
                        case "BAD_WORDS":
                            super.handleError("Nickname contains bad words");
                            break;
                        case "NOT_UNIQUE":
                            super.handleError("Nickname is already in use");
                            break;
                        case "CANNOT_EDIT":
                            super.handleError("Cannot edit nickname");
                            break;
                        default:
                            super.handleError("Unknown error: " + error);
                            break;
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
                    // TODO: send this information back to portal screen or wherever
                    // TOO_SOON_BIG, TOO_SOON_(x), TOO_OFTEN, OUT_OF_RANGE, NEED_MORE_ENERGY, SERVER_ERROR
                    super.handleError(error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    // TODO: consider, maybe updating the bundle using inventory info from this basket
                    processGameBasket(gameBasket);
                    initBundle();
                    Log.d("HACKING (size of inventory)", String.valueOf(gameBasket.getInventory().size()));
                    HashMap<String, ItemBase> items = new HashMap<>();
                    for (ItemBase item: gameBasket.getInventory()) {
                        Log.d("HACKING", item.getName());
                        items.put(item.getEntityGuid(), item);
                        getData().putSerializable("items", items);
                    }
                }

                @Override
                public void handleResult(JSONObject result) {
                    // TODO: send information to handler for display
                    // {"items":{"addedGuids":[]},"baseHackMultiplier":"1"}
                    // list of hacked Guids is sent as result.
                    // items pointed to by Guids are sent with gameBasket as inventory.
                    // so on other screen, look up the inventory items by GUID and display them in hack result
                    // NB you *could* show the inventory contents as-is, couldn't you?
                    Log.d("HACKING", result.toString());
                    try {
                        initBundle();
                        ArrayList<String> items = new ArrayList<>();
                        JSONArray guids = result.getJSONObject("items").getJSONArray("addedGuids");
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
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", item.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/recycleItem", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // DOES_NOT_EXIST
                    super.handleError(error);
                }

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

    public void intGetModifiedEntity(final Handler handler)
    {
        Log.w("Game", "intSetNotificationSettings not yet implemented");
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
}

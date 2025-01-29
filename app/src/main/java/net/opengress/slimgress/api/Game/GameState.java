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

package net.opengress.slimgress.api.Game;

import static net.opengress.slimgress.ViewHelpers.getString;
import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus;
import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus.ClientMustUpgrade;
import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus.ClientUpgradeRecommended;
import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus.UserMustAcceptTOS;
import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus.UserRequiresActivation;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Common.Utils;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityControlField;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Interface.Damage;
import net.opengress.slimgress.api.Interface.GameBasket;
import net.opengress.slimgress.api.Interface.Handshake;
import net.opengress.slimgress.api.Interface.Interface;
import net.opengress.slimgress.api.Interface.RequestResult;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemFlipCard;
import net.opengress.slimgress.api.Item.ItemMod;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.api.Item.ItemPowerCube;
import net.opengress.slimgress.api.Item.ItemResonator;
import net.opengress.slimgress.api.Item.ItemWeapon;
import net.opengress.slimgress.api.Knobs.KnobsBundle;
import net.opengress.slimgress.api.Player.Agent;
import net.opengress.slimgress.api.Player.PlayerEntity;
import net.opengress.slimgress.api.Plext.PlextBase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class GameState {
    private final Interface mInterface;
    private Handshake mHandshake;
    private KnobsBundle mKnobs;
    private BulkPlayerStorage mStorage;
    private final Inventory mInventory;
    private final World mWorld;
    private Agent mAgent;
    // todo: actually use this, and consider whether it needs split for globs/agent/etc
    private String mLastSyncTimestamp = "0";
    private String mInventoryTimestamp = "0";
    private final Map<String, String> mCellUpdateTimeStamps = new HashMap<>();
    private final Map<String, Long> mMinCommTimestamps = new HashMap<>();
    private final Map<String, Long> mMaxCommTimestamps = new HashMap<>();
    private Location mLocation;
    private GameEntityPortal mPortal;
    private final HashMap<String, String> mAgentNames = new HashMap<>();
    private boolean mLocationIsAccurate = false;
    private final Queue<Bundle> mHackResultsQueue = new LinkedList<>();

    public GameState() {
        mInterface = new Interface();
        mInventory = new Inventory();
        mWorld = new World();
        mLastSyncTimestamp = "0";
        mInventoryTimestamp = "0";
    }

    public void clear() {
        mInventory.clear();
        mWorld.clear();
        mLastSyncTimestamp = "0";
        mInventoryTimestamp = "0";
        mCellUpdateTimeStamps.clear();
        mAgentNames.clear();
        mMinCommTimestamps.clear();
        mMaxCommTimestamps.clear();
        SlimgressApplication app = SlimgressApplication.getInstance();
        app.getInventoryViewModel().postInventory(mInventory);
        app.getAllCommsViewModel().clearMessages();
        app.getFactionCommsViewModel().clearMessages();
    }

    public void updateLocation(Location location) {
        mLocation = location;
    }

    public final Location getLocation() {
        return mLocation;
    }

    private synchronized void processGameBasket(GameBasket gameBasket) {
        SlimgressApplication app = SlimgressApplication.getInstance();
        if (gameBasket == null) {
            Log.w("Game", "game basket is invalid");
        } else {
//            Log.d("Game", "processing game basket");
            // really we should get info back about what changed
            if (!gameBasket.getInventory().isEmpty() || !gameBasket.getDeletedEntityGuids().isEmpty()) {
                app.getInventoryViewModel().postInventory(mInventory);
            }
            mInventory.processGameBasket(gameBasket);
            mWorld.processGameBasket(gameBasket);
            if (!gameBasket.getAPGains().isEmpty()) {
                for (var gain : gameBasket.getAPGains()) {
                    app.getAllCommsViewModel().addMessage(PlextBase.createByAPGain(gain));
                    mAgent.addAP(gain.getAmount());
                }
            }
            if (!gameBasket.getPlayerDamages().isEmpty()) {
                for (var dam : gameBasket.getPlayerDamages()) {
                    app.getAllCommsViewModel().addMessage(PlextBase.createByPlayerDamage(dam));
                    app.getMainActivity().damagePlayer(dam.getAmount(), dam.getAttackerGuid());
                    mAgent.subtractEnergy(dam.getAmount());
                }
            }

            // update player data
            PlayerEntity playerEntity = gameBasket.getPlayerEntity();
            if (playerEntity != null && mAgent != null) {
                mAgent.update(playerEntity);
            }
        }
    }

    public synchronized void checkInterface() {
        // check
        if (mHandshake == null || !mHandshake.isValid()) {
            throw new RuntimeException("invalid handshake data");
        }

        // get agent
        if (mAgent == null) {
            mAgent = mHandshake.getAgent();
        }
    }

    public Interface.AuthSuccess intAuthenticate(String session_name, String session_id) {
        return mInterface.authenticate(session_name, session_id);
    }

    public synchronized void intHandshake(final Handler handler, Map<String, String> params) {
        mInterface.handshake(handshake -> {
            mHandshake = handshake;
            mKnobs = mHandshake.getKnobs();
            mStorage = mHandshake.getBulkPlayerStorage();
            boolean handshakeValid = mHandshake.isValid();
            PregameStatus status = mHandshake.getPregameStatus();

            Message msg = new Message();
            Bundle bundle = new Bundle();
            bundle.putBoolean("Successful", handshakeValid);

            if (!handshakeValid) {
                String errString;
                if (status == ClientMustUpgrade) {
                    errString = "Client must upgrade";
                } else if (status == ClientUpgradeRecommended) {
                    errString = "Client software is out of date";
                } else if (status == UserRequiresActivation) {
                    errString = "Opengress requires an invitation to play";
                } else if (status == UserMustAcceptTOS) {
                    errString = "User must accept Terms of Service";
                    bundle.putBoolean("MaySendPromoEmail", mHandshake.getAgent().getNotificationSettings().maySendPromoEmail());
                } else if (Objects.equals(mHandshake.getErrorFromServer(), "NOT_LOGGED_IN")) {
                    errString = "Expired user session";
                } else if (mHandshake.getErrorFromServer() != null) {
                    errString = mHandshake.getErrorFromServer(); // a bit wonky for now
                } else if (Objects.equals(mHandshake.getServerVersion(), "")) {
                    errString = "Server returned incorrect handshake response";
                } else if (mHandshake.getAgent() == null) {
                    errString = "Invalid agent data";
                } else {
                    errString = "Unknown error";
                }

                bundle.putString("Error", errString);
            } else {
                putAgentName(mHandshake.getAgent().getEntityGuid(), mHandshake.getNickname());
            }

            msg.setData(bundle);
            handler.sendMessage(msg);
        }, params);
    }

    public void intGetInventory(final Handler handler) {
        try {
            checkInterface();

            // create params
            JSONObject params = new JSONObject();
            params.put("lastQueryTimestamp", mInventoryTimestamp);

            // request basket
            mInterface.request(mHandshake, "playerUndecorated/getInventory", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }

                @Override
                public void handleResult(String result) {
                    mInventoryTimestamp = result;
                    super.handleResult(result);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intGetObjectsInCells(final Location location, final Handler handler) {
        try {
            checkInterface();

            // get cell ids for surrounding area - i think the radius and such may end up as parameter(s)
            String[] cellIds = Utils.getCellIdsFromLocationRadiusKm(location, 1.5, 16, 16);

            // create cells
            JSONArray cellsAsHex = new JSONArray();
            for (String cellId : cellIds) {
                cellsAsHex.put(cellId);
            }

            // create dates (timestamps?)
            JSONArray dates = new JSONArray();
            for (String cellId : cellIds) {
                String lastQueried = "0";
                if (mCellUpdateTimeStamps.containsKey(cellId)) {
                    lastQueried = mCellUpdateTimeStamps.get(cellId);
                    mCellUpdateTimeStamps.get(cellId);
                    if (lastQueried == null || lastQueried.isEmpty()) {
                        lastQueried = "0";
                    }
                }
                dates.put(Long.parseLong(lastQueried));
            }

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

                @Override
                public void handleResult(String result) {
                    for (String cellId : cellIds) {
                        mCellUpdateTimeStamps.put(cellId, result);
                    }
                    // not ready for this yet
//                    mInventoryTimeStamp = result;
                    super.handleResult(result);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public synchronized void intLoadCommunication(boolean getOlderMessages, final double radiusKM, final boolean factionOnly, final Handler handler) {
        try {
            checkInterface();

            String[] cellIds = Utils.getCellIdsFromLocationRadiusKm(mLocation, radiusKM, 8, 12);

            // create cells
            JSONArray cellsAsHex = new JSONArray();
            for (String cellId : cellIds) {
                cellsAsHex.put(cellId);
            }

            String categoryName = factionOnly ? "faction" : "all";
            long minTimeStampMs = -1;
            long maxTimeStampMs = -1;
            if (!getOlderMessages && mMinCommTimestamps.containsKey(categoryName)) {
                minTimeStampMs = mMinCommTimestamps.get(categoryName);
            }
            // probably harmless to always send this regardless
            if (mMaxCommTimestamps.containsKey(categoryName)) {
                maxTimeStampMs = mMaxCommTimestamps.get(categoryName);
            }

            // create params
            JSONObject params = new JSONObject();
            params.put("cellsAsHex", cellsAsHex);
            params.put("minTimestampMs", minTimeStampMs);
            params.put("maxTimestampMs", maxTimeStampMs);
            params.put("desiredNumItems", 50);
            params.put("factionOnly", factionOnly);
            params.put("ascendingTimestampOrder", false);
            params.put("categories", factionOnly ? 2 : 1);

            long finalMinTimeStampMs = minTimeStampMs;
            long finalMaxTimeStampMs = maxTimeStampMs;
            mInterface.request(mHandshake, "playerUndecorated/getPaginatedPlexts", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONArray result) {
                    try {
                        if (result.length() > 0) {
                            ArrayList<PlextBase> plexts = new ArrayList<>(result.length());
                            for (int i = 0; i < result.length(); i++) {
                                PlextBase newPlext = PlextBase.createByJSON(result.getJSONArray(i));
                                assert newPlext != null;
                                plexts.add(newPlext);
                                long time = Long.parseLong(newPlext.getEntityTimestamp());
                                // FIXME i think that what i should actually do is
                                //  override the mix/max times with -1
                                //  (which is our molly value in the server)
                                //  if the user wants earlier/later comms
                                if (finalMinTimeStampMs == -1 || finalMinTimeStampMs > time) {
                                    if (getOlderMessages) {
                                        mMaxCommTimestamps.put(categoryName, time);
                                    }
                                }
                                if (finalMaxTimeStampMs == -1 || finalMaxTimeStampMs < time) {
                                    if (!getOlderMessages) {
                                        mMinCommTimestamps.put(categoryName, time + 1);
                                    }
                                }
                            }

                            if (factionOnly) {
                                SlimgressApplication.getInstance().getFactionCommsViewModel().addMessages(plexts);
                            } else {
                                SlimgressApplication.getInstance().getAllCommsViewModel().addMessages(plexts);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intSendMessage(final String message, final boolean factionOnly, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("message", message);
            params.put("factionOnly", factionOnly);

            mInterface.request(mHandshake, "player/say", mLocation, params, new RequestResult(handler) {

                @Override
                public void handleError(String error) {
                    String pretty_error = switch (error) {
                        case "MESSAGE_REJECTED" -> "You are not allowed to talk!";
                        case "TOO_LONG" -> // new!
                                "Message can't be longer than 512 characters.";
                        default -> getString(R.string.server_error);
                    };
                    super.handleError(pretty_error);
                    super.handleError(error);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intGetGameScore(final Handler handler) {
        checkInterface();

        mInterface.request(mHandshake, "playerUndecorated/getGameScore", null, null, new RequestResult(handler) {
            @Override
            public void handleResult(JSONObject result) {
                try {
                    getData().putInt("ResistanceScore", result.getInt("resistanceScore"));
                    getData().putInt("EnlightenedScore", result.getInt("alienScore"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public synchronized void intLevelUp(int level, final Handler handler) {
        try {
            checkInterface();

            // create params
            JSONObject params = new JSONObject();
            params.put("newLevelUpMsgId", level);

            // request basket
            mInterface.request(mHandshake, "gameplay/levelUp", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    // TODO handle all this
                    processGameBasket(gameBasket);
                    // maybe something like this:
                    initBundle();
                    HashMap<String, ItemBase> items = new HashMap<>();
                    for (ItemBase item : gameBasket.getInventory()) {
                        items.put(item.getEntityGuid(), item);
                    }
                    getData().putSerializable("items", items);
                    // and then...?
                    var player = gameBasket.getPlayerEntity();
                    Log.d("GAME", "Got level: " + player.getVerifiedLevel());
                }

                @Override
                public void handleError(String error) {
                    String pretty_error;
                    if (error.contains("DENIED")) {
                        pretty_error = "LevelUp request was denied!";
                    } else if (error.contains("INSUFFICIENT_AP")) {
                        pretty_error = "Not enough AP to level up that high!";
                    } else {
                        Log.e("GameState/LevelUp", error);
                        pretty_error = error;
                    }
                    super.handleError(pretty_error);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void intRedeemReward(String passcode, final Handler handler) {
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
                        case "ALREADY_REDEEMED" -> super.handleError("Passcode fully redeemed");
                        case "ALREADY_REDEEMED_BY_PLAYER" ->
                                super.handleError("Passcode already redeemed by you");
                        case "INVENTORY_FULL" ->
                                super.handleError("Too many items in Inventory. Your Inventory can have no more than 2000 items");
                        default -> super.handleError("Unknown error: " + error);
                    }
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    super.handleGameBasket(gameBasket);
                }

                @Override
                public void handleResult(JSONObject res) {

                    try {
                        super.handleResult(res);
                        ArrayList<ItemBase> items = new ArrayList<>();
                        ArrayList<String> extras = new ArrayList<>();

                        JSONArray inventory = res.getJSONArray("inventoryAward");
                        for (int i = 0; i < inventory.length(); i++) {
                            JSONArray resource = inventory.getJSONArray(i);
                            ItemBase newItem = ItemBase.createByJSON(resource);
                            if (newItem != null) {
                                items.add(newItem);
                            }
                        }
                        getData().putSerializable("inventoryAward", items);

                        JSONArray extraJson = res.getJSONArray("additionalAwards");
                        for (int i = 0; i < extraJson.length(); i++) {
                            String thing = extraJson.getString(i);
                            if (thing != null) {
                                extras.add(thing);
                            }
                        }
                        getData().putSerializable("additionalAwards", extras);

                        getData().putLong("apAward", res.optLong("apAward"));
                        getData().putLong("xmAward", res.optLong("xmAward"));

                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intGetNumberOfInvites(final Handler handler) {
        checkInterface();

        mInterface.request(mHandshake, "playerUndecorated/getInviteInfo", null, null, new RequestResult(handler) {
            @Override
            public void handleResult(JSONObject result) {
                try {
                    getData().putInt("NumInvites", result.getInt("numAvailableInvites"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public void intInviteUser(final String email, final String customMessage, final Handler handler) {
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
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intValidateNickname(final String nickname, final Handler handler) {
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
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intPersistNickname(final String nickname, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray nicknames = new JSONArray();
            nicknames.put(nickname);
            params.put("params", nicknames);

            mInterface.request(mHandshake, "playerUndecorated/persistNickname", null, params, new RequestResult(handler));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // new
    public void intGetLatestSlimgressVersion(final Handler handler) {
        checkInterface();
        mInterface.request(mHandshake, "playerUndecorated/getLatestSlimgressVersion", null, null, new RequestResult(handler) {
            @Override
            public void handleResult(String result) {
                getData().putString("version", result);
            }
        });
    }

    public void intChooseFaction(final Team team, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray factions = new JSONArray();
            factions.put(team.getID());
            params.put("params", factions);

            mInterface.request(mHandshake, "playerUndecorated/chooseFaction", null, params, new RequestResult(handler));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intGetNicknameFromUserGUID(String guid, final Handler handler) {
        String[] guids = {guid};
        intGetNicknamesFromUserGUIDs(guids, handler);
    }

    public void intGetNicknamesFromUserGUIDs(@NonNull final String[] guids, final Handler handler) {
        try {
            checkInterface();

            // create params (don't know why there are two nested arrays)
            JSONObject params = new JSONObject();

            JSONArray playerGuids = new JSONArray();
            for (String guid : guids) {
                playerGuids.put(guid);
            }

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
                                if (!result.isNull(i)) {
                                    getData().putString(guids[i], result.getString(i));
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intFireWeapon(ItemWeapon weapon, final Handler handler) {
        intFireWeapon(weapon, 0, handler);
    }

    public void intFireWeapon(@NonNull ItemWeapon weapon, int encodedBoost, final Handler handler) {
        // FIXME: this is inappropriate because we are not encoding the boost param
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", weapon.getEntityGuid());
            params.put("encodedBoost", encodedBoost);

            mInterface.request(mHandshake, "gameplay/fireUntargetedRadialWeaponV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // looks like norman was copying these from ios client - nemesis had way more
                    // PLAYER_DEPLETED, WEAPON_DOES_NOT_EXIST, SPEED_LOCKED
                    switch (error) {
                        case "WEAPON_DOES_NOT_EXIST", "WEAPON_DOES_NOT_HAVE_OWNER",
                             "WRONG_OWNER_FOR_WEAPON" ->
                                super.handleError(getString(R.string.weapon_does_not_exist));
                        case "WRONG_WEAPON_TYPE" ->
                                super.handleError("You can only fire an XMP or UltraStrike!");
                        case "WRONG_LEVEL" ->
                                super.handleError("You can't fire a weapon above your access level!");
                        case "SPEED_LOCKED" -> // new!
                                super.handleError(getString(R.string.you_are_moving_too_fast));
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                super.handleError(getString(R.string.you_don_t_have_enough_xm));
                        case "SERVER_ERROR" -> super.handleError(getString(R.string.server_error));
                        default -> super.handleError("Unknown error: " + error);
                    }
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }

                @Override
                public void handleResult(JSONObject result) {
                    super.handleResult(result);
                    try {
                        initBundle();
                        JSONArray damages = result.getJSONArray("damages");
                        ArrayList<Damage> damageList = new ArrayList<>();
                        for (int x = 0; x < damages.length(); x++) {
                            JSONObject damage = damages.getJSONObject(x);
                            Damage damageObj = new Damage(
                                    damage.getString("responsibleGuid"),
                                    damage.getString("targetGuid"),
                                    damage.getInt("targetSlot"),
                                    damage.getString("resonatorId"),
                                    damage.getBoolean("criticalHit"),
                                    damage.getInt("damageAmount"),
                                    damage.getBoolean("targetDestroyed")
                            );
                            damageList.add(damageObj);
                        }
                        getData().putParcelableArrayList("damages", damageList);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intHackPortal(@NonNull GameEntityPortal portal, final Handler handler) {
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
                            String seconds = error.replaceAll("\\D", "");
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
                        case "SERVER_ERROR" -> getString(R.string.server_error);
                        case "SPEED_LOCKED" -> // new!
                                getString(R.string.you_are_moving_too_fast);
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            yield "You are moving too fast! You will be ready to play in " + t + "seconds"; // new!
                        }
                        case "INVENTORY_FULL" -> // new!
                                "Too many items in Inventory. Your Inventory can have no more than 2000 items";
                        default -> //                            pretty_error = "An unknown error occurred";
                                "Hack acquired no items";
                    };
                    super.handleError(pretty_error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    initBundle();
                    HashMap<String, ItemBase> items = new HashMap<>();
                    for (ItemBase item : gameBasket.getInventory()) {
                        items.put(item.getEntityGuid(), item);
                    }
                    getData().putSerializable("items", items);
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
                        for (int x = 0; x < guids.length(); x++) {
                            items.add(guids.getString(x));
                        }
                        getData().putStringArrayList("guids", items);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // FIXME instead of one resonator this should ultimately accept multiple and then send a set of Guids
    public void intDeployResonator(@NonNull List<ItemResonator> wantedResonators, @NonNull GameEntityPortal portal, int slot, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", portal.getEntityGuid());
            params.put("preferredSlot", slot);
            int level = wantedResonators.get(0).getItemLevel();

            JSONArray resonators = new JSONArray();
            for (ItemResonator resonator : wantedResonators) {
                resonators.put(resonator.getEntityGuid());
            }
            params.put("itemGuids", resonators);

            mInterface.request(mHandshake, "gameplay/deployResonatorV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // PORTAL_OUT_OF_RANGE, TOO_MANY_RESONATORS_FOR_LEVEL_BY_USER,
                    // PORTAL_AT_MAX_RESONATORS, ITEM_DOES_NOT_EXIST, SERVER_ERROR
                    String pretty_error = switch (error.replaceAll("\\d", "")) {
                        case "PORTAL_OUT_OF_RANGE" -> "Portal is out of range";
                        case "PLAYER_LIMIT_REACHED", "TOO_MANY_RESONATORS_FOR_LEVEL_BY_USER" ->
                            // You already deployed the maximum number of resonators of that level
                                "Too many resonators with same level by you";
                        case "PORTAL_AT_MAX_RESONATORS" ->
                            // Portal is already fully deployed
                                "Portal already has all resonators";
                        case "WRONG_LEVEL" ->
                                "You can't deploy a resonator above your access level";
                        case "ITEM_DOES_NOT_EXIST", "RESONATOR_DOES_NOT_EXIST" ->
                                "The resonator you tried to deploy is not in your inventory";
                        case "SERVER_ERROR" -> "Server error";
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                getString(R.string.you_don_t_have_enough_xm);
                        case "PORTAL_BELONGS_TO_ENEMY" -> "That portal belongs to the wrong team!";
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
                    getAgent().subtractEnergy(getKnobs().getXMCostKnobs().getResonatorDeployCostByLevel().get(level - 1));
                }

            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intUpgradeResonator(@NonNull ItemResonator resonator, @NonNull GameEntityPortal portal, int slot, final Handler handler) {
        try {
            checkInterface();

            int level = resonator.getItemLevel();

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
                            // You already deployed the maximum number of resonators of that level
                                "Too many resonators with same level by you";
                        case "CAN_ONLY_UPGRADE_TO_HIGHER_LEVEL" ->
                            // Resonator is already upgraded
                                "You can't upgrade that resonator as it's already upgraded";
                        case "WRONG_LEVEL" ->
                                "You can't deploy a resonator above your access level";
                        case "ITEM_DOES_NOT_EXIST" -> // new!
                                "The resonator you tried to deploy is not in your inventory";
                        case "SERVER_ERROR" -> // new!
                                "Server error";
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                getString(R.string.you_don_t_have_enough_xm);
                        case "PORTAL_BELONGS_TO_ENEMY" -> "That portal belongs to the wrong team!";
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
                    getAgent().subtractEnergy(getKnobs().getXMCostKnobs().getResonatorUpgradeCostByLevel().get(level - 1));
                }

            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intAddMod(@NonNull ItemMod mod, @NonNull GameEntityPortal portal, int slot, final Handler handler) {
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
                    String pretty_error = switch (error.replaceAll("\\d", "")) {
                        case "OUT_OF_RANGE", "PORTAL_OUT_OF_RANGE" -> "Portal is out of range";
                        case "PLAYER_LIMIT_REACHED" ->
                            // You already deployed the maximum number of resonators of that level
                                "You already have the maximum number of mods installed on this portal";
                        case "NO_EMPTY_SLOTS" ->
                                "This portal already has the maximum number of mods";
                        case "MOD_DOES_NOT_EXIST" ->
                                "The mod you tried to deploy is not in your inventory";
                        case "SERVER_ERROR" -> // new!
                                "Server error";
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                getString(R.string.you_don_t_have_enough_xm);
                        case "NOT_A_TEAMMATE" -> "That portal belongs to the wrong team!";
                        case "SPEED_LOCKED" -> // new!
                                "You are moving too fast";
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            yield "You are moving too fast! You will be ready to play in " + t + "seconds"; // new!
                        }
                        default -> "Adding mod failed: unknown error: " + error;
                    };
                    // some of these error message strings might be a bit clumsy
                    super.handleError(pretty_error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    super.handleGameBasket(gameBasket);
                    getData().putSerializable("mod", mod);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intRemoveMod(@NonNull GameEntityPortal portal, int slot, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("modableGuid", portal.getEntityGuid());
            params.put("index", slot);

            mInterface.request(mHandshake, "gameplay/removeMod", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // PORTAL_OUT_OF_RANGE, MODABLE_DOES_NOT_EXIST, MOD_DOES_NOT_EXIST,
                    // NOT_A_TEAMMATE, MOD_SLOT_EMPTY, SERVER_ERROR, INDEX_OUT_OF_BOUNDS??
                    String pretty_error = switch (error.replaceAll("\\d", "")) {
                        case "OUT_OF_RANGE", "PORTAL_OUT_OF_RANGE" -> "Portal is out of range";
                        case "NO_EMPTY_SLOTS" ->
                                "This portal already has the maximum number of mods";
                        case "MOD_SLOT_EMPTY" -> "That mod slot is empty";
                        case "MOD_DOES_NOT_EXIST" ->
                                "The mod you tried to remove is not on the portal";
                        case "SERVER_ERROR" -> // new!
                                "Server error";
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                getString(R.string.you_don_t_have_enough_xm);
                        case "NOT_A_TEAMMATE" -> "That portal belongs to the wrong team!";
                        case "SPEED_LOCKED" -> // new!
                                "You are moving too fast";
                        case "SPEED_LOCKED_" -> {
                            // TODO: maybe format this as "x hours, x minutes, x seconds"
                            String t = error.substring(error.lastIndexOf("_") + 1);
                            yield "You are moving too fast! You will be ready to play in " + t + "seconds"; // new!
                        }
                        default -> "Removing mod failed: unknown error: " + error;
                    };
                    super.handleError(pretty_error);
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    super.handleGameBasket(gameBasket);
                }

            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intDropItem(@NonNull ItemBase item, final Handler handler) {
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
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intPickupItem(String guid, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", guid);

            mInterface.request(mHandshake, "gameplay/pickUp", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // RESOURCE_NOT_AVAILABLE, OUT_OF_RANGE, INVENTORY_FULL
                    switch (error) {
                        case "RESOURCE_NOT_AVAILABLE" -> {
                            super.handleError("Pickup failed: Item not available to pick up");
                            mWorld.deleteEntityByGuid(guid);
                            SlimgressApplication.getInstance().getDeletedEntityGuidsViewModel().addGuids(List.of(new String[]{guid}));
                        }
                        case "OUT_OF_RANGE" -> super.handleError("Pickup failed: Out of range");
                        case "INVENTORY_FULL" ->
                                super.handleError("Too many items in Inventory. Your Inventory can have no more than 2000 items");
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

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    ItemBase item = gameBasket.getInventory().get(0);
                    getData().putString("description", item.getUsefulName());
                }

            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intRecycleItem(@NonNull ItemBase item, final Handler handler) {
        // now unused, but probably still connected in backend
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
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intRecycleItems(@NonNull List<ItemBase> items, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray itemGuids = new JSONArray();
            for (var item : items) {
                itemGuids.put(item.getEntityGuid());
            }
            params.put("itemGuids", itemGuids);

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
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intUsePowerCube(@NonNull ItemPowerCube powerCube, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("itemGuid", powerCube.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/dischargePowerCube", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    // DOES_NOT_EXIST
                    switch (error) {
                        case "NOT_POWER_CUBE" -> super.handleError("Invalid power cube.");
                        case "UNABLE_TO_DISCHARGE" ->
                                super.handleError("Unable to discharge Power Cube.");
                        case "DOES_NOT_EXIST", "ITEM_DOES_NOT_EXIST", "RESOURCE_NOT_AVAILABLE",
                             "NOT_IN_PLAYER_INVENTORY" ->
                                super.handleError("Item is not in your inventory.");
                        case "WRONG_LEVEL" ->
                                super.handleError("You can't discharge a power cube above your access level!");
                        case "ENERGY_FULL" ->
                                super.handleError("You can't discharge a power cube into an already full XM tank");
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
                    getData().putStringArray("consumed", gameBasket.getDeletedEntityGuids().toArray(new String[0]));
                }

                @Override
                public void handleResult(String result) {
                    mAgent.addEnergy(Integer.parseInt(result));
                    getData().putString("result", result);
                    super.handleResult(result);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intRechargePortal(@NonNull GameEntityPortal portal, @NonNull int[] slots, boolean isBoostRecharge, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", portal.getEntityGuid());
            params.put("portalKeyGuid", null);
            params.put("isBoostRecharge", isBoostRecharge);

            JSONArray resonatorSlots = new JSONArray();
            for (int slot : slots) {
                resonatorSlots.put(slot);
            }
            params.put("resonatorSlots", resonatorSlots);

            mInterface.request(mHandshake, "gameplay/rechargeResonatorsV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    getData().putInt("recharged", slots.length);
                }

                @Override
                public void handleError(String error) {
                    // TOO_BUSY_RECHARGE
                    // MISSING_PORTAL_KEY
                    switch (error) {
                        case "PORTAL_OUT_OF_RANGE" -> super.handleError("Out of range.");
                        case "PORTAL_BELONGS_TO_ENEMY" -> super.handleError("Enemy Portal.");
                        case "RESONATORS_FULLY_CHARGED" -> super.handleError("Fully charged.");
                        case "NO_RESONATORS_TO_RECHARGE" -> super.handleError("Not rechargeable.");
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                super.handleError(getString(R.string.you_don_t_have_enough_xm));
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
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intRemoteRechargePortal(@NonNull ItemPortalKey key, int[] slots, boolean isBoostRecharge, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", null);
            params.put("portalKeyGuid", key.getEntityGuid());
            params.put("isBoostRecharge", isBoostRecharge);

//            JSONArray resonatorSlots = new JSONArray();
//            for (int i = 0; i < 8; i++) {
//                resonatorSlots.put(i);
//            }
            JSONArray resonatorSlots = new JSONArray();
            for (int slot : slots) {
                resonatorSlots.put(slot);
            }
            params.put("resonatorSlots", resonatorSlots);

            mInterface.request(mHandshake, "gameplay/remoteRechargeResonatorsV2", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                    getData().putInt("recharged", slots.length);
                }

                @Override
                public void handleError(String error) {
                    // TOO_BUSY_RECHARGE
                    switch (error) {
                        case "MISSING_PORTAL_KEY" -> super.handleError("Missing Portal Key.");
                        case "PORTAL_OUT_OF_RANGE" -> super.handleError("Out of range.");
                        case "PORTAL_BELONGS_TO_ENEMY" -> super.handleError("Enemy Portal.");
                        case "RESONATORS_FULLY_CHARGED" -> super.handleError("Fully charged.");
                        case "NO_RESONATORS_TO_RECHARGE" -> super.handleError("Not rechargeable.");
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                super.handleError(getString(R.string.you_don_t_have_enough_xm));
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
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intQueryLinkablilityForPortal(GameEntityPortal portal, ItemPortalKey key, final Handler handler) {
        // I do not know why you would just check ONE key, but EVERY unofficial client did ¯\_(ツ)_/¯
        intQueryLinkablilityForPortal(portal, new AbstractList<>() {
            @Override
            public ItemPortalKey get(int index) {
                return key;
            }

            @Override
            public int size() {
                return 1;
            }
        }, handler);
    }

    public void intQueryLinkablilityForPortal(@NonNull GameEntityPortal portal, @NonNull List<ItemPortalKey> keys, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("originPortalGuid", portal.getEntityGuid());
            JSONArray keyGuids = new JSONArray();
            // maybe filter out wrong portals at this stage to save server some stress?
            for (ItemPortalKey key : keys) {
                keyGuids.put(key.getEntityGuid());
            }
            params.put("portalLinkKeyGuidSet", keyGuids);

            mInterface.request(mHandshake, "gameplay/getLinkabilityImpediment", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONObject result) {
                    Set<ItemPortalKey> toRemove = new HashSet<>();
                    for (ItemPortalKey key : keys) {
                        if (result.has(key.getEntityGuid())) {
                            toRemove.add(key);
                        }
                    }
                    keys.removeAll(toRemove);

                    getData().putSerializable("result", (Serializable) keys);
                    super.handleResult(result);
                }

                @Override
                public void handleError(String error) {
                    switch (error) {
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                super.handleError(getString(R.string.you_don_t_have_enough_xm));
                        case "ITEM_DOES_NOT_EXIST" ->
                                super.handleError("The portal key wasn't found in your inventory.");
                        case "CLIENT_UNABLE_TO_USE_ITEM" ->
                                super.handleError("Can't use that item to create a link.");
                        case "REQUIRES_LINK_KEY" ->
                                super.handleError("Linking requires a key to the destination portal.");
                        case "ORIGIN_IS_DESTINATION" ->
                                super.handleError("Can't link a portal to itself.");
                        case "DESTINATION_MUST_BE_FULL" ->
                                super.handleError("Destination portal isn't fully deployed.");
                        case "DESTINATION_UNOWNED" ->
                                super.handleError("Destination portal is neutral.");
                        case "DESTINATION_WRONG_TEAM" ->
                                super.handleError("Destination portal isn't aligned with your team.");
                        case "BEYOND_ORIGIN_RANGE" ->
                                super.handleError("Destination portal is too far from origin portal.");
                        case "EDGE_ALREADY_EXISTS" ->
                                super.handleError("That link already exists.");
                        case "CROSSES_EXISTING_LINK" ->
                                super.handleError("That link would cross an existing link.");
                        case "ORIGIN_NOT_FOUND" -> super.handleError("Origin portal not found.");
                        case "ORIGIN_UNOWNED" -> super.handleError("Origin portal is neutral.");
                        case "ORIGIN_WRONG_TEAM" ->
                                super.handleError("Origin portal is aligned with the wrong faction.");
                        case "ORIGIN_MUST_BE_FULL" ->
                                super.handleError("Origin portal must be completely deployed.");
                        case "ORIGIN_LINK_CAPACITY_REACHED" ->
                                super.handleError("Too many outgoing links.");
                        case "ORIGIN_PORTAL_NOT_IN_RANGE_OF_PLAYER" ->
                                super.handleError("Get closer.");
                        case "SPEED_LOCKED" -> super.handleError("You are moving too fast!");
                        case "CONTAINED_WITHIN_CAPTURED_REGION" ->
                                super.handleError("Can't link from within a field.");
                        case "SERVER_ERROR" -> super.handleError(getString(R.string.server_error));
                        default -> super.handleError("Unknown error: " + error);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intLinkPortal(@NonNull GameEntityPortal portal, @NonNull ItemPortalKey toKey, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("originPortalGuid", portal.getEntityGuid());
            params.put("destinationPortalGuid", toKey.getPortalGuid());
            params.put("linkKeyGuid", toKey.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/createLink", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    List<GameEntityBase> entities = gameBasket.getGameEntities();
                    int numFields = 0;
                    int totalMu = 0;
                    for (GameEntityBase entity : entities) {
                        if (entity.getGameEntityType() == GameEntityBase.GameEntityType.ControlField) {
                            if (Objects.equals(entity.getOwnerGuid(), mAgent.getEntityGuid())) {
                                ++numFields;
                                int thisMu = ((GameEntityControlField) entity).getFieldScore();
                                totalMu += thisMu;
                                SlimgressApplication.postPlainCommsMessage(String.format("Field established! +%d mind units", thisMu));
                            }
                        }
                    }
                    getData().putInt("numFields", numFields);
                    getData().putInt("mu", totalMu);
                    processGameBasket(gameBasket);
                }

                @Override
                public void handleError(String error) {
                    switch (error) {
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                super.handleError(getString(R.string.you_don_t_have_enough_xm));
                        case "ITEM_DOES_NOT_EXIST" ->
                                super.handleError("The portal key wasn't found in your inventory.");
                        case "CLIENT_UNABLE_TO_USE_ITEM" ->
                                super.handleError("Can't use that item to create a link.");
                        case "REQUIRES_LINK_KEY" ->
                                super.handleError("Linking requires a key to the destination portal.");
                        case "ORIGIN_IS_DESTINATION" ->
                                super.handleError("Can't link a portal to itself.");
                        case "DESTINATION_MUST_BE_FULL" ->
                                super.handleError("Destination portal isn't fully deployed.");
                        case "DESTINATION_UNOWNED" ->
                                super.handleError("Destination portal is neutral.");
                        case "DESTINATION_WRONG_TEAM" ->
                                super.handleError("Destination portal isn't aligned with your team.");
                        case "BEYOND_ORIGIN_RANGE" ->
                                super.handleError("Destination portal is too far from origin portal.");
                        case "EDGE_ALREADY_EXISTS" ->
                                super.handleError("That link already exists.");
                        case "CROSSES_EXISTING_LINK" ->
                                super.handleError("That link would cross an existing link.");
                        case "ORIGIN_NOT_FOUND" -> super.handleError("Origin portal not found.");
                        case "ORIGIN_UNOWNED" -> super.handleError("Origin portal is neutral.");
                        case "ORIGIN_WRONG_TEAM" ->
                                super.handleError("Origin portal is aligned with the wrong faction.");
                        case "ORIGIN_MUST_BE_FULL" ->
                                super.handleError("Origin portal must be completely deployed.");
                        case "ORIGIN_LINK_CAPACITY_REACHED" ->
                                super.handleError("Too many outgoing links.");
                        case "ORIGIN_PORTAL_NOT_IN_RANGE_OF_PLAYER" ->
                                super.handleError("Get closer.");
                        case "SPEED_LOCKED" -> super.handleError("You are moving too fast!");
                        case "CONTAINED_WITHIN_CAPTURED_REGION" ->
                                super.handleError("Can't link from within a field.");
                        case "SERVER_ERROR" -> super.handleError(getString(R.string.server_error));
                        default -> super.handleError("Unknown error: " + error);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intSetNotificationSettings(final Handler handler) {
        Log.w("Game", "intSetNotificationSettings not yet implemented");
    }

    public void intGetModifiedEntity(String guid, final Handler handler) {
        String[] guids = {guid};
        intGetModifiedEntitiesByGuid(guids, handler);
    }

    public void intGetModifiedEntitiesByGuid(@NonNull String[] guids, final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();

            JSONArray entityGuids = new JSONArray();
            for (String guid : guids) {
                entityGuids.put(guid);
            }
            params.put("params", entityGuids);

            // request basket
            mInterface.request(mHandshake, "gameplay/getModifiedEntitiesByGuid", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intFlipPortal(@NonNull GameEntityPortal portal, @NonNull ItemFlipCard flipCard, final Handler handler) {
        /*
        DOES_NOT_EXIST, OUT_OF_RANGE, WRONG_OWNER_FOR_ITEM, NOT_A_RESOURCE, SERVER_ERROR,
        NEED_MORE_ENERGY, PLAYER_DEPLETED, NO_PLAYER_SPECIFIED, WRONG_LEVEL, PLAYER_DOES_NOT_EXIST,
        NOT_APPLICABLE_FOR_RESOURCE, INVALID_TARGET, CANNOT_FLIP,
        TOO_BUSY_ADA, TOO_BUSY_JAR, TOO_SOON
         */
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            params.put("portalGuid", portal.getEntityGuid());
            params.put("resourceGuid", flipCard.getEntityGuid());

            mInterface.request(mHandshake, "gameplay/flipPortal", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleError(String error) {
                    switch (error) {
                        case "DOES_NOT_EXIST" ->
                                super.handleError(getString(R.string.weapon_does_not_exist));
                        case "NOT_APPLICABLE_FOR_RESOURCE" ->
                                super.handleError(getString(R.string.flipcard_not_applicable_for_resource));
                        case "INVALID_TARGET" ->
                                super.handleError(getString(R.string.flipcard_invalid_target));
                        case "CANNOT_FLIP" -> super.handleError(getString(R.string.CANNOT_FLIP));
                        case "TOO_SOON" -> super.handleError(getString(R.string.FLIPCARD_TOO_SOON));
                        case "OUT_OF_RANGE" -> super.handleError(getString(R.string.out_of_range));
                        case "SPEED_LOCKED" -> // new!
                                super.handleError(getString(R.string.you_are_moving_too_fast));
                        case "PLAYER_DEPLETED", "NEED_MORE_ENERGY" ->
                                super.handleError(getString(R.string.you_don_t_have_enough_xm));
                        case "SERVER_ERROR" -> super.handleError(getString(R.string.server_error));
                        default -> super.handleError("Unknown error: " + error);
                    }
                }

                @Override
                public void handleGameBasket(GameBasket gameBasket) {
                    processGameBasket(gameBasket);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intSetPortalDetailsForCuration(final Handler handler) {
        Log.w("Game", "intSetPortalDetailsForCuration not yet implemented");
    }

    public void intGetUploadUrl(final Handler handler) {
        checkInterface();

        mInterface.request(mHandshake, "playerUndecorated/getUploadUrl", null, null, new RequestResult(handler) {
            @Override
            public void handleResult(String result) {
                getData().putString("Url", result);
            }
        });
    }

    public void intUploadPortalPhotoByUrl(String requestId, String imageUrl, final Handler handler) {
        Log.w("Game", "intUploadPortalPhotoByUrl not yet implemented");
    }

    public void intUploadPortalImage(final Handler handler) {
        Log.w("Game", "intUploadPortalImage not yet implemented");
    }

    public void intFindNearbyPortals(int maxPortals, final Handler handler) {
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
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void intPutBulkPlayerStorage(final Handler handler) {
        try {
            checkInterface();

            JSONObject params = new JSONObject();
            JSONArray actualParams = new JSONArray();
            actualParams.put(getBulkPlayerStorage().toJSONObject());
            params.put("params", actualParams);

            mInterface.request(mHandshake, "playerUndecorated/putBulkPlayerStorage", mLocation, params, new RequestResult(handler) {
                @Override
                public void handleResult(JSONArray result) {
                    // TODO: UNDONE
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public Handshake getHandshake() {
        return mHandshake;
    }

    public void invalidateHandshake() {
        mHandshake = null;
    }

    public BulkPlayerStorage getBulkPlayerStorage() {
        return mStorage;
    }

    public KnobsBundle getKnobs() {
        return mKnobs;
    }

    public synchronized World getWorld() {
        return mWorld;
    }

    public synchronized Inventory getInventory() {
        return mInventory;
    }

    public synchronized Agent getAgent() {
        checkInterface();
        return mAgent;
    }

    public void addSlurpableXMParticles(Set<String> slurpableParticles) {
        mInterface.addSlurpableParticles(slurpableParticles);
        for (var particle : slurpableParticles) {
            mWorld.getXMParticles().remove(Long.parseLong(particle.substring(0, 16), 16));
        }
    }

    public void putAgentName(String guid, String name) {
        mAgentNames.put(guid, name);
    }

    public void putAgentNames(HashMap<String, String> agentNames) {
        mAgentNames.putAll(agentNames);
    }

    public String getAgentName(String guid) {
        return mAgentNames.get(guid);
    }

    public HashMap<String, String> getAgentNames() {
        return mAgentNames;
    }

    public List<String> checkAgentNames(@NonNull HashSet<String> guids) {
        List<String> rejects = new ArrayList<>();
        for (String guid : guids) {
            if (guid != null && !mAgentNames.containsKey(guid)) {
                rejects.add(guid);
            }
        }
        return rejects;
    }

    public void setLocationAccurate(boolean b) {
        mLocationIsAccurate = b;
    }

    public boolean isLocationAccurate() {
        return mLocationIsAccurate;
    }

    public synchronized void addHackResult(Bundle result) {
        mHackResultsQueue.add(result);
    }

    public synchronized boolean hasHackResults() {
        return !mHackResultsQueue.isEmpty();
    }

    public synchronized Bundle pollHackResult() {
        return mHackResultsQueue.poll();
    }
}

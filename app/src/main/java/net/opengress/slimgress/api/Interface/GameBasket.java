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

package net.opengress.slimgress.api.Interface;

import androidx.annotation.NonNull;

import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Game.XMParticle;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Player.PlayerEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

public class GameBasket
{
    private PlayerEntity mPlayerEntity;
    private final List<GameEntityBase> mGameEntities;
    private final List<ItemBase> mInventory;
    private final List<String> mDeletedEntityGuids;
    private final List<XMParticle> mEnergyGlobGuids;
    private final List<PlayerDamage> mPlayerDamages;
    private final List<APGain> mAPGains;

    public GameBasket(@NonNull JSONObject json) throws JSONException
    {
        mPlayerEntity = null;
        mGameEntities = new LinkedList<>();
        mInventory = new LinkedList<>();
        mDeletedEntityGuids = new LinkedList<>();
        mEnergyGlobGuids = new LinkedList<>();
        mPlayerDamages = new LinkedList<>();
        mAPGains = new LinkedList<>();

        processPlayerDamages(json.optJSONArray("playerDamages"));
        processPlayerEntity(json.optJSONArray("playerEntity"));
        processGameEntities(json.getJSONArray("gameEntities"));
        processAPGains(json.optJSONArray("apGains"));
        processLevelUp(json.optJSONObject("levelUp"));
        processInventory(json.getJSONArray("inventory"));
        processEnergyGlobGuids(json.optJSONArray("energyGlobGuids"), json.optString("energyGlobTimestamp"));
        processDeletedEntityGuids(json.getJSONArray("deletedEntityGuids"));
    }

    private void processPlayerDamages(JSONArray playerDamages) throws JSONException
    {
        if (playerDamages != null) {
            for (int i = 0; i < playerDamages.length(); i++) {
                PlayerDamage playerDamage = new PlayerDamage(playerDamages.getJSONObject(i));
                mPlayerDamages.add(playerDamage);
            }
        }
    }

    private void processPlayerEntity(JSONArray playerEntity) throws JSONException
    {
        if (playerEntity != null) {
            mPlayerEntity = new PlayerEntity(playerEntity);
        }
    }

    private void processGameEntities(@NonNull JSONArray gameEntities) throws JSONException
    {
        // iterate over game entities
        for (int i = 0; i < gameEntities.length(); i++) {
            JSONArray resource = gameEntities.getJSONArray(i);

            // deserialize the game entity using the JSON representation
            GameEntityBase newEntity = GameEntityBase.createByJSON(resource);

            // add the new entity to the world
            if (newEntity != null) {
                mGameEntities.add(newEntity);
            }
        }
    }

    private void processAPGains(JSONArray apGains) throws JSONException
    {
        if (apGains != null) {
            for (int i = 0; i < apGains.length(); i++) {
                APGain newGain = new APGain(apGains.getJSONObject(i));
                mAPGains.add(newGain);
            }
        }
    }

    private void processLevelUp(JSONObject levelUp) {
        if (levelUp != null) {
            try {
                SlimgressApplication.getInstance().getLevelUpViewModel().postLevelUpMsgId(levelUp.getInt("nextLevelToken"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void processInventory(@NonNull JSONArray inventory) throws JSONException
    {
        // iterate over inventory items
        for (int i = 0; i < inventory.length(); i++) {
            JSONArray resource = inventory.getJSONArray(i);

            // deserialize the item using the JSON representation
            ItemBase newItem = ItemBase.createByJSON(resource);

            // add the new item to the player inventory
            if (newItem != null) {
                mInventory.add(newItem);
            }
        }
    }

    private void processDeletedEntityGuids(JSONArray deletedEntityGuids) throws JSONException
    {
        if (deletedEntityGuids != null) {
            for (int i = 0; i < deletedEntityGuids.length(); i++) {
                mDeletedEntityGuids.add(deletedEntityGuids.getString(i));
            }
        }
    }

    private void processEnergyGlobGuids(JSONArray energyGlobGuids, String timestamp) throws JSONException
    {
        if (energyGlobGuids != null && !timestamp.isEmpty()) {
            for (int i = 0; i < energyGlobGuids.length(); i++) {
                String guid = energyGlobGuids.getString(i);

                XMParticle newParticle = new XMParticle(guid, timestamp);
                mEnergyGlobGuids.add(newParticle);
            }
        }
    }

    public final PlayerEntity getPlayerEntity()
    {
        return mPlayerEntity;
    }

    public final List<GameEntityBase> getGameEntities()
    {
        return mGameEntities;
    }

    public final List<ItemBase> getInventory()
    {
        return mInventory;
    }

    public final List<String> getDeletedEntityGuids()
    {
        return mDeletedEntityGuids;
    }

    public final List<XMParticle> getEnergyGlobGuids()
    {
        return mEnergyGlobGuids;
    }

    public List<PlayerDamage> getPlayerDamages()
    {
        return mPlayerDamages;
    }

    public List<APGain> getAPGains()
    {
        return mAPGains;
    }
}

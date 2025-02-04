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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Interface.GameBasket;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemFlipCard;
import net.opengress.slimgress.api.Item.ItemMod;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.api.Item.ItemResonator;
import net.opengress.slimgress.api.Item.ModKey;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Inventory {
    private final Map<String, ItemBase> mItems;

    public Inventory() {
        mItems = new HashMap<>();
    }

    public void clear() {
        mItems.clear();
    }

    public void processGameBasket(@NonNull GameBasket basket) {

        // remove deleted entities
        List<String> deletedEntityGuids = basket.getDeletedEntityGuids();
        for (String guid : deletedEntityGuids) {
            mItems.remove(guid);
        }

        // add new inventory items
        List<ItemBase> entities = basket.getInventory();
        for (ItemBase entity : entities) {
            if (!mItems.containsKey(entity.getEntityGuid())) {
                mItems.put(entity.getEntityGuid(), entity);
            }
        }
    }

    @NonNull
    @Contract(" -> new")
    public final List<ItemBase> getItemsList() {
        return new ArrayList<>(mItems.values());
    }

    public final Map<String, ItemBase> getItems() {
        return mItems;
    }

    @NonNull
    public final List<ItemBase> getItems(ItemBase.ItemType type) {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> item : mItems.entrySet()) {
            if (item.getValue().getItemType() == type) {
                items.add(item.getValue());
            }
        }

        return items;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public <T extends ItemBase> List<T> getItemsOfType(Class<T> clazz) {
        List<T> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> item : mItems.entrySet()) {
            if (clazz.isInstance(item.getValue())) {
                items.add((T) item.getValue());
            }
        }
        return items;
    }

    @NonNull
    public final List<ItemBase> getFlipCards(ItemFlipCard.FlipCardType type) {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.FlipCard &&
                    ((ItemFlipCard) item).getFlipCardType() == type) {
                items.add(item);
            }
        }

        return items;
    }

    @NonNull
    public final List<ItemBase> getItems(ItemBase.ItemType type, ItemBase.Rarity rarity) {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == type &&
                    item.getItemRarity() == rarity) {
                items.add(item);
            }
        }

        return items;
    }


    @NonNull
    public final List<ItemBase> getItems(ItemBase.ItemType type, String displayName) {
        // currently seems like a reasonable way to fetch DoubleAP objects from DB
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == type &&
                    Objects.equals(item.getDisplayName(), displayName)) {
                items.add(item);
            }
        }

        return items;
    }

    @NonNull
    public final List<ItemBase> getItems(ItemBase.ItemType type, int accessLevel) {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == type &&
                    item.getItemAccessLevel() == accessLevel) {
                items.add(item);
            }
        }

        return items;
    }

    // probably not applicable at this point
    @NonNull
    public final List<ItemBase> getItems(ItemBase.ItemType type, ItemBase.Rarity rarity, int accessLevel) {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == type &&
                    item.getItemRarity() == rarity &&
                    item.getItemAccessLevel() == accessLevel) {
                items.add(item);
            }
        }

        return items;
    }

    @Nullable
    public final ItemBase findItem(String guid) {
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            if (pair.getKey().equals(guid)) {
                return pair.getValue();
            }
        }

        return null;
    }

    // FIXME don't return resos above your level (also server) or above limits
    @Nullable
    public final ItemResonator getResoForDeployment(int accessLevel) {

        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.Resonator &&
                    item.getItemAccessLevel() == accessLevel) {
                return (ItemResonator) item;
            }
        }

        return null;
    }

    @NonNull
    public final List<ItemResonator> getResosForDeployment(int accessLevel) {
        List<ItemResonator> items = new LinkedList<>();

        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.Resonator &&
                    item.getItemAccessLevel() == accessLevel) {
                items.add((ItemResonator) item);
            }
        }

        return items;
    }

    // FIXME don't return resos above your level (also server) or above limits
    @NonNull
    public final List<ItemResonator> getResosForUpgrade(int accessLevel) {
        List<ItemResonator> items = new LinkedList<>();

        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.Resonator &&
                    item.getItemAccessLevel() > accessLevel) {
                items.add((ItemResonator) item);
            }
        }

        return items;
    }

    public final void removeItem(String guid) {
        mItems.remove(guid);
    }

    public final void removeItem(@NonNull ItemBase item) {
        removeItem(item.getEntityGuid());
    }

    public List<ItemMod> getMods() {
        List<ItemMod> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item instanceof ItemMod) {
                items.add((ItemMod) item);
            }
        }

        return items;
    }

    @NonNull
    public final List<ItemMod> getMods(ItemBase.Rarity rarity) {
        List<ItemMod> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item instanceof ItemMod &&
                    item.getItemRarity() == rarity) {
                items.add((ItemMod) item);
            }
        }

        return items;
    }

    @Nullable
    public final ItemMod getModForDeployment(ModKey key) {
        for (ItemBase item : mItems.values()) {
            if (item instanceof ItemMod mod) {
                if (mod.getItemRarity() == key.rarity && mod.getModDisplayName().equals(key.modDisplayName)) {
                    return mod;
                }
            }
        }
        return null;
    }

    @NonNull
    public final List<ItemPortalKey> getKeysForPortal(@NonNull GameEntityPortal portal) {
        return getKeysForPortal(portal.getEntityGuid());
    }

    @NonNull
    public final List<ItemPortalKey> getKeysForPortal(String portal) {
        List<ItemPortalKey> items = new ArrayList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.PortalKey &&
                    Objects.equals(((ItemPortalKey) item).getPortalGuid(), portal)) {
                items.add((ItemPortalKey) item);
            }
        }

        return items;
    }

    @Nullable
    public final ItemPortalKey getKeyForPortal(@NonNull GameEntityPortal portal) {
        return getKeyForPortal(portal.getEntityGuid());
    }

    @Nullable
    public final ItemPortalKey getKeyForPortal(String portal) {
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.PortalKey &&
                    Objects.equals(((ItemPortalKey) item).getPortalGuid(), portal)) {
                return (ItemPortalKey) item;
            }
        }
        return null;
    }
}

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

package net.opengress.slimgress.API.Game;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Interface.GameBasket;
import net.opengress.slimgress.API.Item.ItemBase;
import net.opengress.slimgress.API.Item.ItemResonator;

public class Inventory
{
    private final Map<String, ItemBase> mItems;

    public Inventory()
    {
        mItems = new HashMap<>();
    }

    public void clear()
    {
        mItems.clear();
    }

    public void processGameBasket(GameBasket basket)
    {
        // add new inventory items
        List<ItemBase> entities = basket.getInventory();
        for (ItemBase entity : entities) {
            if (!mItems.containsKey(entity.getEntityGuid()))
                mItems.put(entity.getEntityGuid(), entity);
        }

        // remove deleted entities
        List<String> deletedEntityGuids = basket.getDeletedEntityGuids();
        for (String guid : deletedEntityGuids) {
            mItems.remove(guid);
        }
    }

    public final Map<String, ItemBase> getItems()
    {
        return mItems;
    }

    public final List<ItemBase> getItemsList()
    {
        return new ArrayList<>(mItems.values());
    }

    public final List<ItemBase> getItems(ItemBase.ItemType type)
    {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> item : mItems.entrySet()) {
            if (item.getValue().getItemType() == type)
                items.add(item.getValue());
        }

        return items;
    }

    public final List<ItemBase> getItems(ItemBase.ItemType type, ItemBase.Rarity rarity)
    {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == type &&
                    item.getItemRarity() == rarity)
                items.add(item);
        }

        return items;
    }

    public final List<ItemBase> getItems(ItemBase.ItemType type, int accessLevel)
    {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == type &&
                    item.getItemAccessLevel() == accessLevel)
                items.add(item);
        }

        return items;
    }

    // probably not applicable at this point
    public final List<ItemBase> getItems(ItemBase.ItemType type, ItemBase.Rarity rarity, int accessLevel)
    {
        List<ItemBase> items = new LinkedList<>();
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == type &&
                    item.getItemRarity() == rarity &&
                    item.getItemAccessLevel() == accessLevel)
                items.add(item);
        }

        return items;
    }

    public final ItemBase findItem(String guid)
    {
        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            if (pair.getKey().equals(guid))
                return pair.getValue();
        }

        return null;
    }

    public final ItemResonator getResoForDeployment(int accessLevel) {

        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.Resonator &&
                    item.getItemAccessLevel() == accessLevel)
                return (ItemResonator) item;
        }

        return null;
    }

    public final List<ItemResonator> getResosForUpgrade(int accessLevel)
    {
        List<ItemResonator> items = new LinkedList<>();

        for (Map.Entry<String, ItemBase> pair : mItems.entrySet()) {
            ItemBase item = pair.getValue();
            if (item.getItemType() == ItemBase.ItemType.Resonator &&
                    item.getItemAccessLevel() > accessLevel)
                items.add((ItemResonator) item);
        }

        return items;
    }
}

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

package net.opengress.slimgress;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.Inventory;
import net.opengress.slimgress.API.Item.ItemBase;
import net.opengress.slimgress.API.Item.ItemBase.Rarity;
import net.opengress.slimgress.API.Item.ItemFlipCard;
import net.opengress.slimgress.API.Item.ItemMod;
import net.opengress.slimgress.API.Item.ItemPortalKey;
import net.opengress.slimgress.API.Item.ItemBase.ItemType;
import net.opengress.slimgress.API.Item.ItemFlipCard.FlipCardType;
import net.opengress.slimgress.API.Item.ItemMedia;

import android.os.Bundle;
import android.os.Handler;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;

public class FragmentInventory extends Fragment {
    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();

    ArrayList<String> mGroupNames;
    ArrayList<Object> mGroups;
    ArrayList<InventoryListItem> mGroupMedia;
    ArrayList<InventoryListItem> mGroupMods;
    ArrayList<InventoryListItem> mGroupPortalKeys;
    ArrayList<InventoryListItem> mGroupPowerCubes;
    ArrayList<InventoryListItem> mGroupResonators;
    ArrayList<InventoryListItem> mGroupWeapons;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_inventory,
                container, false);

        final ExpandableListView list = rootView.findViewById(R.id.listView);
        final ProgressBar progress = rootView.findViewById(R.id.progressBar1);

        list.setVisibility(View.INVISIBLE);
        progress.setVisibility(View.VISIBLE);

        // create group names
        mGroupNames = new ArrayList<>();
        mGroups = new ArrayList<>();

        mGroupMedia = new ArrayList<>();
        mGroupMods = new ArrayList<>();
        mGroupPortalKeys = new ArrayList<>();
        mGroupPowerCubes = new ArrayList<>();
        mGroupResonators = new ArrayList<>();
        mGroupWeapons = new ArrayList<>();

        final FragmentInventory thisObject = this;

        final Handler handler = new Handler();

        FragmentInventory.this.fillInventory(() -> {
            handler.post(() -> {
                InventoryList inventoryList = new InventoryList(mGroupNames, mGroups);
                inventoryList.setInflater(inflater, thisObject.getActivity());
                list.setAdapter(inventoryList);
                list.setVisibility(View.VISIBLE);
                progress.setVisibility(View.INVISIBLE);
            });
        });

        return rootView;
    }

    private void fillInventory(final Runnable callback) {
        new Thread(() -> {
            fillMedia();
            fillMods();
            fillResonators();
            fillPortalKeys();
            fillWeapons();
            fillPowerCubes();

            callback.run();
        }).start();
    }

    void fillMedia() {
        Inventory inv = mGame.getInventory();

        int count = 0;
        for (int level = 1; level <= 8; level++) {
            List<ItemBase> items = inv.getItems(ItemType.Media, level);
            count += items.size();

            LinkedList<ItemMedia> skipItems = new LinkedList<>();
            ArrayList<String> medias = new ArrayList<>();
            for (ItemBase item1 : items) {
                ItemMedia theItem1 = (ItemMedia) item1;

                // skip items that have already been checked
                if (skipItems.contains(theItem1))
                    continue;

                String descr = "L" + level + " " + theItem1.getMediaDescription();
                medias.add(item1.getEntityGuid());

                // check for multiple media items with the same description
                for (ItemBase item2 : items) {
                    ItemMedia theItem2 = (ItemMedia) item2;

                    // don't check the doubles
                    if (theItem2 == theItem1)
                        continue;

                    if (theItem1.getMediaDescription().equals(theItem2.getMediaDescription())) {
                        skipItems.add(theItem2);
                        medias.add(theItem2.getEntityGuid());
                    }
                }

                if (!theItem1.getMediaHasBeenViewed())
                    descr += " [NEW]";

                InventoryListItem media = new InventoryListItem(descr, ItemType.Media, "@drawable/ic_launcher", medias);
                mGroupMedia.add(media);
            }
        }
        mGroupNames.add("Media (" + count + ")");
        mGroups.add(mGroupMedia);
    }

    void fillMods() {
        Inventory inv = mGame.getInventory();

        ItemType[] types = {ItemType.ModForceAmp,
                ItemType.ModHeatsink,
                ItemType.ModLinkAmp,
                ItemType.ModMultihack,
                ItemType.ModShield,
                ItemType.ModTurret
        };

        Rarity[] rarities = {Rarity.None,
                Rarity.None,
                Rarity.LessCommon,
                Rarity.Common,
                Rarity.VeryCommon,
                Rarity.Rare,
                Rarity.VeryRare,
                Rarity.ExtraRare
        };

        int count = 0;

        for (ItemType type : types) {
            for (Rarity rarity : rarities) {
                List<ItemBase> items = inv.getItems(type, rarity);
                count += items.size();

                if (!items.isEmpty()) {
                    ItemMod theFirstItem = (ItemMod) (items.get(0));
                    String descr = theFirstItem.getModDisplayName();

                    switch (theFirstItem.getItemRarity()) {
                        case None:
                            break;
                        case LessCommon:
                            descr += " - Less Common";
                            break;
                        case Common:
                            descr += " - Common";
                            break;
                        case VeryCommon:
                            descr += " - Very Common";
                            break;
                        case Rare:
                            descr += " - Rare";
                            break;
                        case VeryRare:
                            descr += " - VeryRare";
                            break;
                        case ExtraRare:
                            descr += " - Extra Rare";
                            break;
                    }

                    ArrayList<String> mods = new ArrayList<>();
                    for (ItemBase item : items) {
                        mods.add(item.getEntityGuid());
                    }

                    InventoryListItem mod = new InventoryListItem(descr, type, "@drawable/ic_launcher", mods);
                    mGroupMods.add(mod);
                }
            }
        }

        mGroupNames.add("Mods (" + count + ")");
        mGroups.add(mGroupMods);
    }

    void fillResonators() {
        Inventory inv = mGame.getInventory();

        int count = 0;
        for (int level = 1; level <= 8; level++) {
            List<ItemBase> items = inv.getItems(ItemType.Resonator, level);
            count += items.size();

            String descr = "L" + level + " Resonator";
            if (!items.isEmpty()) {
                ArrayList<String> resos = new ArrayList<>();
                for (ItemBase item : items) {
                    resos.add(item.getEntityGuid());
                }
                InventoryListItem reso = new InventoryListItem(descr, ItemType.Resonator, "@drawable/ic_launcher", resos);
                mGroupResonators.add(reso);
            }
        }
        mGroupNames.add("Resonators (" + count + ")");
        mGroups.add(mGroupResonators);

    }

    void fillWeapons() {
        Inventory inv = mGame.getInventory();

        // get xmp weapon items
        int count = 0;
        for (int level = 1; level <= 8; level++) {
            List<ItemBase> items = inv.getItems(ItemType.WeaponXMP, level);
            count += items.size();

            String descr = "L" + level + " XMP";
            if (!items.isEmpty()) {
                ArrayList<String> weapons = new ArrayList<>();
                for (ItemBase item : items) {
                    weapons.add(item.getEntityGuid());
                }
                InventoryListItem weapon = new InventoryListItem(descr, ItemType.Resonator, "@drawable/ic_launcher", weapons);
                mGroupWeapons.add(weapon);
            }
        }

        // get ultrastrike weapon items
        for (int level = 1; level <= 8; level++) {
            List<ItemBase> items = inv.getItems(ItemType.WeaponUltraStrike, level);
            count += items.size();

            String descr = "L" + level + " UltraStrike";
            if (!items.isEmpty()) {
                ArrayList<String> weapons = new ArrayList<>();
                for (ItemBase item : items) {
                    weapons.add(item.getEntityGuid());
                }
                InventoryListItem weapon = new InventoryListItem(descr, ItemType.Resonator, "@drawable/ic_launcher", weapons);
                mGroupWeapons.add(weapon);
            }
        }

        // get flipcard items
        List<ItemBase> items = inv.getItems(ItemType.FlipCard);
        ArrayList<String> jarvises = new ArrayList<>();
        ArrayList<String> adas = new ArrayList<>();
        count += items.size();

        int adaCount = 0, jarvisCount = 0;
        for (ItemBase item : items) {
            ItemFlipCard theItem = (ItemFlipCard) item;
            if (theItem.getFlipCardType() == FlipCardType.Ada)
                adas.add(theItem.getEntityGuid());
            else if (theItem.getFlipCardType() == FlipCardType.Jarvis)
                jarvises.add(theItem.getEntityGuid());
        }

        String descr = "ADA Refactor";
        if (!adas.isEmpty()) {
            InventoryListItem weapon = new InventoryListItem(descr, ItemType.FlipCard, "@drawable/ic_launcher", adas);
            weapon.setFlipCardType(FlipCardType.Ada);
            mGroupWeapons.add(weapon);
        }

        descr = "Jarvis Virus";
        if (!jarvises.isEmpty()) {
            InventoryListItem weapon = new InventoryListItem(descr, ItemType.FlipCard, "@drawable/ic_launcher", jarvises);
            weapon.setFlipCardType(FlipCardType.Jarvis);
            mGroupWeapons.add(weapon);
        }

        mGroupNames.add("Weapons (" + count + ")");
        mGroups.add(mGroupWeapons);
    }

    void fillPowerCubes() {
        Inventory inv = mGame.getInventory();

        int count = 0;
        for (int level = 1; level <= 8; level++) {
            List<ItemBase> items = inv.getItems(ItemType.PowerCube, level);
            count += items.size();

            String descr = "L" + level + " PowerCube";
            if (!items.isEmpty()) {
                ArrayList<String> cubes = new ArrayList<>();
                for (ItemBase item : items) {
                    cubes.add(item.getEntityGuid());
                }
                InventoryListItem cube = new InventoryListItem(descr, ItemType.Resonator, "@drawable/ic_launcher", cubes);
                mGroupResonators.add(cube);
            }
        }
        mGroupNames.add("PowerCubes (" + count + ")");
        mGroups.add(mGroupPowerCubes);
    }

    void fillPortalKeys() {
        Inventory inv = mGame.getInventory();

        int count = 0;
        List<ItemBase> items = inv.getItems(ItemType.PortalKey);
        count += items.size();

        LinkedList<ItemPortalKey> skipItems = new LinkedList<>();
        for (ItemBase item1 : items) {
            ItemPortalKey theItem1 = (ItemPortalKey) item1;
            ArrayList<String> keys = new ArrayList<>();

            // skip items that have already been checked
            if (skipItems.contains(theItem1))
                continue;

            String descr = theItem1.getPortalTitle();
            keys.add(item1.getEntityGuid());

            // check for multiple portal keys with the same portal guid
            for (ItemBase item2 : items) {
                ItemPortalKey theItem2 = (ItemPortalKey) item2;

                // don't check the doubles
                if (theItem2 == theItem1)
                    continue;

                if (theItem1.getPortalGuid().equals(theItem2.getPortalGuid())) {
                    skipItems.add(theItem2);
                    keys.add(item2.getEntityGuid());
                }
            }

            InventoryListItem key = new InventoryListItem(descr, ItemType.Media, "@drawable/ic_launcher", keys);
            mGroupPortalKeys.add(key);
        }
        mGroupNames.add("PortalKeys (" + count + ")");
        mGroups.add(mGroupPortalKeys);
    }

}

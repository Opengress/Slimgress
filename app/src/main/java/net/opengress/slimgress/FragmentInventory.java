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

import static androidx.core.content.ContextCompat.getDrawable;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_KEY_SORT;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_KEY_SORT_VISIBLE;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_LEVEL_FILTER_VISIBLE;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_RARITY_FILTER_VISIBLE;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_SEARCH_BOX_VISIBLE;
import static net.opengress.slimgress.ViewHelpers.getImageForCubeLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForResoLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForUltrastrikeLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForXMPLevel;
import static net.opengress.slimgress.ViewHelpers.setUpSpinner;
import static net.opengress.slimgress.api.Common.Utils.notBouncing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.Inventory;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemBase.ItemType;
import net.opengress.slimgress.api.Item.ItemBase.Rarity;
import net.opengress.slimgress.api.Item.ItemCapsule;
import net.opengress.slimgress.api.Item.ItemFlipCard;
import net.opengress.slimgress.api.Item.ItemFlipCard.FlipCardType;
import net.opengress.slimgress.api.Item.ItemMedia;
import net.opengress.slimgress.api.Item.ItemMod;
import net.opengress.slimgress.api.Item.ItemPlayerPowerup;
import net.opengress.slimgress.api.Item.ItemPortalKey;

import java.text.Collator;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class FragmentInventory extends Fragment {
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private SharedPreferences mPrefs;

    private ArrayList<String> mGroupNames;
    private ArrayList<Object> mGroups;
    private ArrayList<InventoryListItem> mGroupCapsules;
    private ArrayList<InventoryListItem> mGroupMedia;
    private ArrayList<InventoryListItem> mGroupMods;
    private ArrayList<InventoryListItem> mGroupPortalKeys;
    private ArrayList<InventoryListItem> mGroupPowerCubes;
    private ArrayList<InventoryListItem> mGroupResonators;
    private ArrayList<InventoryListItem> mGroupWeapons;

    private ArrayList<InventoryListItem> mGroupOther;

    private InventoryList mInventoryList;
    private Observer<Inventory> mObserver;

    private Spinner mRaritySpinner;
    private Spinner mSortSpinner;
    private SearchView mSearchBox;
    private Spinner mLevelSpinner;
    private TextView mTotal;

    final String[] mSorts = {"Deployment", "Distance", "Level", "Name", "Team"};
    final String[] mRarityNames = {"ALL", "Very Common", "Common", "Less Common", "Rare", "Very Rare", "Extra Rare"};
    final String[] mLevelNames = {"ALL", "Level 1", "Level 2", "Level 3", "Level 4", "Level 5", "Level 6", "Level 7", "Level 8"};
    int mInventoryKeySort;
    int mInventoryCount = 0;
    boolean mFirstRun = true;

    @Override
    public void onResume() {

        // one day i'd like to centralise all this. same defaults repeated on device screen.
        updateItemVisibilityForPreference(mSearchBox, PREFS_INVENTORY_SEARCH_BOX_VISIBLE, false);
        updateItemVisibilityForPreference(mSortSpinner, PREFS_INVENTORY_KEY_SORT_VISIBLE, true);
        updateItemVisibilityForPreference(mLevelSpinner, PREFS_INVENTORY_LEVEL_FILTER_VISIBLE, false);
        updateItemVisibilityForPreference(mRaritySpinner, PREFS_INVENTORY_RARITY_FILTER_VISIBLE, false);
        if (!mPrefs.getBoolean(PREFS_INVENTORY_SEARCH_BOX_VISIBLE, true)) {
            mSearchBox.setQuery(null, false);
        }

        // FIXME monkeypatch
        int inventoryCount = mApp.getGame().getInventory().getItems().size();
        if (inventoryCount == mInventoryCount && mInventoryCount != 0) {
            super.onResume();
            return;
        }
        mInventoryCount = inventoryCount;

        final ExpandableListView list = requireView().findViewById(R.id.listView);
        final ProgressBar progress = requireView().findViewById(R.id.progressBar1);

        final Runnable runnable = getRunnable(getLayoutInflater(), list, progress);

        if (mFirstRun && !mGame.getInventory().getItems().isEmpty()) {
            FragmentInventory.this.fillInventory(runnable);
        }
        mObserver = inventory -> {
            // FIXME monkeypatch
            int c = mApp.getGame().getInventory().getItems().size();
            if (c != mInventoryCount || mInventoryCount == 0) {
                FragmentInventory.this.fillInventory(runnable);
            }
            mInventoryCount = c;
        };

        mApp.getInventoryViewModel().getInventory().observe(getViewLifecycleOwner(), mObserver);

        mFirstRun = false;
        super.onResume();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_inventory,
                container, false);

        final ExpandableListView list = rootView.findViewById(R.id.listView);
        final ProgressBar progress = rootView.findViewById(R.id.progressBar1);
        mPrefs = mApp.getApplicationContext().getSharedPreferences(requireActivity().getApplicationInfo().packageName, Context.MODE_PRIVATE);
        mInventoryKeySort = mPrefs.getInt(PREFS_INVENTORY_KEY_SORT, 3);

        list.setVisibility(View.INVISIBLE);
        progress.setVisibility(View.VISIBLE);

        mTotal = rootView.findViewById(R.id.textFragmentInventoryTotal);
        mRaritySpinner = setUpSpinner(mRarityNames, rootView, R.id.spinnerRarity);
        mRaritySpinner.setSelection(0);
        mRaritySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (mInventoryList != null) {
                    mInventoryList.limitRarities(mRarityNames[i]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mLevelSpinner = setUpSpinner(mLevelNames, rootView, R.id.spinnerLevel);
        mLevelSpinner.setSelection(0);
        mLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (mInventoryList != null) {
                    mInventoryList.limitLevels(mLevelNames[i]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mSortSpinner = setUpSpinner(mSorts, rootView, R.id.spinnerSort);
        mSortSpinner.setSelection(mInventoryKeySort);
        mSortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                final SharedPreferences.Editor edit = mPrefs.edit();
                // updating mInventoryKeySort is a purely defensive move since we currently don't use it again after this
                mInventoryKeySort = i;
                edit.putInt(PREFS_INVENTORY_KEY_SORT, i);
                edit.apply();
                sortKeys(mSorts[i]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mSearchBox = rootView.findViewById(R.id.editTextSearch);
        mSearchBox.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (mInventoryList == null) {
                    // try to fight a bug which is probably a ghost
                    return false;
                }
                mInventoryList.setSearchText(newText);
                notifyDatasetChanged();
                return false;
            }
        });

        return rootView;
    }

    private void updateItemVisibilityForPreference(@NonNull View item, String preference, boolean defaultValue) {
        item.setVisibility(mPrefs.getBoolean(preference, defaultValue) ? View.VISIBLE : View.GONE);
    }

    private void notifyDatasetChanged() {
        if (mInventoryList != null && notBouncing("updateInventoryList", 500)) {
            mInventoryList.notifyDataSetChanged();
        }
    }

    private void sortKeys(String by) {
        if (mGroupPortalKeys == null) {
            // we get here when keys haven't been set up
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            switch (by) {
                case "Deployment" -> mGroupPortalKeys.sort((item1, item2) -> {
                    ItemPortalKey key1 = (ItemPortalKey) mGame.getInventory().getItems().get(item1.getFirstID());
                    ItemPortalKey key2 = (ItemPortalKey) mGame.getInventory().getItems().get(item2.getFirstID());
                    if (key1 == null && key2 == null) {
                        return 0;
                    }
                    if (key1 == null) {
                        return 1;
                    }
                    if (key2 == null) {
                        return -1;
                    }
                    GameEntityPortal portal1 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key1.getPortalGuid());
                    GameEntityPortal portal2 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key2.getPortalGuid());
                    if (portal1 == null && portal2 == null) {
                        return 0;
                    }
                    if (portal1 == null) {
                        return 1;
                    }
                    if (portal2 == null) {
                        return -1;
                    }
                    return Integer.compare(portal1.getPortalResonatorCount(), portal2.getPortalResonatorCount());
                });
                case "Distance" ->
                        mGroupPortalKeys.sort(Comparator.comparingInt(inventoryListItem -> (int) inventoryListItem.getDistance(mGame.getLocation())));
                case "Level" -> mGroupPortalKeys.sort((item1, item2) -> {
                    ItemPortalKey key1 = (ItemPortalKey) mGame.getInventory().getItems().get(item1.getFirstID());
                    ItemPortalKey key2 = (ItemPortalKey) mGame.getInventory().getItems().get(item2.getFirstID());
                    if (key1 == null && key2 == null) {
                        return 0;
                    }
                    if (key1 == null) {
                        return 1;
                    }
                    if (key2 == null) {
                        return -1;
                    }
                    GameEntityPortal portal1 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key1.getPortalGuid());
                    GameEntityPortal portal2 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key2.getPortalGuid());
                    if (portal1 == null && portal2 == null) {
                        return 0;
                    }
                    if (portal1 == null) {
                        return 1;
                    }
                    if (portal2 == null) {
                        return -1;
                    }
                    return Integer.compare(portal1.getPortalLevel(), portal2.getPortalLevel());
                });
                case "Team" -> mGroupPortalKeys.sort((item1, item2) -> {
                    ItemPortalKey key1 = (ItemPortalKey) mGame.getInventory().getItems().get(item1.getFirstID());
                    ItemPortalKey key2 = (ItemPortalKey) mGame.getInventory().getItems().get(item2.getFirstID());
                    if (key1 == null && key2 == null) {
                        return 0;
                    }
                    if (key1 == null) {
                        return 1;
                    }
                    if (key2 == null) {
                        return -1;
                    }
                    GameEntityPortal portal1 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key1.getPortalGuid());
                    GameEntityPortal portal2 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key2.getPortalGuid());
                    if (portal1 == null && portal2 == null) {
                        return 0;
                    }
                    if (portal1 == null) {
                        return 1;
                    }
                    if (portal2 == null) {
                        return -1;
                    }
                    return Integer.compare(portal1.getPortalTeam().getColour(), portal2.getPortalTeam().getColour());
                });
                default ->
                        mGroupPortalKeys.sort((item1, item2) -> Collator.getInstance().compare(item1.getDescription(), item2.getDescription()));
            }

        } else {
            sortKeysTheOldWay(by);
        }
        notifyDatasetChanged();
    }

    // untested!
    private void sortKeysTheOldWay(String by) {
        ItemPortalKey key1;
        ItemPortalKey key2;
        GameEntityPortal portal1;
        GameEntityPortal portal2;
        for (int i = 0; i < mGroupPortalKeys.size() - 1; i++) {
            for (int j = 0; j < mGroupPortalKeys.size() - i - 1; j++) {
                InventoryListItem item1 = mGroupPortalKeys.get(j);
                InventoryListItem item2 = mGroupPortalKeys.get(j + 1);
                switch (by) {
                    case "Deployment" -> {
                        key1 = (ItemPortalKey) mGame.getInventory().getItems().get(item1.getFirstID());
                        assert key1 != null;
                        portal1 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key1.getPortalGuid());
                        if (portal1 == null) {
                            break;
                        }
                        key2 = (ItemPortalKey) mGame.getInventory().getItems().get(item2.getFirstID());
                        assert key2 != null;
                        portal2 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key2.getPortalGuid());
                        if (portal2 == null) {
                            break;
                        }
                        if ((portal1.getPortalResonatorCount() - portal2.getPortalResonatorCount()) > 0) {
                            mGroupPortalKeys.set(j, item2);
                            mGroupPortalKeys.set(j + 1, item1);
                        }
                    }
                    case "Distance" -> {
                        if ((item1.getDistance(mGame.getLocation()) - item2.getDistance(mGame.getLocation())) > 0) {
                            mGroupPortalKeys.set(j, item2);
                            mGroupPortalKeys.set(j + 1, item1);
                        }
                    }
                    case "Level" -> {
                        key1 = (ItemPortalKey) mGame.getInventory().getItems().get(item1.getFirstID());
                        assert key1 != null;
                        portal1 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key1.getPortalGuid());
                        if (portal1 == null) {
                            break;
                        }
                        key2 = (ItemPortalKey) mGame.getInventory().getItems().get(item2.getFirstID());
                        assert key2 != null;
                        portal2 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key2.getPortalGuid());
                        if (portal2 == null) {
                            break;
                        }
                        if ((portal1.getPortalLevel() - portal2.getPortalLevel()) > 0) {
                            mGroupPortalKeys.set(j, item2);
                            mGroupPortalKeys.set(j + 1, item1);
                        }
                    }
                    case "Team" -> {
                        key1 = (ItemPortalKey) mGame.getInventory().getItems().get(item1.getFirstID());
                        assert key1 != null;
                        portal1 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key1.getPortalGuid());
                        if (portal1 == null) {
                            break;
                        }
                        key2 = (ItemPortalKey) mGame.getInventory().getItems().get(item2.getFirstID());
                        assert key2 != null;
                        portal2 = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key2.getPortalGuid());
                        if (portal2 == null) {
                            break;
                        }
                        if ((portal1.getPortalTeam().getColour() - portal2.getPortalTeam().getColour()) > 0) {
                            mGroupPortalKeys.set(j, item2);
                            mGroupPortalKeys.set(j + 1, item1);
                        }
                    }
                    default -> {
                        if (Collator.getInstance().compare(item1.getDescription(), item2.getDescription()) > 0) {
                            mGroupPortalKeys.set(j, item2);
                            mGroupPortalKeys.set(j + 1, item1);
                        }
                    }
                }
            }
        }
    }

    private @NonNull Runnable getRunnable(LayoutInflater inflater, ExpandableListView list, ProgressBar progress) {
        final FragmentInventory thisObject = this;

        final Handler handler = new Handler();
        return () -> handler.post(() -> {
            Context context = thisObject.getContext();
            if (context == null) {
                return;
            }
            mInventoryList = new InventoryList(context, mGroupNames, mGroups);
            mInventoryList.setInflater(inflater, thisObject.getActivity());
            list.setAdapter(mInventoryList);
            list.setVisibility(View.VISIBLE);
            progress.setVisibility(View.INVISIBLE);

            // FIXME keys may not be sorted if you hit inventory before game data updates in scanner
            sortKeys(mSorts[mInventoryKeySort]);
            notifyDatasetChanged();

            // Update location display on keys - is this expensive? we could update every 10sec?
            mApp.getLocationViewModel().getLocationData().observe(getViewLifecycleOwner(), location -> notifyDatasetChanged());
        });
    }

    private void fillInventory(final Runnable callback) {
        // FIXME: I can't see the sense in the below
//        mApp.getInventoryViewModel().getInventory().removeObserver(mObserver);
        requireActivity().runOnUiThread(() -> {
            // create group names
            mGroupNames = new ArrayList<>();
            mGroups = new ArrayList<>();

            mGroupCapsules = new ArrayList<>();
            mGroupMedia = new ArrayList<>();
            mGroupMods = new ArrayList<>();
            mGroupOther = new ArrayList<>();
            mGroupPortalKeys = new ArrayList<>();
            mGroupPowerCubes = new ArrayList<>();
            mGroupResonators = new ArrayList<>();
            mGroupWeapons = new ArrayList<>();
            fillCapsules();
            fillMedia();
            fillMods();
            fillResonators();
            fillPortalKeys();
            fillWeapons();
            fillPowerCubes();
            fillOther();

            mTotal.setText(MessageFormat.format("{0}", mGame.getInventory().getItems().size()));

            callback.run();
        });
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
                if (skipItems.contains(theItem1)) {
                    continue;
                }

                String descr = "L" + level + " " + theItem1.getMediaDescription();
                medias.add(item1.getEntityGuid());

                // check for multiple media items with the same description
                for (ItemBase item2 : items) {
                    ItemMedia theItem2 = (ItemMedia) item2;

                    // don't check the doubles
                    if (theItem2 == theItem1) {
                        continue;
                    }

                    if (theItem1.getMediaDescription().equals(theItem2.getMediaDescription())) {
                        skipItems.add(theItem2);
                        medias.add(theItem2.getEntityGuid());
                    }
                }

                if (!theItem1.getMediaHasBeenViewed()) {
                    descr += " [NEW]";
                }

                InventoryListItem media = new InventoryListItem(descr, ItemType.Media, getDrawable(requireContext(), R.drawable.no_image), R.drawable.no_image, medias, items.get(0).getItemRarity());
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
                    InventoryListItem mod = getModInventoryListItem(type, items);
                    mGroupMods.add(mod);
                }
            }
        }

        mGroupNames.add("Mods (" + count + ")");
        mGroups.add(mGroupMods);
    }

    private @NonNull InventoryListItem getModInventoryListItem(ItemType type, List<ItemBase> items) {
        ItemMod theFirstItem = (ItemMod) (items.get(0));
        String descr = theFirstItem.getModDisplayName();
        var rarity = theFirstItem.getItemRarity();

        switch (rarity) {
            case None -> {
            }
            case LessCommon -> descr += " - Less Common";
            case Common -> descr += " - Common";
            case VeryCommon -> descr += " - Very Common";
            case Rare -> descr += " - Rare";
            case VeryRare -> descr += " - VeryRare";
            case ExtraRare -> descr += " - Extra Rare";
        }

        ArrayList<String> mods = new ArrayList<>();
        for (ItemBase item : items) {
            mods.add(item.getEntityGuid());
        }

        int drawable = switch (theFirstItem.getItemType()) {
            case ModForceAmp -> R.drawable.force_amp; // always rare
            case ModHeatsink -> switch (rarity) {
                case Common -> R.drawable.heatsink_common;
                case Rare -> R.drawable.heatsink_rare;
                case VeryRare -> R.drawable.heatsink_very_rare;
                default -> R.drawable.no_image;
            };
            case ModLinkAmp -> switch (rarity) { // always rare or very rare, for now
                case Rare -> R.drawable.linkamp_rare;
                case VeryRare -> R.drawable.linkamp_very_rare;
                default -> R.drawable.no_image;
            };
            case ModMultihack -> switch (rarity) {
                case Common -> R.drawable.multihack_common;
                case Rare -> R.drawable.multihack_rare;
                case VeryRare -> R.drawable.multihack_very_rare;
                default -> R.drawable.no_image;
            };
            case ModShield -> switch (rarity) {
                case Common -> R.drawable.shield_common;
                case Rare -> R.drawable.shield_rare;
                case VeryRare -> R.drawable.shield_very_rare;
                default -> R.drawable.no_image;
            };
            case ModTurret -> R.drawable.turret; // always rare
            case Capsule -> R.drawable.capsule; // always rare?
            default -> R.drawable.no_image;
        };

        var mod = new InventoryListItem(descr, type, getDrawable(requireContext(), drawable), drawable, mods);
        mod.setRarity(rarity);
        return mod;
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
                int drawable = getImageForResoLevel(level);
                InventoryListItem reso = new InventoryListItem(descr, ItemType.Resonator, getDrawable(requireContext(), drawable), drawable, resos, items.get(0).getItemRarity(), level);
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
                int drawable = getImageForXMPLevel(level);
                InventoryListItem weapon = new InventoryListItem(descr, ItemType.WeaponXMP, getDrawable(requireContext(), drawable), drawable, weapons, items.get(0).getItemRarity(), level);
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
                int drawable = getImageForUltrastrikeLevel(level);
                InventoryListItem weapon = new InventoryListItem(descr, ItemType.WeaponUltraStrike, getDrawable(requireContext(), drawable), drawable, weapons, items.get(0).getItemRarity(), level);
                mGroupWeapons.add(weapon);
            }
        }

        // get flipcard items
        List<ItemBase> items = inv.getItems(ItemType.FlipCard);
        ArrayList<String> jarvises = new ArrayList<>();
        ArrayList<String> adas = new ArrayList<>();
        count += items.size();

        for (ItemBase item : items) {
            ItemFlipCard theItem = (ItemFlipCard) item;
            if (theItem.getFlipCardType() == FlipCardType.Ada) {
                adas.add(theItem.getEntityGuid());
            } else if (theItem.getFlipCardType() == FlipCardType.Jarvis) {
                jarvises.add(theItem.getEntityGuid());
            }
        }

        String descr = "ADA Refactor";
        if (!adas.isEmpty()) {
            InventoryListItem weapon = new InventoryListItem(descr, ItemType.FlipCard, getDrawable(requireContext(), R.drawable.ada), R.drawable.ada, adas, items.get(0).getItemRarity());
            weapon.setFlipCardType(FlipCardType.Ada);
            mGroupWeapons.add(weapon);
        }

        descr = "Jarvis Virus";
        if (!jarvises.isEmpty()) {
            InventoryListItem weapon = new InventoryListItem(descr, ItemType.FlipCard, getDrawable(requireContext(), R.drawable.jarvis), R.drawable.jarvis, jarvises, items.get(0).getItemRarity());
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
                int drawable = getImageForCubeLevel(level);
                InventoryListItem cube = new InventoryListItem(descr, ItemType.PowerCube, getDrawable(requireContext(), drawable), drawable, cubes, items.get(0).getItemRarity(), level);
                mGroupPowerCubes.add(cube);
            }
        }
        mGroupNames.add("PowerCubes (" + count + ")");
        mGroups.add(mGroupPowerCubes);
    }

    void fillPortalKeys() {
        Inventory inv = mGame.getInventory();


        List<ItemBase> items = inv.getItems(ItemType.PortalKey);
        Set<String> portalGUIDs = new HashSet<>();
        LinkedList<ItemPortalKey> skipItems = new LinkedList<>();
        for (ItemBase item1 : items) {
            ItemPortalKey theItem1 = (ItemPortalKey) item1;
            ArrayList<String> keys = new ArrayList<>();

            // skip items that have already been checked
            if (skipItems.contains(theItem1)) {
                continue;
            }

            String descr = theItem1.getPortalTitle();
//            String descr = theItem1.getUsefulName();
            keys.add(item1.getEntityGuid());

            // check for multiple portal keys with the same portal guid
            for (ItemBase item2 : items) {
                ItemPortalKey theItem2 = (ItemPortalKey) item2;

                // don't check the doubles
                if (theItem2 == theItem1) {
                    continue;
                }

                if (theItem1.getPortalGuid().equals(theItem2.getPortalGuid())) {
                    skipItems.add(theItem2);
                    keys.add(item2.getEntityGuid());
                }
            }

            InventoryListItem key = new InventoryListItem(descr, ItemType.PortalKey, getDrawable(requireContext(), R.drawable.portalkey), R.drawable.portalkey, keys, items.get(0).getItemRarity());
            key.setLocation(new Location(theItem1.getPortalLocation()));
            key.setImage(theItem1.getPortalImageUrl());
            portalGUIDs.add(theItem1.getPortalGuid());
            mGroupPortalKeys.add(key);
        }
        mGroupNames.add("PortalKeys (" + items.size() + ")");
        mGroups.add(mGroupPortalKeys);
        mGame.intGetModifiedEntitiesByGuid(portalGUIDs.toArray(new String[0]), new Handler(msg -> true));
    }

    void fillCapsules() {
        Inventory inv = mGame.getInventory();

        List<ItemBase> items = inv.getItems(ItemType.Capsule);
        for (ItemBase item1 : items) {
            ItemCapsule theItem1 = (ItemCapsule) item1;

            // oof. this is a bit different to all the other types of items - i don't want to collapse them.
            ArrayList<String> capsules = new ArrayList<>();

            String differentiator = theItem1.getDifferentiator();
            capsules.add(theItem1.getEntityGuid());

            InventoryListItem key = new InventoryListItem(differentiator, ItemType.Capsule, getDrawable(requireContext(), R.drawable.capsule), R.drawable.capsule, capsules, items.get(0).getItemRarity());
            mGroupCapsules.add(key);
        }
        mGroupNames.add("Capsules (" + items.size() + ")");
        mGroups.add(mGroupCapsules);
        // might be useful if we nest the capsule stuff in the inventory. think about it later.
//        mGame.intGetModifiedEntitiesByGuid(portalGUIDs.toArray(new String[0]), new Handler(msg -> true));
    }

    void fillOther() {
        Inventory inv = mGame.getInventory();

        List<ItemBase> items = inv.getItems(ItemType.PlayerPowerup);

        ArrayList<String> ids = new ArrayList<>();

        LinkedList<ItemPlayerPowerup> skipItems = new LinkedList<>();
        for (ItemBase item1 : items) {
            ItemPlayerPowerup theItem1 = (ItemPlayerPowerup) item1;

            // skip items that have already been checked
            if (skipItems.contains(theItem1)) {
                continue;
            }

            String differentiator = theItem1.getDisplayName();
            ids.add(theItem1.getEntityGuid());

            // check for multiple powerups with the same type
            for (ItemBase item2 : items) {
                ItemPlayerPowerup theItem2 = (ItemPlayerPowerup) item2;

                // don't check the doubles
                if (theItem2 == theItem1) {
                    continue;
                }

                if (theItem1.getPlayerPowerupType().equals(theItem2.getPlayerPowerupType())) {
                    skipItems.add(theItem2);
                    ids.add(item2.getEntityGuid());
                }
            }

            InventoryListItem key = new InventoryListItem(differentiator, ItemType.PlayerPowerup, getDrawable(requireContext(), R.drawable.dap), R.drawable.dap, ids, items.get(0).getItemRarity());
            mGroupOther.add(key);
        }
        mGroupNames.add("Other (" + items.size() + ")");
        mGroups.add(mGroupOther);
        // might be useful if we nest the capsule stuff in the inventory. think about it later.
//        mGame.intGetModifiedEntitiesByGuid(portalGUIDs.toArray(new String[0]), new Handler(msg -> true));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mObserver != null) {
            mApp.getInventoryViewModel().getInventory().removeObserver(mObserver);
        }
    }
}

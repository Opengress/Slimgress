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

import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_IMAGE_RESOLUTION_NONE;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getImageForResoLevel;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getMainActivity;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.activity.FragmentInventoryItem;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.Inventory;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemPortalKey;

import java.util.ArrayList;
import java.util.Objects;

public class InventoryList extends BaseExpandableListAdapter {
    public final ArrayList<String> mGroupItem;
    public ArrayList<InventoryListItem> mTempChild;
    public final ArrayList<Object> mChildItem;
    public LayoutInflater mInflater;
    public Activity mActivity;
    private final Context mContext;
    private final Inventory mInventory;
    private final GameState mGame;
    private ItemBase.Rarity mRarityFilter = null;
    private int mLevelFilter = -999;
    private String mSearchText = "";

    public InventoryList(Context context, ArrayList<String> grList, ArrayList<Object> childItem) {
        mGroupItem = grList;
        mChildItem = childItem;
        mContext = context;
        mGame = SlimgressApplication.getInstance().getGame();
        mInventory = mGame.getInventory();
    }

    public void setInflater(LayoutInflater inflater, Activity act) {
        mInflater = inflater;
        mActivity = act;
    }

    public void setSearchText(String text) {
        mSearchText = text.toLowerCase();
    }

    public void limitRarities(String rarity) {
        /// "ALL", "Very Common", "Common", "Less Common", "Rare", "Very Rare", "Extra Rare"
        switch (rarity) {
            case "Very Common" -> mRarityFilter = ItemBase.Rarity.VeryCommon;
            case "Common" -> mRarityFilter = ItemBase.Rarity.Common;
            case "Less Common" -> mRarityFilter = ItemBase.Rarity.LessCommon;
            case "Rare" -> mRarityFilter = ItemBase.Rarity.Rare;
            case "Very Rare" -> mRarityFilter = ItemBase.Rarity.VeryRare;
            case "Extra Rare" -> mRarityFilter = ItemBase.Rarity.ExtraRare;
            default ->
                // What is None?
//                mRarityFilter = ItemBase.Rarity.None;
                    mRarityFilter = null;
        }
        notifyDataSetChanged();
    }

    public void limitLevels(String level) {
        switch (level) {
            case "Level 0" ->
                // should never happen because currently nothing is ever 0. portals, tho...
                    mLevelFilter = 0;
            case "Level 1" -> mLevelFilter = 1;
            case "Level 2" -> mLevelFilter = 2;
            case "Level 3" -> mLevelFilter = 3;
            case "Level 4" -> mLevelFilter = 4;
            case "Level 5" -> mLevelFilter = 5;
            case "Level 6" -> mLevelFilter = 6;
            case "Level 7" -> mLevelFilter = 7;
            case "Level 8" -> mLevelFilter = 8;
            default ->
                // again, is this safe?
                    mLevelFilter = -999;
        }
        notifyDataSetChanged();
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0;
    }

    @SuppressLint("DefaultLocale")
    @Override
    @SuppressWarnings("unchecked")
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        mTempChild = (ArrayList<InventoryListItem>) mChildItem.get(groupPosition);
        TextView text;
        ImageView image;
        var item = mTempChild.get(childPosition);

        if (convertView == null) {
            if (item.getType() == ItemBase.ItemType.PortalKey) {
                convertView = mInflater.inflate(R.layout.inventory_childrow_portalkey, parent, false);
            } else {
                convertView = mInflater.inflate(R.layout.inventory_childrow, parent, false);
            }
        } else {
            // Check if the existing convertView is of the correct type
            if (item.getType() == ItemBase.ItemType.PortalKey && convertView.getTag() != ItemBase.ItemType.PortalKey) {
                convertView = mInflater.inflate(R.layout.inventory_childrow_portalkey, parent, false);
            } else if (item.getType() != ItemBase.ItemType.PortalKey && convertView.getTag() == ItemBase.ItemType.PortalKey) {
                convertView = mInflater.inflate(R.layout.inventory_childrow, parent, false);
            }
        }

        // Set the tag to the current item type
        convertView.setTag(item.getType());

        // for the filters...
        int level = item.getLevel();
        String address = "";

        if (item.getType() == ItemBase.ItemType.PortalKey) {

            // set portal address, description and cover image
            text = convertView.findViewById(R.id.inventory_childRow_portalKey_prettyDescription);
            text.setText(item.getPrettyDescription());
            image = convertView.findViewById(R.id.inventory_childRow_portalKey_ChildImage);

            BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
            String desiredResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
            if (Objects.equals(desiredResolution, UNTRANSLATABLE_IMAGE_RESOLUTION_NONE)) {
                Glide.with(mContext)
                        .load(item.getIcon())
                        .into(image);
            } else {
                Glide.with(mContext)
                        .load(item.getImage())
                        .placeholder(R.drawable.no_image)
                        .error(item.getIcon())
                        .into(image);
            }
            ItemPortalKey key = (ItemPortalKey) mInventory.getItems().get(item.getFirstID());
            if (key != null) {
                GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key.getPortalGuid());
                if (portal != null) {
                    address = key.getPortalAddress();
                    ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_address)).setText(address);
                    level = portal.getPortalLevel();

                    // show portal level
                    ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_level)).setText(String.format("L%d", portal.getPortalLevel()));
                    int levelColour = getLevelColour(portal.getPortalLevel());
                    ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_level)).setTextColor(getColourFromResources(convertView.getResources(), levelColour));


                    // get distance to portal and show ownership in the colours
                    if (Objects.equals(portal.getOwnerGuid(), mGame.getAgent().getEntityGuid())) {
                        text.setTextColor(0xFFFCD452);
                    } else {
                        text.setTextColor(0xFFFFFFFF);
                    }
                    ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_distance)).setTextColor(0xff000000 + portal.getPortalTeam().getColour());
                    int dist = 999999000;
                    Location loc = mGame.getLocation();
                    if (loc != null) {
                        dist = (int) (mGame.getLocation().distanceTo(item.getLocation()));
                    }
                    String distanceText = getPrettyDistanceString(dist);
                    ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_distance)).setText(distanceText);

                    // set up resonator graphics
                    // Clear existing images
                    int[] resoImageIds = {
                            R.id.inventory_childRow_portalKey_r1Image,
                            R.id.inventory_childRow_portalKey_r2Image,
                            R.id.inventory_childRow_portalKey_r3Image,
                            R.id.inventory_childRow_portalKey_r4Image,
                            R.id.inventory_childRow_portalKey_r5Image,
                            R.id.inventory_childRow_portalKey_r6Image,
                            R.id.inventory_childRow_portalKey_r7Image,
                            R.id.inventory_childRow_portalKey_r8Image
                    };

                    for (int resId : resoImageIds) {
                        ((ImageView) convertView.findViewById(resId)).setImageResource(R.drawable.no_image);
                        ((ImageView) convertView.findViewById(resId)).setImageAlpha(255);
                    }

                    // add nice, fresh images
                    for (var reso : portal.getPortalResonators()) {
                        if (reso == null) {
                            continue;
                        }
                        int resId = resoImageIds[reso.slot];
                        ((ImageView) convertView.findViewById(resId)).setImageResource(getImageForResoLevel(reso.level));
                        int alpha = (int) (((float) reso.energyTotal / reso.getMaxEnergy()) * 255); // Convert percentage to alpha value (0-255)
                        ((ImageView) convertView.findViewById(resId)).setImageAlpha(alpha);
                    }
                }
            }

        } else {
            text = convertView.findViewById(R.id.inventory_childRow_prettyDescription);
            text.setText(item.getPrettyDescription());
            image = convertView.findViewById(R.id.inventory_childRow_childImage);
            image.setImageDrawable(item.getIcon());
        }
        convertView.setOnClickListener(v -> {
            FragmentTransaction transaction = getMainActivity().getSupportFragmentManager().beginTransaction();
            // I'm mildly surprised that worked. This should all go into the activity, really
            transaction.add(R.id.fragment_container, FragmentInventoryItem.newInstance(item), "INVENTORY_ITEM");
            transaction.addToBackStack("INVENTORY_ITEM");
            transaction.commit();
        });


        var params = convertView.getLayoutParams();
        params.height = 0;
        if (mLevelFilter > -999) {
            if (level != mLevelFilter) {
                params.height = 1;
            }
        }
        if (mRarityFilter != null) {
            if (item.getRarity() != mRarityFilter) {
                params.height = 1;
            }
        }
        if (!Objects.equals(mSearchText, "")) {
            if (!item.getPrettyDescription().toLowerCase().contains(mSearchText) &&
                    !Objects.equals(address, "") &&
                    !address.toLowerCase().contains(mSearchText)) {
                params.height = 1;
            }
        }
        convertView.setLayoutParams(params);

        return convertView;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int getChildrenCount(int groupPosition) {
        return ((ArrayList<String>) mChildItem.get(groupPosition)).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return null;
    }

    @Override
    public int getGroupCount() {
        return mGroupItem.size();
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
        super.onGroupCollapsed(groupPosition);
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return 0;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.inventory_grouprow, parent, false);
        }
        ((CheckedTextView) convertView).setText(mGroupItem.get(groupPosition));
        ((CheckedTextView) convertView).setChecked(isExpanded);
        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }
}

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

import static net.opengress.slimgress.API.Common.Utils.getImageForResoLevel;
import static net.opengress.slimgress.API.Common.Utils.getLevelColor;
import static net.opengress.slimgress.API.Common.Utils.getPrettyDistanceString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.Inventory;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemBase;
import net.opengress.slimgress.API.Item.ItemPortalKey;

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
        mGame = IngressApplication.getInstance().getGame();
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
            case "ALL":
            default:
                // What is None?
//                mRarityFilter = ItemBase.Rarity.None;
                mRarityFilter = null;
                break;
            case "Very Common":
                mRarityFilter = ItemBase.Rarity.VeryCommon;
                break;
            case "Common":
                mRarityFilter = ItemBase.Rarity.Common;
                break;
            case "Less Common":
                mRarityFilter = ItemBase.Rarity.LessCommon;
                break;
            case "Rare":
                mRarityFilter = ItemBase.Rarity.Rare;
                break;
            case "Very Rare":
                mRarityFilter = ItemBase.Rarity.VeryRare;
                break;
            case "Extra Rare":
                mRarityFilter = ItemBase.Rarity.ExtraRare;
                break;
        }
        notifyDataSetChanged();
    }

    public void limitLevels(String level) {
        switch (level) {
            case "ALL":
            default:
                // again, is this safe?
                mLevelFilter = -999;
                break;
            case "Level 0":
                // should never happen because currently nothing is ever 0. portals, tho...
                mLevelFilter = 0;
                break;
            case "Level 1":
                mLevelFilter = 1;
                break;
            case "Level 2":
                mLevelFilter = 2;
                break;
            case "Level 3":
                mLevelFilter = 3;
                break;
            case "Level 4":
                mLevelFilter = 4;
                break;
            case "Level 5":
                mLevelFilter = 5;
                break;
            case "Level 6":
                mLevelFilter = 6;
                break;
            case "Level 7":
                mLevelFilter = 7;
                break;
            case "Level 8":
                mLevelFilter = 8;
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

            Glide.with(mContext)
                    .load(item.getImage())
                    .placeholder(R.drawable.no_image)
                    .error(item.getIcon())
                    .into(image);
            ItemPortalKey key = (ItemPortalKey) mInventory.getItems().get(item.getFirstID());
            assert key != null;
            GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(key.getPortalGuid());
            assert portal != null;
            address = key.getPortalAddress();
            ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_address)).setText(address);
            level = portal.getPortalLevel();

            // show portal level
            ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_level)).setText(String.format("L%d", portal.getPortalLevel()));
            int levelColour = getLevelColor(portal.getPortalLevel());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_level)).setTextColor(convertView.getResources().getColor(levelColour, null));
            } else {
                ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_level)).setTextColor(convertView.getResources().getColor(levelColour));
            }

            // get distance to portal and show ownership in the colour
            ((TextView) convertView.findViewById(R.id.inventory_childRow_portalKey_distance)).setTextColor(0xff000000 + portal.getPortalTeam().getColour());
            int dist = 999999000;
            Location loc = mGame.getLocation();
            if (loc != null) {
                dist = (int) (mGame.getLocation().getS2LatLng().getEarthDistance(item.getLocation()));
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
                int resId = resoImageIds[reso.slot - 1];
                ((ImageView) convertView.findViewById(resId)).setImageResource(getImageForResoLevel(reso.level));
                int alpha = (int) (((float) reso.energyTotal / reso.getMaxEnergy()) * 255); // Convert percentage to alpha value (0-255)
                ((ImageView) convertView.findViewById(resId)).setImageAlpha(alpha);
            }

        } else {
            text = convertView.findViewById(R.id.inventory_childRow_prettyDescription);
            text.setText(item.getPrettyDescription());
            image = convertView.findViewById(R.id.inventory_childRow_childImage);
            image.setImageDrawable(item.getIcon());
        }
        convertView.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, ActivityInventoryItem.class);
            intent.putExtra("item", item);
            mContext.startActivity(intent);
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

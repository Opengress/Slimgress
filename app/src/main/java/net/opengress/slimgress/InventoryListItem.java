package net.opengress.slimgress;

import android.graphics.drawable.Drawable;

import com.google.common.geometry.S2LatLng;

import net.opengress.slimgress.API.Item.ItemBase.ItemType;
import net.opengress.slimgress.API.Item.ItemBase.Rarity;
import net.opengress.slimgress.API.Item.ItemFlipCard.FlipCardType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

public class InventoryListItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private String mDescription;
    private ItemType mType;
    private transient Drawable mIcon;
    private int mIconID;
    private String mImage;
    private final ArrayList<String> mIDs;
    private FlipCardType mFlipCardType;
    private Rarity mRarity = Rarity.None;
    private transient S2LatLng mLocation;
    private String mSerializableLocation;
    private int mLevel = -999;

    public InventoryListItem(String description, ItemType type, Drawable icon, int iconID, ArrayList<String> IDs) {
        mDescription = description;
        mType = type;
        mIcon = icon;
        mIDs = IDs;
        mIconID = iconID;
    }

    public InventoryListItem(String description, ItemType type, Drawable icon, int iconID, ArrayList<String> IDs, Rarity rarity) {
        mDescription = description;
        mType = type;
        mIcon = icon;
        mIDs = IDs;
        mIconID = iconID;
        mRarity = rarity;
    }

    public InventoryListItem(String description, ItemType type, Drawable icon, int iconID, ArrayList<String> IDs, Rarity rarity, int level) {
        mDescription = description;
        mType = type;
        mIcon = icon;
        mIDs = IDs;
        mIconID = iconID;
        mRarity = rarity;
        mLevel = level;
    }

    public String getFirstID() {
        // would it be faster if i get the last?
        return mIDs.get(0);
    }

    public String pop() {
        // would it be faster if i get the last?
        var item = mIDs.get(0);
        mIDs.remove(0);
        return item;
    }

    public ArrayList<String> getAllIDs() {
        return mIDs;
    }

    public void clear() {
        // might be wiser to accept a list of IDs and then remove all those IDs, because sync/race
        mIDs.clear();
    }

    public void add(String ID) {
        mIDs.add(ID);
    }

    public void remove(String ID) {
        mIDs.remove(ID);
    }

    public int getQuantity() {
        return mIDs.size();
    }

    public String getPrettyDescription() {
        if (getQuantity() == 1) {
            return mDescription;
        }
        return mDescription + " (" + mIDs.size() + ")";
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public void setIcon(Drawable image) {
        mIcon = image;
    }

    public int getIconID() {
        return mIconID;
    }

    public void setIconID(int resID) {
        mIconID = resID;
    }

    public ItemType getType() {
        return mType;
    }

    public void setType(ItemType type) {
        mType = type;
    }

    public FlipCardType getFlipCardType() {
        return mFlipCardType;
    }

    public void setFlipCardType(FlipCardType flipCardType) {
        mFlipCardType = flipCardType;
    }

    public void setLocation(S2LatLng loc) {
        mLocation = loc;
    }

    public S2LatLng getLocation() {
        if (mLocation == null && mSerializableLocation != null) {
            String[] lls = mSerializableLocation.substring(1, mSerializableLocation.length() - 1).split(",");
            mLocation = S2LatLng.fromDegrees(Double.parseDouble(lls[0]), Double.parseDouble(lls[1]));
        }
        return mLocation;
    }

    public double getDistance(S2LatLng playerLocation) {
        return mLocation.getEarthDistance(playerLocation);
    }

    public Rarity getRarity() {
        return mRarity;
    }

    public void setRarity(Rarity rarity) {
        mRarity = rarity;
    }

    public String getImage() {
        return mImage;
    }

    public void setImage(String image) {
        mImage = image;
    }

    public int getLevel() {
        return mLevel;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (mLocation != null) {
            mSerializableLocation = mLocation.toStringDegrees();
        }

        // Default serialization
        oos.defaultWriteObject();
    }
}

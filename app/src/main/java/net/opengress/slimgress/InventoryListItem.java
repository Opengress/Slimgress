package net.opengress.slimgress;

import android.graphics.drawable.Drawable;

import com.google.common.geometry.S2LatLng;

import net.opengress.slimgress.API.Item.ItemBase.Rarity;
import net.opengress.slimgress.API.Item.ItemBase.ItemType;
import net.opengress.slimgress.API.Item.ItemFlipCard.FlipCardType;

import java.util.ArrayList;

public class InventoryListItem {
    private String mDescription;
    private ItemType mType;
    private Drawable mIcon;
    private String mImage;
    private final ArrayList<String> mIDs;
    private FlipCardType mFlipCardType;
    private Rarity mRarity = Rarity.None;
    private S2LatLng mLocation;

    public InventoryListItem(String description, ItemType type, Drawable icon, ArrayList<String> IDs) {
        this.mDescription = description;
        this.mType = type;
        this.mIcon = icon;
        this.mIDs = IDs;
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
        this.mDescription = description;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public void setIcon(Drawable image) {
        this.mIcon = image;
    }

    public ItemType getType() {
        return mType;
    }

    public void setType(ItemType type) {
        this.mType = type;
    }

    public FlipCardType getFlipCardType() {
        return mFlipCardType;
    }

    public void setFlipCardType(FlipCardType flipCardType) {
        this.mFlipCardType = flipCardType;
    }

    public void setLocation(S2LatLng loc) {
        this.mLocation = loc;
    }

    public S2LatLng getLocation() {
        return mLocation;
    }

    public double getDistance(S2LatLng playerLocation) {
        return mLocation.getEarthDistance(playerLocation);
    }

    public Rarity getRarity() {
        return mRarity;
    }

    public void setRarity(Rarity rarity) {
        this.mRarity = rarity;
    }

    public String getImage() {
        return mImage;
    }

    public void setImage(String mImage) {
        this.mImage = mImage;
    }
}

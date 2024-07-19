package net.opengress.slimgress;

import net.opengress.slimgress.API.Item.ItemBase.ItemType;
import net.opengress.slimgress.API.Item.ItemFlipCard.FlipCardType;

import java.util.ArrayList;

public class InventoryListItem {
    private String mDescription;
    private ItemType mType;
    private String mImage;
    private final ArrayList<String> mIDs;
    private FlipCardType mFlipCardType;

    public InventoryListItem(String description, ItemType type, String image, ArrayList<String> IDs) {
        this.mDescription = description;
        this.mType = type;
        this.mImage = image;
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

    public String getImage() {
        return mImage;
    }

    public void setImage(String image) {
        this.mImage = image;
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
}

package net.opengress.slimgress.api.Item;

public class ModKey implements Comparable<ModKey> {
    private final ItemBase.ItemType type;
    public final ItemBase.Rarity rarity;
    public final String modDisplayName;

    public ModKey(ItemBase.ItemType itemType, ItemBase.Rarity rarity, String modDisplayName) {
        this.type = itemType;
        this.rarity = rarity;
        this.modDisplayName = modDisplayName;
    }

    // For use in HashMap
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModKey modKey = (ModKey) o;
        if (rarity != modKey.rarity) {
            return false;
        }
        return modDisplayName.equals(modKey.modDisplayName);
    }

    @Override
    public int hashCode() {
        int result = rarity.hashCode();
        result = 31 * result + modDisplayName.hashCode();
        return result;
    }

    @Override
    public int compareTo(ModKey other) {

        int compare = type.compareTo(other.type);
        if (compare != 0) {
            return compare;
        }

        compare = rarity.compareTo(other.rarity);
        if (compare != 0) {
            return compare;
        }

        return this.modDisplayName.compareTo(other.modDisplayName);
    }
}

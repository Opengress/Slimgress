package net.opengress.slimgress.api.Item;

public class ModKey implements Comparable<ModKey> {
    public ItemBase.Rarity rarity;
    public String modDisplayName;

    public ModKey(ItemBase.Rarity rarity, String modDisplayName) {
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
        // First, compare rarity based on assigned values
        int rarityCompare = rarity.compareTo(other.rarity);
        if (rarityCompare != 0) {
            return rarityCompare;
        }
        // If rarities are the same, compare modDisplayName
        return this.modDisplayName.compareTo(other.modDisplayName);
    }
}

package net.opengress.slimgress;

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;

public class BlankTileSource extends OnlineTileSourceBase {
    public BlankTileSource() {
        super("Blank", 0, 19, 256, ".png", new String[]{});
    }

    @Override
    public String getTileURLString(long pMapTileIndex) {
        return "";
    }
}

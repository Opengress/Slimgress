package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getImageBitmap;
import static net.opengress.slimgress.API.Common.Utils.getLevelColor;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.Inventory;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemBase;
import net.opengress.slimgress.API.Item.ItemPortalKey;

public class ActivityInventoryItem extends AppCompatActivity {

    private GameState mGame;
    private Inventory mInventory;

    @SuppressLint("DefaultLocale")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_item);
        // Get the item from the intent
        InventoryListItem item = (InventoryListItem) getIntent().getSerializableExtra("item");

        mGame = IngressApplication.getInstance().getGame();
        mInventory = mGame.getInventory();

        TextView itemName = findViewById(R.id.activity_inventory_item_name);
        TextView itemDescription = findViewById(R.id.activity_inventory_item_description);
        TextView itemLevel = findViewById(R.id.activity_inventory_item_level);

        // Set the item details
        assert item != null;
        ItemBase.ItemType type = item.getType();
        var actual = mInventory.getItems().get(item.getFirstID());
        assert actual != null;

        int levelColour;

        // handle items with levels etc differently? need to check old stuff...
        switch (type) {
            case PortalKey:
                ((TextView) findViewById(R.id.activity_inventory_item_title)).setText(R.string.item_name_portal_key);
                GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(((ItemPortalKey) actual).getPortalGuid());
                assert portal != null;
                itemName.setText(item.getPrettyDescription());
                itemDescription.setText(((ItemPortalKey) actual).getPortalAddress());
                itemLevel.setText(String.format("L%d", portal.getPortalLevel()));
                levelColour = getLevelColor(portal.getPortalLevel());
                itemLevel.setTextColor(getResources().getColor(levelColour, null));

                break;
            case Media:
            case ModForceAmp:
            case ModHeatsink:
            case ModLinkAmp:
            case ModMultihack:
            case ModShield:
            case ModTurret:
            case PowerCube:
            case Resonator:
            case FlipCard:
            case WeaponXMP:
            case WeaponUltraStrike:
            default:
                itemName.setText(item.getPrettyDescription());
                itemDescription.setText(item.getDescription());
                itemLevel.setText(String.format("L%d", actual.getItemLevel()));
                levelColour = getLevelColor(actual.getItemLevel());
                itemLevel.setTextColor(getResources().getColor(levelColour, null));
        }


        String url = item.getImage();
        if (url == null) {
            if (item.getIcon() == null) {
                ((ImageView) findViewById(R.id.activity_inventory_item_image)).setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), item.getIconID()));
            } else {
                ((ImageView) findViewById(R.id.activity_inventory_item_image)).setImageDrawable(item.getIcon());
            }
        } else {
            new Thread(() -> {
                Bitmap mBitmap = getImageBitmap(url);
                if (mBitmap != null) {
                    runOnUiThread(() -> ((ImageView) findViewById(R.id.activity_inventory_item_image)).setImageBitmap(mBitmap));
                }
            }).start();
        }
    }
}

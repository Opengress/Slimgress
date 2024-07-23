package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getImageBitmap;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.Inventory;
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

        TextView itemName = findViewById(R.id.item_name);
        TextView itemDescription = findViewById(R.id.item_description);
        TextView itemLevel = findViewById(R.id.item_level);

        // Set the item details
        assert item != null;
        ItemBase.ItemType type = item.getType();
        var actual = mInventory.getItems().get(item.getFirstID());
        assert actual != null;

        switch (type) {
            case PortalKey:
                itemName.setText(item.getPrettyDescription());
                itemDescription.setText(((ItemPortalKey) actual).getPortalAddress());
                itemLevel.setText(String.format("L%d", actual.getItemLevel()));
                break;
            default:
                itemName.setText(item.getPrettyDescription());
                itemDescription.setText(item.getDescription());
                itemLevel.setText(String.format("L%d", actual.getItemLevel()));
        }



        String url = item.getImage();
        if (url == null) {
            if (item.getIcon() == null) {
                ((ImageView) findViewById(R.id.item_image)).setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), item.getIconID()));
            } else {
                ((ImageView) findViewById(R.id.item_image)).setImageDrawable(item.getIcon());
            }
        } else {
            new Thread(() -> {
                Bitmap mBitmap = getImageBitmap(url);
                if (mBitmap != null) {
                    runOnUiThread(() -> ((ImageView) findViewById(R.id.item_image)).setImageBitmap(mBitmap));
                }
            }).start();
        }
    }
}

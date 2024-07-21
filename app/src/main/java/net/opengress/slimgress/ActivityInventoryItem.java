package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getImageBitmap;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

public class ActivityInventoryItem extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_item);

        // Get the item from the intent
        InventoryListItem item = (InventoryListItem) getIntent().getSerializableExtra("item");

        TextView itemName = findViewById(R.id.item_name);
        TextView itemDescription = findViewById(R.id.item_description);

        // Set the item details
        assert item != null;
        itemName.setText(item.getPrettyDescription());
        itemDescription.setText(item.getDescription());

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
//        itemImage.setImageResource(item.getImageResource());

        // Add more complex logic here
    }
}

package net.opengress.slimgress;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ActivityInventoryItem extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_item);

        // Get the item from the intent
        InventoryListItem item = (InventoryListItem) getIntent().getSerializableExtra("item");

        ImageView itemImage = findViewById(R.id.item_image);
        TextView itemName = findViewById(R.id.item_name);
        TextView itemDescription = findViewById(R.id.item_description);

        // Set the item details
        itemName.setText(item.getPrettyDescription());
        itemDescription.setText(item.getDescription());
//        itemImage.setImageResource(item.getImageResource());

        // Add more complex logic here
    }
}

package net.opengress.slimgress;

import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;
import static net.opengress.slimgress.ViewHelpers.getRarityColor;
import static net.opengress.slimgress.ViewHelpers.getRarityText;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.Inventory;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemBase;
import net.opengress.slimgress.API.Item.ItemPortalKey;

public class ActivityInventoryItem extends AppCompatActivity {

    private GameState mGame;
    private Inventory mInventory;
    private TextView mItemTitle;
    private TextView mItemRarity;
    private TextView mItemName;
    private TextView mItemDescription;
    private TextView mItemLevel;
    private int mLevelColour;
    private int mRarityColor;
    private InventoryListItem mItem;

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_item);
        // Get the item from the intent
        mItem = (InventoryListItem) getIntent().getSerializableExtra("item");

        mGame = IngressApplication.getInstance().getGame();
        mInventory = mGame.getInventory();

        mItemTitle = findViewById(R.id.activity_inventory_item_title);
        mItemRarity = findViewById(R.id.activity_inventory_item_rarity);
        mItemName = findViewById(R.id.activity_inventory_item_name);
        mItemDescription = findViewById(R.id.activity_inventory_item_description);
        mItemLevel = findViewById(R.id.activity_inventory_item_level);

        // Set the item details
        assert mItem != null;
        ItemBase.ItemType type = mItem.getType();
        var actual = mInventory.getItems().get(mItem.getFirstID());
        assert actual != null;

        // handle items with levels etc differently? need to check old stuff...
        switch (type) {
            case PortalKey:
                GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(((ItemPortalKey) actual).getPortalGuid());
                assert portal != null;

                mItemRarity.setVisibility(View.GONE);

                // Portal Key L1
                mItemTitle.setText(R.string.item_name_portal_key);
                mItemLevel.setText(String.format("L%d", portal.getPortalLevel()));
                mLevelColour = getLevelColor(portal.getPortalLevel());
                mItemLevel.setTextColor(getColorFromResources(getResources(), mLevelColour));


                // bonanza at south beach
                mItemName.setText(mItem.getPrettyDescription());
                mItemName.setTextColor(0xFF000000 + portal.getPortalTeam().getColour());
                mItemName.setVisibility(View.VISIBLE);

                // 12.4km
                updateDistance();
                findViewById(R.id.activity_inventory_item_distance).setVisibility(View.VISIBLE);
                IngressApplication.getInstance().getLocationViewModel().getLocationData().observe(this, unused -> updateDistance());

                // Domain Terrace, Karoro
                ((TextView) findViewById(R.id.activity_inventory_item_address)).setText(((ItemPortalKey) actual).getPortalAddress());
                findViewById(R.id.activity_inventory_item_address).setVisibility(View.VISIBLE);

//                itemDescription.setText("Use to create links and remote recharge this Portal");

                findViewById(R.id.activity_inventory_item_recharge).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_recharge).setEnabled(false);

                break;
            case Resonator:
                mItemTitle.setText("Resonator");
                mItemDescription.setText("XM object used to power up a portal and align it to a faction");
                inflateResourceWithLevels(mItem, actual);
                break;
            case PowerCube:
                mItemTitle.setText("Power Cube");
                mItemDescription.setText("Store of XM which can be used to recharge Scanner");
                inflateResourceWithLevels(mItem, actual);
                findViewById(R.id.activity_inventory_item_use).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_use).setEnabled(false);
                break;
            case WeaponXMP:
                mItemTitle.setText("XMP Burster");
                mItemDescription.setText("Exotic Matter Pulse weapons which can destroy enemy resonators and Mods and neutralize enemy portals");
                inflateResourceWithLevels(mItem, actual);
                findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_fire).setEnabled(false);
                break;
            case WeaponUltraStrike:
                mItemTitle.setText("Ultra Strike");
                mItemDescription.setText("A variation of the Exotic Matter Pulse weapon with a more powerful blast that occurs within a smaller radius");
                inflateResourceWithLevels(mItem, actual);
                findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_fire).setEnabled(false);
                break;
            case Media:
            case ModForceAmp:
            case ModHeatsink:
            case ModLinkAmp:
            case ModMultihack:
            case ModShield:
            case ModTurret:
            case FlipCard:
            default:
                mItemName.setText(mItem.getPrettyDescription());
                mItemDescription.setText(mItem.getDescription());
                mItemLevel.setText(String.format("L%d", actual.getItemLevel()));
                mLevelColour = getLevelColor(actual.getItemLevel());
                mItemLevel.setTextColor(getColorFromResources(getResources(), mLevelColour));
        }


        String url = mItem.getImage();
        if (url == null) {
            if (mItem.getIcon() == null) {
                ((ImageView) findViewById(R.id.activity_inventory_item_image)).setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), mItem.getIconID()));
            } else {
                ((ImageView) findViewById(R.id.activity_inventory_item_image)).setImageDrawable(mItem.getIcon());
            }
        } else {
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.no_image)
                    .error(R.drawable.no_image)
                    .into((ImageView) findViewById(R.id.activity_inventory_item_image));
        }
    }

    private void updateDistance() {
        int dist = 999999000;
        Location loc = mGame.getLocation();
        if (loc != null) {
            dist = (int) (mGame.getLocation().getS2LatLng().getEarthDistance(mItem.getLocation()));
        }
        ((TextView) findViewById(R.id.activity_inventory_item_distance)).setText(getPrettyDistanceString(dist));
    }

    @SuppressLint("DefaultLocale")
    private void inflateResourceWithLevels(InventoryListItem item, ItemBase actual) {
        mItemRarity.setText(getRarityText(item.getRarity()));
        mRarityColor = getRarityColor(item.getRarity());
        mItemRarity.setTextColor(getColorFromResources(getResources(), mRarityColor));
        mItemLevel.setText(String.format("L%d", actual.getItemLevel()));
        mLevelColour = getLevelColor(actual.getItemLevel());
        mItemLevel.setTextColor(getColorFromResources(getResources(), mLevelColour));
    }

}

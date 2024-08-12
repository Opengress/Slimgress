package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getErrorStringFromAPI;
import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;
import static net.opengress.slimgress.ViewHelpers.getRarityColor;
import static net.opengress.slimgress.ViewHelpers.getRarityText;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.Objects;

public class ActivityInventoryItem extends AppCompatActivity {

    private GameState mGame;
    private Inventory mInventory;
    private TextView mItemRarity;
    private TextView mItemLevel;
    private int mLevelColour;
    private InventoryListItem mItem;

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_item);
        // Get the item from the intent
        mItem = (InventoryListItem) getIntent().getSerializableExtra("item");

        mGame = SlimgressApplication.getInstance().getGame();
        mInventory = mGame.getInventory();

        TextView itemTitle = findViewById(R.id.activity_inventory_item_title);
        mItemRarity = findViewById(R.id.activity_inventory_item_rarity);
        TextView itemName = findViewById(R.id.activity_inventory_item_name);
        TextView itemDescription = findViewById(R.id.activity_inventory_item_description);
        mItemLevel = findViewById(R.id.activity_inventory_item_level);

        // Set the item details
        assert mItem != null;
        ItemBase.ItemType type = mItem.getType();
        var actual = mInventory.getItems().get(mItem.getFirstID());
        assert actual != null;

        // handle items with levels etc differently? need to check old stuff...
        switch (type) {
            case PortalKey -> {
                GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(((ItemPortalKey) actual).getPortalGuid());
                assert portal != null;

                mItemRarity.setVisibility(View.GONE);

                // Portal Key L1
                itemTitle.setText(R.string.item_name_portal_key);
                mItemLevel.setText(String.format("L%d", portal.getPortalLevel()));
                mLevelColour = getLevelColor(portal.getPortalLevel());
                mItemLevel.setTextColor(getColorFromResources(getResources(), mLevelColour));


                // bonanza at south beach
                itemName.setText(mItem.getPrettyDescription());
                itemName.setTextColor(0xFF000000 + portal.getPortalTeam().getColour());
                itemName.setVisibility(View.VISIBLE);

                // 12.4km
                updateDistance();
                findViewById(R.id.activity_inventory_item_distance).setVisibility(View.VISIBLE);
                SlimgressApplication.getInstance().getLocationViewModel().getLocationData().observe(this, unused -> updateDistance());

                // Domain Terrace, Karoro
                ((TextView) findViewById(R.id.activity_inventory_item_address)).setText(((ItemPortalKey) actual).getPortalAddress());
                findViewById(R.id.activity_inventory_item_address).setVisibility(View.VISIBLE);

//                itemDescription.setText("Use to create links and remote recharge this Portal");

                findViewById(R.id.activity_inventory_item_recharge).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_recharge).setEnabled(false);
            }
            case Resonator -> {
                itemTitle.setText("Resonator");
                itemDescription.setText("XM object used to power up a portal and align it to a faction");
                inflateResource(mItem, actual);
            }
            case PowerCube -> {
                itemTitle.setText("Power Cube");
                itemDescription.setText("Store of XM which can be used to recharge Scanner");
                inflateResource(mItem, actual);
                findViewById(R.id.activity_inventory_item_use).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_use).setEnabled(false);
            }
            case WeaponXMP -> {
                itemTitle.setText("XMP Burster");
                itemDescription.setText("Exotic Matter Pulse weapons which can destroy enemy resonators and Mods and neutralize enemy portals");
                inflateResource(mItem, actual);
                findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_fire).setEnabled(false);
            }
            case WeaponUltraStrike -> {
                itemTitle.setText("Ultra Strike");
                itemDescription.setText("A variation of the Exotic Matter Pulse weapon with a more powerful blast that occurs within a smaller radius");
                inflateResource(mItem, actual);
                findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_fire).setEnabled(false);
            }
            case ModShield -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Mod which shields Portal from attacks.");
                inflateResource(mItem, actual);
            }
            case ModMultihack -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Mod that increases hacking capacity of a Portal.");
                inflateResource(mItem, actual);
            }
            case ModHeatsink -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Mod that reduces cooldown time between Portal hacks.");
                inflateResource(mItem, actual);
            }
            case ModForceAmp -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Mod that increases power of Portal attacks against enemy agents.");
                inflateResource(mItem, actual);
            }
            case ModTurret -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Mod that increases frequency of Portal attacks against enemy agents.");
                inflateResource(mItem, actual);
            }
            case ModLinkAmp -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Mod that increases Portal link range.");
                inflateResource(mItem, actual);
            }
            case Capsule -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Object which can hold other objects.");
                inflateResource(mItem, actual);
            }
            case PlayerPowerup -> {
                itemTitle.setText(actual.getDisplayName());
                itemDescription.setText("Player Powerup which doubles AP for thirty minutes.");
                inflateResource(mItem, actual);
            }
            default -> {
                itemName.setText(mItem.getPrettyDescription());
                itemDescription.setText(mItem.getDescription());
                mItemLevel.setText(String.format("L%d", actual.getItemLevel()));
                mLevelColour = getLevelColor(actual.getItemLevel());
                mItemLevel.setTextColor(getColorFromResources(getResources(), mLevelColour));
            }
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

        findViewById(R.id.activity_inventory_item_drop).setEnabled(true);
        findViewById(R.id.activity_inventory_item_drop).setOnClickListener(this::onDropItemClicked);
        if (mGame.getKnobs().getClientFeatureKnobs().isEnableRecycle()) {
            findViewById(R.id.activity_inventory_item_recycle).setEnabled(true);
            findViewById(R.id.activity_inventory_item_recycle).setOnClickListener(this::onRecycleItemClicked);
        } else {
            findViewById(R.id.activity_inventory_item_recycle).setVisibility(View.INVISIBLE);
        }

    }

    private void updateDistance() {
        int dist = 999999000;
        Location loc = mGame.getLocation();
        if (loc != null) {
            // crashes on next line if there aren't exclusions in proguard config
            dist = (int) (loc.getS2LatLng().getEarthDistance(mItem.getLocation()));
        }
        ((TextView) findViewById(R.id.activity_inventory_item_distance)).setText(getPrettyDistanceString(dist));
    }

    @SuppressLint("DefaultLocale")
    private void inflateResource(InventoryListItem item, ItemBase actual) {
        mItemRarity.setText(getRarityText(item.getRarity()));
        int rarityColor = getRarityColor(item.getRarity());
        mItemRarity.setTextColor(getColorFromResources(getResources(), rarityColor));
        if (actual.getItemLevel() > 0) {
            mItemLevel.setText(String.format("L%d", actual.getItemLevel()));
            mLevelColour = getLevelColor(actual.getItemLevel());
            mItemLevel.setTextColor(getColorFromResources(getResources(), mLevelColour));
        }
    }

    private void onDropItemClicked(View v) {

        AlertDialog.Builder builder = new AlertDialog.Builder(ActivityInventoryItem.this);
        builder.setTitle("DROP: " + mItem.getDescription());
        builder.setMessage(String.format("This %s will be removed from your inventory and you will not be able to see it for a VERY LONG TIME!", mItem.getDescription()));

        builder.setPositiveButton(R.string.ok, (d, which) -> {
            mGame.intDropItem(Objects.requireNonNull(mInventory.getItems().get(mItem.getFirstID())), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                // FIXME why is my error text funny?
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                } else {
                    for (var id : Objects.requireNonNull(data.getStringArray("dropped"))) {
                        mItem.remove(id);
                        mInventory.removeItem(id);
                    }
                    if (mItem.getQuantity() == 0) {
                        new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
                    }
                }
                return false;
            }));
            d.dismiss();
        });

        builder.setNegativeButton(R.string.cancel, (d, which) -> {
            d.dismiss();
        });

        AlertDialog d = builder.create();
        d.show();
    }

    private void onRecycleItemClicked(View v) {
        if (mGame.getKnobs().getClientFeatureKnobs().isEnableRecycleConfirmationDialog()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ActivityInventoryItem.this);
            builder.setTitle("RECYCLE: " + mItem.getDescription());
            builder.setMessage(String.format("This %s will be destroyed. This cannot be undone!", mItem.getDescription()));

            builder.setPositiveButton(R.string.ok, (d, which) -> {
                recycleItem();
                d.dismiss();
            });

            builder.setNegativeButton(R.string.cancel, (d, which) -> {
                d.dismiss();
            });

            AlertDialog d = builder.create();
            d.show();
        } else {
            recycleItem();
        }
    }

    private void recycleItem() {
        mGame.intRecycleItem(Objects.requireNonNull(mInventory.getItems().get(mItem.getFirstID())), new Handler(msg -> {
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            // FIXME why is my error text funny?
            if (error != null && !error.isEmpty()) {
                DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                dialog.setMessage(error).setDismissDelay(1500).show();
            } else {
                var res = data.getString("result");
                DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                dialog.setMessage(String.format("Gained %s XM from recycling a %s", res, mItem.getDescription())).setDismissDelay(1500).show();
                for (var id : Objects.requireNonNull(data.getStringArray("recycled"))) {
                    mItem.remove(id);
                    mInventory.removeItem(id);
                }
                if (mItem.getQuantity() == 0) {
                    new Handler(Looper.getMainLooper()).postDelayed(this::finish, 1500);
                }
            }
            return false;
        }));
    }
}

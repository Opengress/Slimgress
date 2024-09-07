package net.opengress.slimgress.activity;

import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
import static net.opengress.slimgress.ViewHelpers.getMainActivity;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;
import static net.opengress.slimgress.ViewHelpers.getRarityColor;
import static net.opengress.slimgress.ViewHelpers.getRarityText;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.InventoryListItem;
import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.Inventory;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemFlipCard;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.api.Item.ItemPowerCube;
import net.opengress.slimgress.dialog.DialogInfo;

import java.text.MessageFormat;
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
                findViewById(R.id.activity_inventory_item_use).setEnabled(true);
                findViewById(R.id.activity_inventory_item_use).setOnClickListener(this::onUseItemClicked);
            }
            case WeaponXMP -> {
                itemTitle.setText("XMP Burster");
                itemDescription.setText("Exotic Matter Pulse weapons which can destroy enemy resonators and Mods and neutralize enemy portals");
                inflateResource(mItem, actual);
                findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_fire).setEnabled(true);
                findViewById(R.id.activity_inventory_item_fire).setOnClickListener(this::onFireClicked);
            }
            case WeaponUltraStrike -> {
                itemTitle.setText("Ultra Strike");
                itemDescription.setText("A variation of the Exotic Matter Pulse weapon with a more powerful blast that occurs within a smaller radius");
                inflateResource(mItem, actual);
                findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_fire).setEnabled(true);
                findViewById(R.id.activity_inventory_item_fire).setOnClickListener(this::onFireClicked);
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
            case FlipCard -> {
                itemTitle.setText(actual.getDisplayName());
                switch (((ItemFlipCard) actual).getFlipCardType()) {
                    case Jarvis ->
                            itemDescription.setText("The JARVIS virus can be used to reverse the alignment of a Resistance Portal.");
                    case Ada ->
                            itemDescription.setText("The ADA Refactor can be used to reverse the alignment of an Enlightened Portal.");
                }
                inflateResource(mItem, actual);
                findViewById(R.id.activity_inventory_item_use).setVisibility(View.VISIBLE);
                findViewById(R.id.activity_inventory_item_use).setEnabled(true);
                findViewById(R.id.activity_inventory_item_use).setOnClickListener(this::onFireClicked);
            }
            case PlayerPowerup -> {
                // FIXME different types of playerpowerups
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

        boolean isRecyclable = mGame.getKnobs().getRecycleKnobs().getRecycleValues(mInventory.getItems(mItem.getType()).get(0).getName()) != null;
        if (mGame.getKnobs().getClientFeatureKnobs().isEnableRecycle() && isRecyclable) {
            findViewById(R.id.activity_inventory_item_recycle).setEnabled(true);
            findViewById(R.id.activity_inventory_item_recycle).setOnClickListener(this::onRecycleItemClicked);
            findViewById(R.id.activity_inventory_item_drop).setEnabled(true);
            findViewById(R.id.activity_inventory_item_drop).setOnClickListener(this::onDropItemClicked);
        } else if (!isRecyclable) {
            findViewById(R.id.activity_inventory_item_recycle).setEnabled(false);
            findViewById(R.id.activity_inventory_item_drop).setEnabled(false);
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

    private void onDropItemClicked(View ignoredV) {

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

        builder.setNegativeButton(R.string.cancel, (d, which) -> d.dismiss());

        AlertDialog d = builder.create();
        d.show();
    }

    private void onFireClicked(View view) {
        getMainActivity().showFireCarousel(mItem);
        Intent intent = new Intent(ActivityInventoryItem.this, ActivityMain.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        setResult(RESULT_OK, intent);
        startActivity(intent);
        finish();
    }

    private void onUseItemClicked(View ignoredV) {
        if (mItem.getType() == ItemBase.ItemType.PowerCube) {
            mGame.intUsePowerCube((ItemPowerCube) Objects.requireNonNull(mInventory.getItems().get(mItem.getFirstID())), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                } else {
                    var res = data.getString("result");
                    DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                    String message = "Gained %s XM from using a powercube";
                    dialog.setMessage(String.format(message, res, mItem.getDescription())).setDismissDelay(1500).show();


                    for (var id : Objects.requireNonNull(data.getStringArray("consumed"))) {
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

    @SuppressLint("DefaultLocale")
    private void onRecycleItemClicked(View ignoredV) {
        if (mGame.getKnobs().getClientFeatureKnobs().isEnableRecycleConfirmationDialog()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ActivityInventoryItem.this);
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_recycle, null);
            builder.setView(dialogView);

            TextView itemDescription = dialogView.findViewById(R.id.item_description);
            TextView quantityDisplay = dialogView.findViewById(R.id.quantity_display);
            TextView recoveryInfo = dialogView.findViewById(R.id.recovery_info);
            Button buttonIncrement = dialogView.findViewById(R.id.button_increment);
            Button buttonDecrement = dialogView.findViewById(R.id.button_decrement);
            quantityDisplay.setText(String.format("%d/%d", 1, mItem.getQuantity()));
            updateRecoveryInfo(1, recoveryInfo);

            itemDescription.setText(MessageFormat.format("{0}{1}", getString(R.string.recycle_something), mItem.getDescription()));

            buttonIncrement.setOnClickListener(v1 -> {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                if (quantity < mItem.getQuantity()) {
                    quantity++;
                    quantityDisplay.setText(String.format("%d/%d", quantity, mItem.getQuantity()));
                    updateRecoveryInfo(quantity, recoveryInfo);
                }
            });

            buttonDecrement.setOnClickListener(v1 -> {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                if (quantity > 1) {
                    quantity--;
                    quantityDisplay.setText(String.format("%d/%d", quantity, mItem.getQuantity()));
                    updateRecoveryInfo(quantity, recoveryInfo);
                }
            });

            buttonIncrement.setOnLongClickListener(v1 -> {
                incrementQuantity(buttonIncrement, quantityDisplay, recoveryInfo);
                return true;
            });

            buttonDecrement.setOnLongClickListener(v1 -> {
                decrementQuantity(buttonDecrement, quantityDisplay, recoveryInfo);
                return true;
            });

            builder.setPositiveButton(R.string.ok, (d, which) -> {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                recycleItems(quantity);
                d.dismiss();
            });

            builder.setNegativeButton(R.string.cancel, (d, which) -> d.dismiss());

            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            recycleItems(1);
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateRecoveryInfo(int quantity, TextView recoveryInfo) {
        int recoveryAmount = calculateRecoveryAmount(quantity);
        recoveryInfo.setText(String.format("+%dXM", recoveryAmount));
    }

    /**
     * @noinspection BusyWait
     */
    @SuppressLint("DefaultLocale")
    private void incrementQuantity(Button buttonIncrement, TextView quantityDisplay, TextView recoveryInfo) {
        new Thread(() -> {
            while (buttonIncrement.isPressed()) {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                if (quantity < mItem.getQuantity()) {
                    quantity++;
                    int finalQuantity = quantity;
                    runOnUiThread(() -> {
                        quantityDisplay.setText(String.format("%d/%d", finalQuantity, mItem.getQuantity()));
                        updateRecoveryInfo(finalQuantity, recoveryInfo);
                    });
                }
                try {
                    Thread.sleep(33); // Adjust the speed of incrementing
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /** @noinspection BusyWait*/
    @SuppressLint("DefaultLocale")
    private void decrementQuantity(Button buttonDecrement, TextView quantityDisplay, TextView recoveryInfo) {
        new Thread(() -> {
            while (buttonDecrement.isPressed()) {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                if (quantity > 1) {
                    quantity--;
                    int finalQuantity = quantity;
                    runOnUiThread(() -> {
                        quantityDisplay.setText(String.format("%d/%d", finalQuantity, mItem.getQuantity()));
                        updateRecoveryInfo(finalQuantity, recoveryInfo);
                    });
                }
                try {
                    Thread.sleep(33); // Adjust the speed of decrementing
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private int calculateRecoveryAmount(int quantity) {
        int level = mItem.getLevel();
        // if guard value
        if (level == -999) {
            level = switch (mItem.getRarity()) {
                default -> -999; // crash
                case VeryCommon -> 1;
                case Common -> 2;
                case LessCommon -> 3;
                case Rare -> 4;
                case VeryRare -> 5;
                case ExtraRare -> 6;
            };
        }
        String name = mInventory.getItems(mItem.getType()).get(0).getName();
        return quantity * mGame.getKnobs().getRecycleKnobs().getRecycleValues(name).get(level - 1);
    }


    private void recycleItems(int quantity) {
        mGame.intRecycleItems(Objects.requireNonNull(mInventory.getItems(mItem.getType(), mItem.getRarity(), mItem.getLevel()).subList(0, quantity)), new Handler(msg -> {
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            // FIXME why is my error text funny?
            if (error != null && !error.isEmpty()) {
                DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                dialog.setMessage(error).setDismissDelay(1500).show();
            } else {
                var res = data.getString("result");
                DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                String message;
                if (quantity > 1) {
                    message = "Gained %s XM from recycling %d %ss";
                    dialog.setMessage(String.format(message, res, quantity, mItem.getDescription())).setDismissDelay(1500).show();
                } else {
                    message = "Gained %s XM from recycling a %s";
                    dialog.setMessage(String.format(message, res, mItem.getDescription())).setDismissDelay(1500).show();
                }

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

package net.opengress.slimgress.activity;

import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_IMAGE_RESOLUTION_NONE;
import static net.opengress.slimgress.SlimgressApplication.runInThread;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getMainActivity;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;
import static net.opengress.slimgress.ViewHelpers.getRarityColour;
import static net.opengress.slimgress.ViewHelpers.getRarityText;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.InventoryListItem;
import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.BulkPlayerStorage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
        ItemBase actual = mInventory.getItems().get(mItem.getFirstID());
        assert actual != null;

        ((TextView) findViewById(R.id.activity_inventory_item_qty)).setText("x" + mItem.getQuantity());

        // handle items with levels etc differently? need to check old stuff...
        switch (type) {
            case PortalKey -> {
                GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(((ItemPortalKey) actual).getPortalGuid());
                assert portal != null;

                mItemRarity.setVisibility(View.GONE);

                // Portal Key L1
                itemTitle.setText(R.string.item_name_portal_key);
                mItemLevel.setText(String.format("L%d", portal.getPortalLevel()));
                mLevelColour = getLevelColour(portal.getPortalLevel());
                mItemLevel.setTextColor(getColourFromResources(getResources(), mLevelColour));


                // bonanza at south beach
                itemName.setText(mItem.getDescription());
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
                findViewById(R.id.activity_inventory_item_image).setOnClickListener(c -> {
                    Intent myIntent = new Intent(this, ActivityPortal.class);
                    mGame.setCurrentPortal(portal);
                    startActivity(myIntent);
                });
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
                mLevelColour = getLevelColour(actual.getItemLevel());
                mItemLevel.setTextColor(getColourFromResources(getResources(), mLevelColour));
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
            BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
            String desiredResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
            if (Objects.equals(desiredResolution, UNTRANSLATABLE_IMAGE_RESOLUTION_NONE)) {
                Glide.with(this)
                        .load(R.drawable.no_image)
                        .into((ImageView) findViewById(R.id.activity_inventory_item_image));
            } else {
                Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.no_image)
                        .error(R.drawable.no_image)
                        .into((ImageView) findViewById(R.id.activity_inventory_item_image));
            }
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
            dist = (int) (loc.distanceTo(mItem.getLocation()));
        }
        ((TextView) findViewById(R.id.activity_inventory_item_distance)).setText(getPrettyDistanceString(dist));
    }

    @SuppressLint("DefaultLocale")
    private void inflateResource(InventoryListItem item, ItemBase actual) {
        mItemRarity.setText(getRarityText(item.getRarity()));
        int rarityColor = getRarityColour(item.getRarity());
        mItemRarity.setTextColor(getColourFromResources(getResources(), rarityColor));
        if (actual.getItemLevel() > 0) {
            mItemLevel.setText(String.format("L%d", actual.getItemLevel()));
            mLevelColour = getLevelColour(actual.getItemLevel());
            mItemLevel.setTextColor(getColourFromResources(getResources(), mLevelColour));
        }
    }

    private void onDropItemClicked(View ignoredV) {

        mGame.intDropItem(Objects.requireNonNull(mInventory.getItems().get(mItem.getFirstID())), new Handler(msg -> {
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            // FIXME why is my error text funny?
            if (error != null && !error.isEmpty()) {
                DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                dialog.setMessage(error).setDismissDelay(1500).show();
                SlimgressApplication.postPlainCommsMessage("Drop failed: " + error);
            } else {
                // could say what we dropped
                SlimgressApplication.postPlainCommsMessage("Drop successful");
                for (var id : Objects.requireNonNull(data.getStringArray("dropped"))) {
                    mItem.remove(id);
                    mInventory.removeItem(id);
                    ((TextView) findViewById(R.id.activity_inventory_item_qty)).setText("x" + mItem.getQuantity());
                }
                if (mItem.getQuantity() == 0) {
                    SlimgressApplication.schedule(this::finish, 100, TimeUnit.MILLISECONDS);
                }
            }
            return false;
        }));
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
            ItemPowerCube cube = (ItemPowerCube) Objects.requireNonNull(mInventory.getItems().get(mItem.getFirstID()));
            String name = cube.getUsefulName();
            mGame.intUsePowerCube(cube, new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                    SlimgressApplication.postPlainCommsMessage("Unable to use power cube: " + error);
                } else {
                    var res = data.getString("result");
                    String message = "Gained %s XM from using a %s";
                    message = String.format(message, res, name);
                    SlimgressApplication.postPlainCommsMessage(message);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();

                    for (var id : Objects.requireNonNull(data.getStringArray("consumed"))) {
                        mItem.remove(id);
                        mInventory.removeItem(id);
                        ((TextView) findViewById(R.id.activity_inventory_item_qty)).setText("x" + mItem.getQuantity());
                    }
                    if (mItem.getQuantity() == 0) {
                        SlimgressApplication.schedule(this::finish, 100, TimeUnit.MILLISECONDS);
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
        runInThread(() -> {
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
        });
    }

    /**
     * @noinspection BusyWait
     */
    @SuppressLint("DefaultLocale")
    private void decrementQuantity(Button buttonDecrement, TextView quantityDisplay, TextView recoveryInfo) {
        runInThread(() -> {
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
        });
    }

    private int calculateRecoveryAmount(int quantity) {
        int level = mItem.getLevel();
        // if guard value
        if (level == -999) {
            level = switch (mItem.getRarity()) {
                case VeryCommon -> 1;
                case Common -> 2;
                case LessCommon -> 3;
                case Rare -> 4;
                case VeryRare -> 5;
                case ExtraRare -> 6;
                default -> -999; // crash
            };
        }
        String name = mInventory.getItems(mItem.getType()).get(0).getName();
        return quantity * mGame.getKnobs().getRecycleKnobs().getRecycleValues(name).get(level - 1);
    }


    private void recycleItems(int quantity) {

        // Get all the IDs for the items of this type
        ArrayList<String> itemIDs = mItem.getAllIDs();

        // Ensure the requested quantity doesn't exceed available items
        if (itemIDs.size() < quantity) {
            quantity = itemIDs.size();
        }

        // Create a list of ItemBase from the item IDs
        List<ItemBase> itemsToRecycle = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            String id = itemIDs.get(i);
            ItemBase item = mInventory.getItems().get(id);
            if (item != null) {
                itemsToRecycle.add(item);
            }
        }

        String name = itemsToRecycle.get(0).getUsefulName();

        int finalQuantity = quantity;
        mGame.intRecycleItems(itemsToRecycle, new Handler(msg -> {
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            // FIXME why is my error text funny?
            if (error != null && !error.isEmpty()) {
                DialogInfo dialog = new DialogInfo(ActivityInventoryItem.this);
                dialog.setMessage(error).setDismissDelay(1500).show();
                SlimgressApplication.postPlainCommsMessage("Recycle failed: " + error);
            } else {
                SlimgressApplication.postPlainCommsMessage("Recycle successful");
                var res = data.getString("result");
                String message;
                if (finalQuantity > 1) {
                    message = "Gained %s XM from recycling %d %ss";
                    message = String.format(message, res, finalQuantity, name);
                } else {
                    message = "Gained %s XM from recycling a %s";
                    message = String.format(message, res, name);
                }
                SlimgressApplication.postPlainCommsMessage(message);
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();

                for (var id : Objects.requireNonNull(data.getStringArray("recycled"))) {
                    mItem.remove(id);
                    mInventory.removeItem(id);
                    ((TextView) findViewById(R.id.activity_inventory_item_qty)).setText("x" + mItem.getQuantity());
                }
                if (mItem.getQuantity() == 0) {
                    SlimgressApplication.schedule(this::finish, 100, TimeUnit.MILLISECONDS);
                }
            }
            return false;
        }));
    }
}

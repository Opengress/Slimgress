package net.opengress.slimgress.activity;

import static androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_IMAGE_RESOLUTION_NONE;
import static net.opengress.slimgress.SlimgressApplication.postPlainCommsMessage;
import static net.opengress.slimgress.SlimgressApplication.runInThread;
import static net.opengress.slimgress.SlimgressApplication.schedule;
import static net.opengress.slimgress.ViewHelpers.TextType.Drop;
import static net.opengress.slimgress.ViewHelpers.TextType.XMGain;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getMainActivity;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;
import static net.opengress.slimgress.ViewHelpers.getRarityColour;
import static net.opengress.slimgress.ViewHelpers.getRarityText;
import static net.opengress.slimgress.ViewHelpers.showFloatingText;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.InventoryListItem;
import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.Inventory;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemFlipCard;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.api.Item.ItemPowerCube;
import net.opengress.slimgress.dialog.DialogInfo;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FragmentInventoryItem extends Fragment {

    private GameState mGame;
    private Inventory mInventory;
    private TextView mItemRarity;
    private TextView mItemLevel;
    private int mLevelColour;
    private InventoryListItem mItem;
    private View mRootView;

    @NonNull
    public static FragmentInventoryItem newInstance(InventoryListItem item) {
        FragmentInventoryItem fragment = new FragmentInventoryItem();
        Bundle args = new Bundle();
        args.putSerializable("item", item);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRootView = inflater.inflate(R.layout.activity_inventory_item, container, false);
        // Get the item from the intent
        mItem = (InventoryListItem) getArguments().getSerializable("item");

        mGame = SlimgressApplication.getInstance().getGame();
        mInventory = mGame.getInventory();

        TextView itemTitle = mRootView.findViewById(R.id.activity_inventory_item_title);
        mItemRarity = mRootView.findViewById(R.id.activity_inventory_item_rarity);
        TextView itemName = mRootView.findViewById(R.id.activity_inventory_item_name);
        TextView itemDescription = mRootView.findViewById(R.id.activity_inventory_item_description);
        mItemLevel = mRootView.findViewById(R.id.activity_inventory_item_level);

        // Set the item details
        assert mItem != null;
        ItemBase.ItemType type = mItem.getType();
        ItemBase actual = mInventory.getItems().get(mItem.getFirstID());
        assert actual != null;

        // handle items with levels etc differently? need to check old stuff...
        switch (type) {
            case PortalKey -> {
                GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(((ItemPortalKey) actual).getPortalGuid());
                if (portal == null) {
                    throw new RuntimeException("Portal key can't refer to a null portal! ID was " + ((ItemPortalKey) actual).getPortalGuid());
                }

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
                mRootView.findViewById(R.id.activity_inventory_item_distance).setVisibility(View.VISIBLE);
                SlimgressApplication.getInstance().getLocationViewModel().getLocationData().observe(getViewLifecycleOwner(), unused -> updateDistance());

                // Domain Terrace, Karoro
                ((TextView) mRootView.findViewById(R.id.activity_inventory_item_address)).setText(((ItemPortalKey) actual).getPortalAddress());
                mRootView.findViewById(R.id.activity_inventory_item_address).setVisibility(View.VISIBLE);

//                itemDescription.setText("Use to create links and remote recharge this Portal");

                mRootView.findViewById(R.id.activity_inventory_item_recharge).setVisibility(View.VISIBLE);
                mRootView.findViewById(R.id.activity_inventory_item_recharge).setEnabled(mGame.canRecharge(portal));
                SlimgressApplication.getInstance().getUpdatedEntitiesViewModel().getEntities().observe(getViewLifecycleOwner(), list -> checkForUpdates(list, portal));
                SlimgressApplication.getInstance().getLocationViewModel().getLocationData().observe(getViewLifecycleOwner(), location -> onReceiveLocation(portal));
                mRootView.findViewById(R.id.activity_inventory_item_recharge).setOnClickListener(c -> {
                    FragmentTransaction transaction = getMainActivity().getSupportFragmentManager().beginTransaction();
                    transaction.add(R.id.fragment_container, FragmentRecharge.newInstance(portal.getEntityGuid()), "RECHARGE");
                    transaction.addToBackStack("RECHARGE");
                    transaction.commit();
                });
                mRootView.findViewById(R.id.activity_inventory_item_image).setOnClickListener(c -> {
                    FragmentTransaction transaction = getMainActivity().getSupportFragmentManager().beginTransaction();
                    transaction.add(R.id.fragment_container, FragmentPortal.newInstance(portal.getEntityGuid()), "PORTAL");
                    transaction.addToBackStack("PORTAL");
                    transaction.commit();
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
                mRootView.findViewById(R.id.activity_inventory_item_use).setVisibility(View.VISIBLE);
                boolean canUse = mGame.getAgent().getEnergy() < mGame.getAgent().getEnergyMax();
                canUse = canUse && mItem.getLevel() <= mGame.getAgent().getLevel();
                mRootView.findViewById(R.id.activity_inventory_item_use).setEnabled(canUse);
                mRootView.findViewById(R.id.activity_inventory_item_use).setOnClickListener(this::onUseItemClicked);
            }
            case WeaponXMP -> {
                itemTitle.setText("XMP Burster");
                itemDescription.setText("Exotic Matter Pulse weapons which can destroy enemy resonators and Mods and neutralize enemy portals");
                inflateResource(mItem, actual);
                mRootView.findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                mRootView.findViewById(R.id.activity_inventory_item_fire).setEnabled(mItem.getLevel() <= mGame.getAgent().getLevel());
                mRootView.findViewById(R.id.activity_inventory_item_fire).setOnClickListener(this::onFireClicked);
            }
            case WeaponUltraStrike -> {
                itemTitle.setText("Ultra Strike");
                itemDescription.setText("A variation of the Exotic Matter Pulse weapon with a more powerful blast that occurs within a smaller radius");
                inflateResource(mItem, actual);
                mRootView.findViewById(R.id.activity_inventory_item_fire).setVisibility(View.VISIBLE);
                mRootView.findViewById(R.id.activity_inventory_item_fire).setEnabled(mItem.getLevel() <= mGame.getAgent().getLevel());
                mRootView.findViewById(R.id.activity_inventory_item_fire).setOnClickListener(this::onFireClicked);
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
                mRootView.findViewById(R.id.activity_inventory_item_use).setVisibility(View.VISIBLE);
                mRootView.findViewById(R.id.activity_inventory_item_use).setEnabled(true);
                mRootView.findViewById(R.id.activity_inventory_item_use).setOnClickListener(this::onFireClicked);
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
                ((ImageView) mRootView.findViewById(R.id.activity_inventory_item_image)).setImageDrawable(AppCompatResources.getDrawable(requireActivity().getApplicationContext(), mItem.getIconID()));
            } else {
                ((ImageView) mRootView.findViewById(R.id.activity_inventory_item_image)).setImageDrawable(mItem.getIcon());
            }
        } else {
            BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
            String desiredResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
            if (Objects.equals(desiredResolution, UNTRANSLATABLE_IMAGE_RESOLUTION_NONE)) {
                Glide.with(this)
                        .load(R.drawable.no_image)
                        .into((ImageView) mRootView.findViewById(R.id.activity_inventory_item_image));
            } else {
                Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.no_image)
                        .error(R.drawable.no_image)
                        .into((ImageView) mRootView.findViewById(R.id.activity_inventory_item_image));
            }
        }

        boolean isRecyclable = mGame.getKnobs().getRecycleKnobs().getRecycleValues(mInventory.getItems(mItem.getType()).get(0).getName()) != null;
        if (isRecyclable) {
            mRootView.findViewById(R.id.activity_inventory_item_recycle).setEnabled(true);
            mRootView.findViewById(R.id.activity_inventory_item_recycle).setOnClickListener(this::onRecycleItemClicked);
            mRootView.findViewById(R.id.activity_inventory_item_drop).setEnabled(mGame.scannerIsEnabled());
            mRootView.findViewById(R.id.activity_inventory_item_drop).setOnClickListener(this::onDropItemClicked);
        } else {
            mRootView.findViewById(R.id.activity_inventory_item_recycle).setEnabled(false);
            mRootView.findViewById(R.id.activity_inventory_item_drop).setEnabled(false);
        }

        mRootView.findViewById(R.id.activity_inventory_item_back_button).setOnClickListener(v -> finish());

        return mRootView;
    }

    private void onReceiveLocation(GameEntityPortal portal) {
        boolean isRecyclable = mGame.getKnobs().getRecycleKnobs().getRecycleValues(mInventory.getItems(mItem.getType()).get(0).getName()) != null;
        mRootView.findViewById(R.id.activity_inventory_item_drop).setEnabled(isRecyclable && mGame.scannerIsEnabled());
        mRootView.findViewById(R.id.activity_inventory_item_recharge).setEnabled(mGame.canRecharge(portal));
    }

    private void checkForUpdates(List<GameEntityBase> gameEntityBases, GameEntityPortal portal) {
        for (GameEntityBase entity : gameEntityBases) {
            if (entity.getEntityGuid().equals(portal.getEntityGuid())) {
                mRootView.findViewById(R.id.activity_inventory_item_recharge).setEnabled(mGame.canRecharge(portal));
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onResume() {
        super.onResume();

        // Retrieve the current list of IDs from the InventoryListItem
        ArrayList<String> itemIDs = new ArrayList<>(mItem.getAllIDs());

        // Check each ID against the Inventory to ensure it still exists
        for (String id : itemIDs) {
            if (mInventory.findItem(id) == null) {
                // If no longer in the inventory, remove it from mItem
                mItem.remove(id);
            }
        }

        // Now mItem.getQuantity() reflects the current actual quantity
        ((TextView) mRootView.findViewById(R.id.activity_inventory_item_qty)).setText("x" + mItem.getQuantity());

        // If the quantity is zero, you might consider closing this activity
        if (mItem.getQuantity() == 0) {
            finish();
        }
    }

    private void updateDistance() {
        int dist = 999999000;
        Location loc = mGame.getLocation();
        if (loc != null) {
            // crashes on next line if there aren't exclusions in proguard config
            dist = (int) (loc.distanceTo(mItem.getLocation()));
        }
        ((TextView) mRootView.findViewById(R.id.activity_inventory_item_distance)).setText(getPrettyDistanceString(dist));
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

    @SuppressLint("SetTextI18n")
    private void onDropItemClicked(View ignoredV) {
        if (mItem.getQuantity() < 1) {
            finish();
        }
        ItemBase item = mInventory.getItems().get(mItem.getFirstID());
        if (item == null) {
            String error = "Item is not in your inventory.";
            FragmentActivity act = getActivity();
            if (act != null) {
                DialogInfo dialog = new DialogInfo(act);
                dialog.setMessage(error).setDismissDelay(1500).show();
            }
            postPlainCommsMessage("Drop failed: " + error);
            return;
        }
        mGame.intDropItem(item, new Handler(msg -> {
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            if (error != null && !error.isEmpty()) {
                FragmentActivity act = getActivity();
                if (act != null) {
                    DialogInfo dialog = new DialogInfo(act);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                }
                postPlainCommsMessage("Drop failed: " + error);
            } else {
                // could say what we dropped
                postPlainCommsMessage("Drop successful");
                showFloatingText("Drop successful", Drop);
                for (var id : requireNonNull(data.getStringArray("dropped"))) {
                    mItem.remove(id);
                    mInventory.removeItem(id);
                    ((TextView) mRootView.findViewById(R.id.activity_inventory_item_qty)).setText("x" + mItem.getQuantity());
                }
                if (mItem.getQuantity() == 0) {
                    schedule(this::finish, 50, MILLISECONDS);
                }
            }
            return false;
        }));
    }

    private void onFireClicked(View view) {
        getMainActivity().showFireCarousel(mItem);
        requireActivity().getSupportFragmentManager().popBackStack(null, POP_BACK_STACK_INCLUSIVE);
    }

    @SuppressLint("SetTextI18n")
    private void onUseItemClicked(View ignoredV) {
        if (mItem.getQuantity() < 1) {
            finish();
        }
        if (mItem.getType() == ItemBase.ItemType.PowerCube) {
            ItemPowerCube cube = (ItemPowerCube) requireNonNull(mInventory.getItems().get(mItem.getFirstID()));
            String name = cube.getUsefulName();
            mGame.intUsePowerCube(cube, new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    FragmentActivity act = getActivity();
                    if (act != null) {
                        DialogInfo dialog = new DialogInfo(act);
                        dialog.setMessage(error).setDismissDelay(1500).show();
                    }
                    postPlainCommsMessage("Unable to use power cube: " + error);
                } else {
                    var res = data.getInt("xmGained");
                    String message = "Gained %s XM from using a %s";
                    message = String.format(message, res, name);
                    postPlainCommsMessage(message);
                    showFloatingText(String.format(Locale.getDefault(), "+%dXM", res), XMGain);
//                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();

                    for (var id : requireNonNull(data.getStringArray("consumed"))) {
                        mItem.remove(id);
                        mInventory.removeItem(id);
                        ((TextView) mRootView.findViewById(R.id.activity_inventory_item_qty)).setText("x" + mItem.getQuantity());
                    }
                    if (mItem.getQuantity() == 0) {
                        schedule(this::finish, 50, MILLISECONDS);
                    }
                }
                return false;
            }));
        }
    }

    @SuppressLint("DefaultLocale")
    private void onRecycleItemClicked(View ignoredV) {
        if (mItem.getQuantity() < 1) {
            finish();
        }
        if (mGame.getKnobs().getClientFeatureKnobs().isEnableRecycleConfirmationDialog()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
            LayoutInflater inflater = getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.dialog_recycle, null);
            builder.setView(dialogView);

            TextView neededXmView = dialogView.findViewById(R.id.recycling_xm_to_fill_tank);
            TextView itemDescription = dialogView.findViewById(R.id.item_description);
            TextView quantityDisplay = dialogView.findViewById(R.id.quantity_display);
            TextView recoveryInfo = dialogView.findViewById(R.id.recovery_info);
            Button buttonIncrement = dialogView.findViewById(R.id.button_increment);
            Button buttonDecrement = dialogView.findViewById(R.id.button_decrement);
            quantityDisplay.setText(String.format("%d/%d", 1, mItem.getQuantity()));
            updateRecoveryInfo(1, recoveryInfo);
            updateQuantityDesired(neededXmView);

            itemDescription.setText(MessageFormat.format("{0}{1}", getString(R.string.recycle_something), mItem.getDescription()));

            buttonIncrement.setOnClickListener(v1 -> {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                if (quantity < mItem.getQuantity()) {
                    quantity++;
                    quantityDisplay.setText(String.format("%d/%d", quantity, mItem.getQuantity()));
                    updateRecoveryInfo(quantity, recoveryInfo);
                    updateQuantityDesired(neededXmView);
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
                    updateQuantityDesired(neededXmView);
                }
            });

            buttonIncrement.setOnLongClickListener(v1 -> {
                incrementQuantity(buttonIncrement, quantityDisplay, recoveryInfo, neededXmView);
                return true;
            });

            buttonDecrement.setOnLongClickListener(v1 -> {
                decrementQuantity(buttonDecrement, quantityDisplay, recoveryInfo, neededXmView);
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

    private void updateQuantityDesired(TextView neededXmView) {
        int xm = mGame.getAgent().getEnergy();
        int max = mGame.getAgent().getEnergyMax();
        if (xm < max) {
            neededXmView.setText(String.format(Locale.getDefault(), "Need %d XM to fill tank", max - xm));
        } else {
            neededXmView.setText("XM tank is currently full");
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
    private void incrementQuantity(Button buttonIncrement, TextView quantityDisplay, TextView recoveryInfo, TextView neededXmView) {
        runInThread(() -> {
            while (buttonIncrement.isPressed()) {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                if (quantity < mItem.getQuantity()) {
                    quantity++;
                    int finalQuantity = quantity;
                    requireActivity().runOnUiThread(() -> {
                        quantityDisplay.setText(String.format("%d/%d", finalQuantity, mItem.getQuantity()));
                        updateRecoveryInfo(finalQuantity, recoveryInfo);
                        updateQuantityDesired(neededXmView);
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
    private void decrementQuantity(Button buttonDecrement, TextView quantityDisplay, TextView recoveryInfo, TextView neededXmView) {
        runInThread(() -> {
            while (buttonDecrement.isPressed()) {
                String quantityText = quantityDisplay.getText().toString();
                String[] parts = quantityText.split("/");
                int quantity = Integer.parseInt(parts[0]);
                if (quantity > 1) {
                    quantity--;
                    int finalQuantity = quantity;
                    requireActivity().runOnUiThread(() -> {
                        quantityDisplay.setText(String.format("%d/%d", finalQuantity, mItem.getQuantity()));
                        updateRecoveryInfo(finalQuantity, recoveryInfo);
                        updateQuantityDesired(neededXmView);
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


    @SuppressLint("SetTextI18n")
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
            FragmentActivity act = getActivity();
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            if (error != null && !error.isEmpty()) {
                if (act != null) {
                    DialogInfo dialog = new DialogInfo(act);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                }
                postPlainCommsMessage("Recycle failed: " + error);
            } else {
                postPlainCommsMessage("Recycle successful");
                var res = data.getInt("result");
                String message;
                if (finalQuantity > 1) {
                    message = "Gained %s XM from recycling %d %ss";
                    message = String.format(message, res, finalQuantity, name);
                } else {
                    message = "Gained %d XM from recycling a %s";
                    message = String.format(message, res, name);
                }
                postPlainCommsMessage(message);
//                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                showFloatingText(String.format(Locale.getDefault(), "+%dXM", res), XMGain);
                if (act != null) {
                    for (var id : requireNonNull(data.getStringArray("recycled"))) {
                        mItem.remove(id);
                        mInventory.removeItem(id);
                        act.<TextView>findViewById(R.id.activity_inventory_item_qty).setText("x" + mItem.getQuantity());
                    }
                }
                if (mItem.getQuantity() == 0) {
                    schedule(this::finish, 50, MILLISECONDS);
                }
            }
            return false;
        }));
    }

    private void finish() {
        if (isAdded() && getView() != null) {
            getView().post(() -> getParentFragmentManager().popBackStackImmediate());
        }
    }
}

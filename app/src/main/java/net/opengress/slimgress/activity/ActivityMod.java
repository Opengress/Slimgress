package net.opengress.slimgress.activity;

import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getRarityColour;
import static net.opengress.slimgress.ViewHelpers.updateInfoText;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.Inventory;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal.LinkedMod;
import net.opengress.slimgress.api.Item.ItemMod;
import net.opengress.slimgress.api.Item.ModKey;
import net.opengress.slimgress.dialog.DialogInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// FIXME user can't deploy on portal if portal belongs to wrong team!
public class ActivityMod extends AppCompatActivity {

    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();
    private final int mMaxModsPerPlayer = mGame.getKnobs().getPortalKnobs().getMaxModsPerPlayer();
    private final boolean mPlayerCanRemoveMods = mGame.getKnobs().getPortalKnobs().getCanPlayerRemoveMod();
    private final Inventory inventory = mGame.getInventory();
    private final int[] mModViewIds = {
            R.id.modScreenMod0,
            R.id.modScreenMod1,
            R.id.modScreenMod2,
            R.id.modScreenMod3
    };


    private final Handler deployResultHandler = new Handler(msg -> {
        var data = msg.getData();
        String error = getErrorStringFromAPI(data);
        if (error != null && !error.isEmpty()) {
            DialogInfo dialog = new DialogInfo(ActivityMod.this);
            dialog.setMessage(error).setDismissDelay(1500).show();
        } else {
            ItemMod mod = (ItemMod) msg.getData().getSerializable("mod");
            // FIXME this does not work correctly in the case where we are removing a mod and shields have funny add cost
            String name = (mod == null) ? "RES_SHIELD" : mod.getName();
            mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getPortalModByLevel(name).get(mGame.getCurrentPortal().getPortalLevel() - 1));
        }

        setUpView();
        return false;
    });

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mod);
        setUpView();
        mApp.getLocationViewModel().getLocationData().observe(this, this::onReceiveLocation);

    }

    @SuppressLint({"DefaultLocale", "ObsoleteSdkInt", "SetTextI18n"})
    public void setUpView() {
        GameEntityPortal portal = mGame.getCurrentPortal();
        boolean teamOK = portal.getPortalTeam().toString().equals(mGame.getAgent().getTeam().toString());

        // iterate over the mods (maybe unrolled) and set up their info and any relevant callbacks
        for (int id : mModViewIds) {
            findViewById(id).findViewById(R.id.widgetActionButton).setVisibility(View.INVISIBLE);
            findViewById(id).findViewById(R.id.widgetActionButton).setEnabled(false);
            findViewById(id).findViewById(R.id.widgetActionButton).setTag(id);
//            ((TextView) findViewById(id).findViewById(R.id.modDescriptionText)).setText(R.string.l0);
        }

        List<LinkedMod> mods = portal.getPortalMods();
        // iterate to find out if portal is fully deployed
        int modCount = 0;
        int yourMods = 0;
        for (LinkedMod mod : mods) {
            if (mod != null) {
                modCount++;
                if (Objects.equals(mod.installingUser, mGame.getAgent().getEntityGuid())) {
                    yourMods++;
                }
            }
        }

        List<ItemMod> deployableMods;
        if (teamOK) {
            deployableMods = inventory.getMods();
        } else {
            deployableMods = null;
        }

        // iterate again to set up resonator deployment user interface
        for (int slot = 0; slot < mods.size(); slot++) {
            View widget = findViewById(modSlotToLayoutId(slot));
            LinkedMod mod = mods.get(slot);
            if (mod == null) {
                ((TextView) widget.findViewById(R.id.modDescriptionText)).setText(null);
                ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setText(null);
                boolean canInstall = yourMods < mMaxModsPerPlayer && modCount < 4;
                if (deployableMods != null && !deployableMods.isEmpty() && canInstall) {
                    widget.findViewById(R.id.widgetActionButton).setVisibility(View.VISIBLE);
                    widget.findViewById(R.id.widgetActionButton).setEnabled(true);
                    ((Button) widget.findViewById(R.id.widgetActionButton)).setText(R.string.dply);
                    widget.findViewById(R.id.widgetActionButton).setOnClickListener(this::onInstallModButtonPressed);
                }
                continue;
            }
            ((TextView) widget.findViewById(R.id.modDescriptionText)).setText(mod.rarity.name() + "\n" + mod.displayName);
            int rarityColour = getRarityColour(mod.rarity);
            ((TextView) widget.findViewById(R.id.modDescriptionText)).setTextColor(getColourFromResources(getResources(), rarityColour));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setText(mGame.getAgentName(mod.installingUser));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setTextColor(0xff000000 + portal.getPortalTeam().getColour());
            if (teamOK && mPlayerCanRemoveMods) {
                widget.findViewById(R.id.widgetActionButton).setVisibility(View.VISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(true);
                ((Button) widget.findViewById(R.id.widgetActionButton)).setText(R.string.mod_text_remove_mod);
                widget.findViewById(R.id.widgetActionButton).setOnClickListener(this::onRemoveModButtonPressed);
            } else {
                widget.findViewById(R.id.widgetActionButton).setVisibility(View.INVISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(false);
            }
        }

        int dist = (int) mGame.getLocation().distanceTo(mGame.getCurrentPortal().getPortalLocation());
        updateInfoText(dist, mGame.getCurrentPortal(), findViewById(R.id.modScreenPortalInfo));
        setButtonsEnabled(dist <= mActionRadiusM);
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private void onInstallModButtonPressed(View view) {
        int slot = layoutIdToModSlot((Integer) view.getTag());
        List<ItemMod> mods = mGame.getInventory().getMods();
        HashMap<ModKey, Integer> counts = getAvailableMods(mods);

        List<Map.Entry<ModKey, Integer>> sortedModEntries = getSortedModCounts(counts);

        List<String> displayStrings = new ArrayList<>();
        List<ModKey> modKeys = new ArrayList<>();

        for (Map.Entry<ModKey, Integer> entry : sortedModEntries) {
            ModKey key = entry.getKey();
            int count = entry.getValue();
            String displayString = key.rarity + " " + key.modDisplayName + " (x" + count + ")";
            displayStrings.add(displayString);
            modKeys.add(key);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Mod");
        builder.setItems(displayStrings.toArray(new String[0]), (dialogInterface, i) -> {
            ModKey selectedKey = modKeys.get(i);
            // Retrieve a mod from inventory that matches the selectedKey
            ItemMod modToInstall = mGame.getInventory().getModForDeployment(selectedKey);
            if (modToInstall != null) {
                mGame.getInventory().removeItem(modToInstall);
                // Proceed to install the mod
                mGame.intAddMod(modToInstall, mGame.getCurrentPortal(), slot, deployResultHandler);
            } else {
                // Handle the case where the mod is no longer available
                Toast.makeText(this, "Mod not available", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private void onRemoveModButtonPressed(View view) {
        int slot = layoutIdToModSlot((Integer) view.getTag());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Removal");
        builder.setMessage("You are about to remove a mod. It will be erased from existence. Are you sure?")
                .setPositiveButton("Yes", (dialogInterface, which) -> mGame.intRemoveMod(mGame.getCurrentPortal(), slot, deployResultHandler))
                .setNegativeButton("No", (dialogInterface, which) -> dialogInterface.dismiss());
        builder.show();
    }

    private List<Map.Entry<ModKey, Integer>> getSortedModCounts(HashMap<ModKey, Integer> counts) {
        List<Map.Entry<ModKey, Integer>> modEntries = new ArrayList<>(counts.entrySet());

        // Sort the list based on ModKey's compareTo method
        Collections.sort(modEntries, (e1, e2) -> e1.getKey().compareTo(e2.getKey()));

        return modEntries;
    }

    @SuppressLint("DefaultLocale")
    private HashMap<ModKey, Integer> getAvailableMods(List<ItemMod> mods) {
        HashMap<ModKey, Integer> counts = new HashMap<>();
        for (ItemMod mod : mods) {
            ModKey key = new ModKey(mod.getItemType(), mod.getItemRarity(), mod.getModDisplayName());
            if (counts.containsKey(key)) {
                counts.put(key, counts.get(key) + 1);
            } else {
                counts.put(key, 1);
            }
        }
        return counts;
    }


    private void onReceiveLocation(Location location) {
        if (location != null) {
            int dist = (int) location.distanceTo(mGame.getCurrentPortal().getPortalLocation());
            setButtonsEnabled(dist <= mActionRadiusM);
            updateInfoText(dist, mGame.getCurrentPortal(), findViewById(R.id.modScreenPortalInfo));
        } else {
            setButtonsEnabled(false);
            updateInfoText(999999000, mGame.getCurrentPortal(), findViewById(R.id.modScreenPortalInfo));
        }
    }

    private void setButtonsEnabled(boolean shouldEnableButton) {
        for (int id : mModViewIds) {
            findViewById(id).findViewById(R.id.widgetActionButton).setEnabled(shouldEnableButton);
        }
    }

    private int modSlotToLayoutId(int slot) {
        // not sure if correct - cf intel.js eastAnticlockwiseToNorthClockwise
        // also window.OCTANTS = ['E', 'NE', 'N', 'NW', 'W', 'SW', 'S', 'SE'];
        return switch (slot) {
            case 0 -> R.id.modScreenMod0;
            case 1 -> R.id.modScreenMod1;
            case 2 -> R.id.modScreenMod2;
            case 3 -> R.id.modScreenMod3;
            default -> {
                Log.e("ActivityMod", "Unknown mod slot: " + slot);
                yield -99999;
                // maybe this should be a throw
            }
        };
    }

    // doesn't matter that these are non-constant because this project isn't a library. caveat.
    @SuppressLint("NonConstantResourceId")
    private int layoutIdToModSlot(int id) {
        return switch (id) {
            case R.id.modScreenMod0 -> 0;
            case R.id.modScreenMod1 -> 1;
            case R.id.modScreenMod2 -> 2;
            case R.id.modScreenMod3 -> 3;
            default -> {
                Log.e("ActivityMod", "Unknown mod widget id: " + id);
                yield -99999;
                // FIXME: maybe this should be a throw
            }
        };
    }

}

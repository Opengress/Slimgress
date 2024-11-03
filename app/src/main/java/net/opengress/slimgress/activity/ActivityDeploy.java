package net.opengress.slimgress.activity;

import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.Inventory;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal.LinkedResonator;
import net.opengress.slimgress.api.Item.ItemResonator;
import net.opengress.slimgress.dialog.DialogInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ActivityDeploy extends AppCompatActivity {

    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();
    private final Inventory inventory = mGame.getInventory();
    private final int[] mResoViewIds = {
            R.id.deployScreenResonatorE,
            R.id.deployScreenResonatorNE,
            R.id.deployScreenResonatorN,
            R.id.deployScreenResonatorNW,
            R.id.deployScreenResonatorW,
            R.id.deployScreenResonatorSW,
            R.id.deployScreenResonatorS,
            R.id.deployScreenResonatorSE,
    };

    private final Handler deployResultHandler = new Handler(msg -> {
        var data = msg.getData();
        String error = getErrorStringFromAPI(data);
        if (error != null && !error.isEmpty()) {
            DialogInfo dialog = new DialogInfo(ActivityDeploy.this);
            dialog.setMessage(error).setDismissDelay(1500).show();
        } else {
            ItemResonator reso = (ItemResonator) msg.getData().getSerializable("resonator");
            assert reso != null;
            mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getResonatorDeployCostByLevel().get(reso.getItemLevel() - 1));
        }

        setUpView();
        return false;
    });

    private final Handler upgradeResultHandler = new Handler(msg -> {
        var data = msg.getData();
        String error = getErrorStringFromAPI(data);
        if (error != null && !error.isEmpty()) {
            DialogInfo dialog = new DialogInfo(ActivityDeploy.this);
            dialog.setMessage(error).setDismissDelay(1500).show();
        } else {
            ItemResonator reso = (ItemResonator) msg.getData().getSerializable("resonator");
            assert reso != null;
            mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getResonatorUpgradeCostByLevel().get(reso.getItemLevel() - 1));
        }

        setUpView();
        return false;
    });

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deploy);
        setUpView();
        mApp.getLocationViewModel().getLocationData().observe(this, this::onReceiveLocation);

    }

    @SuppressLint({"DefaultLocale", "ObsoleteSdkInt"})
    public void setUpView() {
        GameEntityPortal portal = mGame.getCurrentPortal();
        boolean teamOK = portal.getPortalTeam().toString().equalsIgnoreCase("neutral") || portal.getPortalTeam().toString().equals(mGame.getAgent().getTeam().toString());


        findViewById(R.id.btnRechargeAll).setVisibility(View.INVISIBLE);
        findViewById(R.id.btnRechargeAll).setEnabled(false);

        // iterate over the resos (maybe unrolled) and set up their info and any relevant callbacks
        for (int id : mResoViewIds) {
            ((Button) findViewById(id).findViewById(R.id.widgetActionButton)).setText(R.string.dply);
            if (teamOK) {
                findViewById(id).findViewById(R.id.widgetActionButton).setOnClickListener(this::onDeployButtonPressed);
            } else {
                findViewById(id).findViewById(R.id.widgetActionButton).setVisibility(View.INVISIBLE);
                findViewById(id).findViewById(R.id.widgetActionButton).setEnabled(false);
            }
            findViewById(id).findViewById(R.id.widgetActionButton).setTag(id);
            // we don't do this in the reso widget because it's also used for recharging
//            ((TextView) findViewById(id).findViewById(R.id.resoLevelText)).setText(R.string.l0);
        }

        List<LinkedResonator> resos = portal.getPortalResonators();
        // iterate to find out if portal is fully deployed
        int resoCount = 0;
        for (LinkedResonator reso : resos) {
            if (reso != null) {
                resoCount++;
            }
        }
        HashMap<Integer, Integer> resoCountForLevel = getResoCountsForLevels(resos);

        // iterate again to set up resonator deployment user interface
        for (LinkedResonator reso : resos) {
            if (reso == null) {
                continue;
            }
            View widget = findViewById(resoSlotToLayoutId(reso.slot));
            ((TextView) widget.findViewById(R.id.resoLevelText)).setText(String.format("L%d", reso.level));
            int levelColour = getLevelColour(reso.level);
            ((TextView) widget.findViewById(R.id.resoLevelText)).setTextColor(getColourFromResources(getResources(), levelColour));
            ((TextView) widget.findViewById(R.id.positionText)).setText(String.format("%dm %s", reso.distanceToPortal, resoSlotToOctantText(reso.slot)));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setText(mGame.getAgentName(reso.ownerGuid));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setTextColor(0xff000000 + portal.getPortalTeam().getColour());

            List<ItemResonator> resosForUpgrade = new ArrayList<>();
            if (teamOK && canUpgradeOrDeploy(resoCountForLevel, reso.level)) {
                resosForUpgrade = inventory.getResosForUpgrade(reso.level);
            }

            HashMap<Integer, String> levels = getAvailableDeployLevels(resosForUpgrade, resoCountForLevel);
            boolean canInstall = !levels.isEmpty();
            if (resosForUpgrade.isEmpty()) {
                widget.findViewById(R.id.widgetActionButton).setVisibility(View.INVISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(false);
            } else if (resoCount == 8 && canInstall) {
                widget.findViewById(R.id.widgetActionButton).setVisibility(View.VISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(true);
                ((Button) widget.findViewById(R.id.widgetActionButton)).setText(R.string.upgd);
                widget.findViewById(R.id.widgetActionButton).setOnClickListener(this::onUpgradeButtonPressed);
            } else {
                // maybe this should be covered by resosForUpgrade branch
                widget.findViewById(R.id.widgetActionButton).setVisibility(View.INVISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(false);
            }
            ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).setMax(reso.getMaxEnergy());
            ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).setProgress(reso.energyTotal);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).getProgressDrawable().setTint(0xff000000 + portal.getPortalTeam().getColour());
            } else {
                ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).getProgressDrawable().setColorFilter(0xff000000 + portal.getPortalTeam().getColour(), PorterDuff.Mode.SRC_IN);
            }
        }

        int dist = (int) mGame.getLocation().distanceTo(mGame.getCurrentPortal().getPortalLocation());
        updateInfoText(dist);
        setButtonsEnabled(dist <= mActionRadiusM);
    }

    private HashMap<Integer, Integer> getResoCountsForLevels(List<GameEntityPortal.LinkedResonator> resos) {
        HashMap<Integer, Integer> resoCountForLevel = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            resoCountForLevel.put(i, 0);
        }

        for (var reso : resos) {
            if (reso == null) {
                continue;
            }
            if (Objects.equals(reso.ownerGuid, mGame.getAgent().getEntityGuid())) {
                resoCountForLevel.put(reso.level, resoCountForLevel.get(reso.level) + 1);
            }
        }
        return resoCountForLevel;
    }

    private boolean canUpgradeOrDeploy(HashMap<Integer, Integer> resoCountForLevel, int currentLevel) {
        while (currentLevel < 8) {
            if (canPutThisResonatorOn(resoCountForLevel, currentLevel + 1)) {
                return true;
            }
            ++currentLevel;
        }
        return false;
    }

    private boolean canPutThisResonatorOn(HashMap<Integer, Integer> resoCountForLevel, int level) {
        return mGame.getKnobs().getPortalKnobs().getBandForLevel(level).getRemaining() > resoCountForLevel.get(level);
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private void onUpgradeButtonPressed(View view) {
        int slot = layoutIdToResoSlot((Integer) view.getTag());
        var reso = mGame.getCurrentPortal().getPortalResonator(slot);
        var resosForUpgrade = inventory.getResosForUpgrade(reso.level);
        var resoCountForLevel = getResoCountsForLevels(mGame.getCurrentPortal().getPortalResonators());
        HashMap<Integer, String> levels = getAvailableDeployLevels(resosForUpgrade, resoCountForLevel);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Resonator");
//        builder.setIcon(R.drawable.ic_format_list_bulleted_black_24dp);

        builder.setItems(levels.values().toArray(new String[0]), (dialogInterface, i) -> {
            var which = levels.keySet().toArray(new Integer[0])[i];
            for (var r : resosForUpgrade) {
                if (r.getItemAccessLevel() == which) {
                    mGame.getInventory().removeItem(r);
                    // FIXME crash on error which will show its head when agents race to upgrade
                    mGame.intUpgradeResonator(r, mGame.getCurrentPortal(), slot, upgradeResultHandler);
                    break;
                }
            }
        });
        builder.show();
    }

    @SuppressLint("DefaultLocale")
    private @NonNull HashMap<Integer, String> getAvailableDeployLevels(List<ItemResonator> resosForUpgrade, HashMap<Integer, Integer> resoCountForLevel) {
        HashMap<Integer, String> levels = new HashMap<>();
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (var r : resosForUpgrade) {
            int level = r.getItemLevel();
            if (canPutThisResonatorOn(resoCountForLevel, level)) {
                if (!levels.containsKey(level)) {
                    levels.put(level, String.format("Level %d (x1)", level));
                    counts.put(level, 1);
                } else {
                    int count = counts.get(level);
                    counts.put(level, count + 1);
                    levels.put(level, String.format("Level %d (x%d)", r.getItemLevel(), count + 1));
                }
            }
        }
        return levels;
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private void onDeployButtonPressed(View view) {
        int slot = layoutIdToResoSlot((Integer) view.getTag());
        var resosForUpgrade = inventory.getResosForUpgrade(0);
        var resoCountForLevel = getResoCountsForLevels(mGame.getCurrentPortal().getPortalResonators());
        HashMap<Integer, String> levels = getAvailableDeployLevels(resosForUpgrade, resoCountForLevel);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Resonator");
//        builder.setIcon(R.drawable.ic_format_list_bulleted_black_24dp);

        builder.setItems(levels.values().toArray(new String[0]), (dialogInterface, i) -> {
            var which = levels.keySet().toArray(new Integer[0])[i];
            ItemResonator r = mGame.getInventory().getResoForDeployment(which);
            mGame.getInventory().removeItem(r);
            // FIXME probable crash on error which will show its head when agents race to deploy
            mGame.intDeployResonator(r, mGame.getCurrentPortal(), slot, deployResultHandler);
        });
        builder.show();
    }

    private void onReceiveLocation(Location location) {
        if (location != null) {
            int dist = (int) location.distanceTo(mGame.getCurrentPortal().getPortalLocation());
            setButtonsEnabled(dist <= mActionRadiusM);
            updateInfoText(dist);
        } else {
            setButtonsEnabled(false);
            updateInfoText(999999000);
        }
    }

    private void setButtonsEnabled(boolean shouldEnableButton) {
        for (int id : mResoViewIds) {
            findViewById(id).findViewById(R.id.widgetActionButton).setEnabled(shouldEnableButton);
        }
    }

    private void updateInfoText(int dist) {
        String distanceText = getPrettyDistanceString(dist);
        GameEntityPortal portal = mGame.getCurrentPortal();

        StringBuilder modText = new StringBuilder();
        for (GameEntityPortal.LinkedMod mod : portal.getPortalMods()) {
            if (mod == null) {
                modText.append("MOD: ").append("\n");
            } else {
                modText.append("MOD: ").append(mod.rarity.name()).append(" ").append(mod.displayName).append("\n");
            }
        }

        String portalInfoText = "LVL: L" + portal.getPortalLevel() + "\n"
                + "RNG: " + portal.getPortalLinkRange() + "m\n"
                + "ENR: " + portal.getPortalEnergy() + " / " + portal.getPortalMaxEnergy() + "\n"
                + modText
//                + "LNK: 0 in, 0 out (unimplemented)"
                + "DST: " + distanceText;
        ((TextView) (findViewById(R.id.deployScreenPortalInfo))).setText(portalInfoText);
    }

    private int resoSlotToLayoutId(int slot) {
        // not sure if correct - cf intel.js eastAnticlockwiseToNorthClockwise
        // also window.OCTANTS = ['E', 'NE', 'N', 'NW', 'W', 'SW', 'S', 'SE'];
        return switch (slot) {
            case 0 -> R.id.deployScreenResonatorE;
            case 1 -> R.id.deployScreenResonatorNE;
            case 2 -> R.id.deployScreenResonatorN;
            case 3 -> R.id.deployScreenResonatorNW;
            case 4 -> R.id.deployScreenResonatorW;
            case 5 -> R.id.deployScreenResonatorSW;
            case 6 -> R.id.deployScreenResonatorS;
            case 7 -> R.id.deployScreenResonatorSE;
            default -> {
                Log.e("ActivityDeploy", "Unknown resonator slot: " + slot);
                yield -99999;
                // maybe this should be a throw
            }
        };
    }

    // doesn't matter that these are non-constant because this project isn't a library. caveat.
    @SuppressLint("NonConstantResourceId")
    private int layoutIdToResoSlot(int id) {
        return switch (id) {
            case R.id.deployScreenResonatorE -> 0;
            case R.id.deployScreenResonatorNE -> 1;
            case R.id.deployScreenResonatorN -> 2;
            case R.id.deployScreenResonatorNW -> 3;
            case R.id.deployScreenResonatorW -> 4;
            case R.id.deployScreenResonatorSW -> 5;
            case R.id.deployScreenResonatorS -> 6;
            case R.id.deployScreenResonatorSE -> 7;
            default -> {
                Log.e("ActivityDeploy", "Unknown resonator widget id: " + id);
                yield -99999;
                // FIXME: maybe this should be a throw
            }
        };
    }

    private String resoSlotToOctantText(int slot) {
        // not sure if correct - cf intel.js eastAnticlockwiseToNorthClockwise
        // also window.OCTANTS = ['E', 'NE', 'N', 'NW', 'W', 'SW', 'S', 'SE'];
        return switch (slot) {
            case 0 -> "E";
            case 2 -> "N";
            case 4 -> "W";
            case 6 -> "S";
            default -> "";
        };
    }

}

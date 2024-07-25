package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getLevelColor;
import static net.opengress.slimgress.API.Common.Utils.getPrettyDistanceString;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Game.Inventory;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;

import java.util.HashMap;

// FIXME user can't deploy on portal if portal belongs to wrong team!
// maybe resonators should show PORTAL team colour instead of owner team colour
public class ActivityDeploy extends AppCompatActivity {

    private final IngressApplication mApp = IngressApplication.getInstance();
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
        String error = data.getString("Exception");
        if (error == null) {
            error = data.getString("Error");
        }
        if (error != null && !error.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(error).setTitle("Error");
            AlertDialog dialog = builder.create();
            dialog.show();
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
//        mGame.connectSignalLocationUpdated(this::onReceiveLocation);
        mApp.getLocationViewModel().getLocationData().observe(this, this::onReceiveLocation);

    }

    @SuppressLint("DefaultLocale")
    public void setUpView() {
        GameEntityPortal portal = mGame.getCurrentPortal();

        // hide a reso (don't do this)
//        findViewById(R.id.deployScreenResonatorNW).setVisibility(View.INVISIBLE);
        // set text on reso (do do this)
//        ((TextView)(findViewById(R.id.deployScreenResonatorNE).findViewById(R.id.positionText))).setText("asdasd");

        findViewById(R.id.button9).setVisibility(View.INVISIBLE);
        findViewById(R.id.button9).setEnabled(false);

        // iterate over the resos (maybe unrolled) and set up their info and any relevant callbacks
        for (int id : mResoViewIds) {
            ((Button) findViewById(id).findViewById(R.id.widgetActionButton)).setText(R.string.dply);
            findViewById(id).findViewById(R.id.widgetActionButton).setOnClickListener(this::onDeployButtonPressed);
            findViewById(id).findViewById(R.id.widgetActionButton).setTag(id);
            // we don't do this in the reso widget because it's also used for recharging
            ((TextView) findViewById(id).findViewById(R.id.resoLevelText)).setText(R.string.l0);
        }

        for (var reso : portal.getPortalResonators()) {
            if (reso == null) {
                continue;
            }
            var widget = findViewById(resoSlotToLayoutId(reso.slot));
            ((TextView) widget.findViewById(R.id.resoLevelText)).setText(String.format("L%d", reso.level));
            int levelColour = getLevelColor(reso.level);
            ((TextView) widget.findViewById(R.id.resoLevelText)).setTextColor(getResources().getColor(levelColour, null));
            ((TextView) widget.findViewById(R.id.positionText)).setText(String.format("%dm %s", reso.distanceToPortal, resoSlotToOctantText(reso.slot)));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setText(mGame.getAgentName(reso.ownerGuid));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setTextColor(0xff000000 + portal.getPortalTeam().getColour());

            // TODO don't offer upgrade button if user can't upgrade
            // maybe put this into the button's tag with setTag?
            var resosForUpgrade = inventory.getResosForUpgrade(reso.level);
            if (resosForUpgrade.isEmpty()) {
                widget.findViewById(R.id.widgetActionButton).setVisibility(View.INVISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(false);
            } else {
                widget.findViewById(R.id.widgetActionButton).setVisibility(View.VISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(true);
                ((Button) widget.findViewById(R.id.widgetActionButton)).setText(R.string.upgd);
                widget.findViewById(R.id.widgetActionButton).setOnClickListener(this::onUpgradeButtonPressed);
            }
            ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).setMax(reso.getMaxEnergy());
            ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).setProgress(reso.energyTotal);
            ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).getProgressDrawable().setTint(0xff000000 + portal.getPortalTeam().getColour());
        }

        int dist = (int) mGame.getLocation().getLatLng().distanceToAsDouble(mGame.getCurrentPortal().getPortalLocation().getLatLng());
        updateInfoText(dist);
        setButtonsEnabled(dist <= mActionRadiusM);
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private void onUpgradeButtonPressed(View view) {
        int slot = layoutIdToResoSlot((Integer) view.getTag());
        Log.d("ActivityDeploy", "Pressed UPGRADE button: " + slot);
        var reso = mGame.getCurrentPortal().getPortalResonator(slot);
        var resosForUpgrade = inventory.getResosForUpgrade(reso.level);
        HashMap<Integer, String> levels = new HashMap<>();
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (var r : resosForUpgrade) {
            int level = r.getItemLevel();
            if (!levels.containsKey(level)) {
                levels.put(level, String.format("Level %d (x1)", level));
                counts.put(level, 1);
            } else {
                int count = counts.get(level);
                counts.put(level, count+1);
                levels.put(level, String.format("Level %d (x%d)", r.getItemLevel(), count+1));
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Resonator");
//        builder.setIcon(R.drawable.ic_format_list_bulleted_black_24dp);

        builder.setItems(levels.values().toArray(new String[0]), (dialogInterface, i) -> {
            var which = levels.keySet().toArray(new Integer[0])[i];
            for (var r : resosForUpgrade) {
                if (r.getItemAccessLevel() == which) {
                    mGame.intUpgradeResonator(r, mGame.getCurrentPortal(), slot, deployResultHandler);
                    break;
                }
            }
            Log.e("ActivityDeploy", String.format("Picked resonator: %d on slot %d", which, slot));
        });
        builder.show();
        Log.e("ActivityDeploy", "Resos for upgrade:" + levels);
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private void onDeployButtonPressed(View view) {
        int slot = layoutIdToResoSlot((Integer) view.getTag());
        Log.d("ActivityDeploy", "Pressed DEPLOY button: " + slot);
        var resosForUpgrade = inventory.getResosForUpgrade(0);
        HashMap<Integer, String> levels = new HashMap<>();
        HashMap<Integer, Integer> counts = new HashMap<>();
        for (var r : resosForUpgrade) {
            int level = r.getItemLevel();
            if (!levels.containsKey(level)) {
                levels.put(level, String.format("Level %d (x1)", level));
                counts.put(level, 1);
            } else {
                int count = counts.get(level);
                counts.put(level, count+1);
                levels.put(level, String.format("Level %d (x%d)", r.getItemLevel(), count+1));
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Resonator");
//        builder.setIcon(R.drawable.ic_format_list_bulleted_black_24dp);

        builder.setItems(levels.values().toArray(new String[0]), (dialogInterface, i) -> {
            var which = levels.keySet().toArray(new Integer[0])[i];
            for (var r : resosForUpgrade) {
                if (r.getItemAccessLevel() == which) {
                    mGame.intDeployResonator(r, mGame.getCurrentPortal(), slot, deployResultHandler);
                    break;
                }
            }
            Log.e("ActivityDeploy", String.format("Picked resonator: %d on slot %d", which, slot));
        });
        builder.show();
        Log.d("ActivityDeploy", "Resos for deployment:" + levels);
    }

    private void onReceiveLocation(Location location) {
        if (location != null) {
            int dist = (int) location.getLatLng().distanceToAsDouble(mGame.getCurrentPortal().getPortalLocation().getLatLng());
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
        // FIXME make these into separate fields and set colours
        String distanceText = getPrettyDistanceString(dist);
        GameEntityPortal portal = mGame.getCurrentPortal();
        String portalInfoText = "LVL: L" + portal.getPortalLevel() + "\n"
                + "RNG: " + portal.getPortalLinkRange() + "m\n"
                + "ENR: " + portal.getPortalEnergy() + " / " + portal.getPortalMaxEnergy() + "\n"
//                + "MOD: [unimplemented]\n"
//                + "MOD: [unimplemented]\n"
//                + "MOD: [unimplemented]\n"
//                + "MOD: [unimplemented]\n"
//                + "LNK: 0 in, 0 out (unimplemented)"
                + "DST: " + distanceText;
        ((TextView) (findViewById(R.id.deployScreenPortalInfo))).setText(portalInfoText);
    }

    private int resoSlotToLayoutId(int slot) {
        // FIXME not sure if correct - cf intel.js eastAnticlockwiseToNorthClockwise
        // also window.OCTANTS = ['E', 'NE', 'N', 'NW', 'W', 'SW', 'S', 'SE'];
        switch (slot) {
            case 1:
                return R.id.deployScreenResonatorE;
            case 2:
                return R.id.deployScreenResonatorNE;
            case 3:
                return R.id.deployScreenResonatorN;
            case 4:
                return R.id.deployScreenResonatorNW;
            case 5:
                return R.id.deployScreenResonatorW;
            case 6:
                return R.id.deployScreenResonatorSW;
            case 7:
                return R.id.deployScreenResonatorS;
            case 8:
                return R.id.deployScreenResonatorSE;
            default:
                Log.e("ActivityDeploy", "Unknown resonator slot: " + slot);
                // FIXME: maybe this should be a throw
                return -99999;
        }
    }

    // doesn't matter that these are non-constant because this project isn't a library. caveat.
    @SuppressLint("NonConstantResourceId")
    private int layoutIdToResoSlot(int id) {
        switch (id) {
            case R.id.deployScreenResonatorE:
                return 1;
            case R.id.deployScreenResonatorNE:
                return 2;
            case R.id.deployScreenResonatorN:
                return 3;
            case R.id.deployScreenResonatorNW:
                return 4;
            case R.id.deployScreenResonatorW:
                return 5;
            case R.id.deployScreenResonatorSW:
                return 6;
            case R.id.deployScreenResonatorS:
                return 7;
            case R.id.deployScreenResonatorSE:
                return 8;
            default:
                Log.e("ActivityDeploy", "Unknown resonator widget id: " + id);
                // FIXME: maybe this should be a throw
                return -99999;
        }
    }

    private String resoSlotToOctantText(int slot) {
        // FIXME not sure if correct - cf intel.js eastAnticlockwiseToNorthClockwise
        // also window.OCTANTS = ['E', 'NE', 'N', 'NW', 'W', 'SW', 'S', 'SE'];
        switch (slot) {
            case 1:
                return "E";
            case 3:
                return "N";
            case 5:
                return "W";
            case 7:
                return "S";
            case 2:
            case 4:
            case 6:
            case 8:
            default:
                return "";
        }
    }

}

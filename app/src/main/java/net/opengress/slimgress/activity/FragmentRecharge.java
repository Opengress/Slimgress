package net.opengress.slimgress.activity;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.updateInfoText;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal.LinkedResonator;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.dialog.DialogInfo;

import org.jetbrains.annotations.Contract;

import java.text.MessageFormat;
import java.util.List;

public class FragmentRecharge extends Fragment {

    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();
    private final int[] mResoViewIds = {
            R.id.rechargeScreenResonatorE,
            R.id.rechargeScreenResonatorNE,
            R.id.rechargeScreenResonatorN,
            R.id.rechargeScreenResonatorNW,
            R.id.rechargeScreenResonatorW,
            R.id.rechargeScreenResonatorSW,
            R.id.rechargeScreenResonatorS,
            R.id.rechargeScreenResonatorSE,
    };
    private GameEntityPortal mPortal;
    private View mRootView;

    private Location mOldLocation;
    private ItemPortalKey mPortalKey = null;
    private int mDistance;

    private final Handler rechargeResultHandler = new Handler(msg -> {
        var data = msg.getData();
        String error = getErrorStringFromAPI(data);
        if (error != null && !error.isEmpty()) {
            Activity activity = getActivity();
            if (activity == null) {
                return false;
            }
            DialogInfo dialog = new DialogInfo(activity);
            dialog.setMessage(error).setDismissDelay(1500).show();
            SlimgressApplication.postPlainCommsMessage(MessageFormat.format("Recharge failed ({0})", error));
        } else {
            // FIXME maybe this should say the number of resonators you actually charged, not the number of resonators you asked about
            SlimgressApplication.postPlainCommsMessage(MessageFormat.format("Recharged {0} resonator(s)", data.getInt("recharged")));
        }
        setUpView();
        return false;
    });

    @NonNull
    public static FragmentRecharge newInstance(String guid) {
        FragmentRecharge fragment = new FragmentRecharge();
        Bundle args = new Bundle();
        args.putSerializable("guid", guid);
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRootView = inflater.inflate(R.layout.activity_recharge, container, false);

        String portalGuid = getArguments().getString("guid");
        if (portalGuid != null) {
            mPortal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(portalGuid);
            if (mPortal != null) {
                mPortalKey = mGame.getInventory().getKeyForPortal(portalGuid);
                mDistance = (int) mGame.getLocation().distanceTo(mPortal.getPortalLocation());
                setUpView();
                mApp.getUpdatedEntitiesViewModel().getEntities().observe(getViewLifecycleOwner(), this::checkForUpdates);
                mApp.getLocationViewModel().getLocationData().observe(getViewLifecycleOwner(), this::onReceiveLocation);
            } else {
                Log.e("FragRecharge", "Portal not found for GUID: " + portalGuid);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        } else {
            Log.e("FragRecharge", "No portal GUID provided");
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }
        return mRootView;
    }

    private void checkForUpdates(List<GameEntityBase> gameEntityBases) {
        for (GameEntityBase entity : gameEntityBases) {
            if (entity.getEntityGuid().equals(mPortal.getEntityGuid())) {
                setUpView();
            }
        }
    }

    @SuppressLint({"DefaultLocale", "ObsoleteSdkInt"})
    public void setUpView() {
        boolean teamOK = mPortal.getPortalTeam().toString().equals(mGame.getAgent().getTeam().toString());
        mRootView.findViewById(R.id.btnRechargeAll).setOnClickListener(this::onRechargeButtonPressed);
        mRootView.findViewById(R.id.btnRechargeAll).setOnLongClickListener(this::onRechargeButtonLongPressed);
        for (int id : mResoViewIds) {
            mRootView.findViewById(id).findViewById(R.id.widgetActionButton).setOnClickListener(this::onRechargeButtonPressed);
            mRootView.findViewById(id).findViewById(R.id.widgetActionButton).setOnLongClickListener(this::onRechargeButtonLongPressed);
            mRootView.findViewById(id).findViewById(R.id.widgetActionButton).setTag(id);
            mRootView.findViewById(id).findViewById(R.id.widgetActionButton).setEnabled(false);
        }

        List<LinkedResonator> resos = mPortal.getPortalResonators();
        boolean canRecharge = false;

        // iterate again to set up resonator deployment user interface
        for (int i = 0; i < resos.size(); i++) {
            LinkedResonator reso = resos.get(i);
            View widget = mRootView.findViewById(resoSlotToLayoutId(i));

            if (reso == null) {
                // Resonator is null, disable and hide its action button
                widget.findViewById(R.id.widgetActionButton).setVisibility(INVISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(false);

                // Optionally clear resonator-specific UI elements
                ((TextView) widget.findViewById(R.id.resoLevelText)).setText("");
                ((TextView) widget.findViewById(R.id.positionText)).setText("");
                ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setText("");
                ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).setProgress(0);
                continue;
            }

            // If the resonator is not null, proceed with setting up its UI
            ((TextView) widget.findViewById(R.id.resoLevelText)).setText(String.format("L%d", reso.level));
            int levelColour = getLevelColour(reso.level);
            ((TextView) widget.findViewById(R.id.resoLevelText)).setTextColor(getColourFromResources(getResources(), levelColour));
            ((TextView) widget.findViewById(R.id.positionText)).setText(String.format("%dm %s", reso.distanceToPortal, resoSlotToOctantText(reso.slot)));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setText(mGame.getAgentName(reso.ownerGuid));
            ((TextView) widget.findViewById(R.id.widgetBtnOwner)).setTextColor(0xff000000 + mPortal.getPortalTeam().getColour());

            if (teamOK && canRecharge(reso)) {
                canRecharge = true;
                widget.findViewById(R.id.widgetActionButton).setVisibility(VISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(true);
            } else {
                widget.findViewById(R.id.widgetActionButton).setVisibility(INVISIBLE);
                widget.findViewById(R.id.widgetActionButton).setEnabled(false);
            }

            ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).setMax(reso.getMaxEnergy());
            ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).setProgress(reso.energyTotal);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).getProgressDrawable().setTint(0xff000000 + mPortal.getPortalTeam().getColour());
            } else {
                ((ProgressBar) widget.findViewById(R.id.resoHealthBar)).getProgressDrawable().setColorFilter(0xff000000 + mPortal.getPortalTeam().getColour(), PorterDuff.Mode.SRC_IN);
            }
        }


        if (canRecharge) {
            mRootView.findViewById(R.id.btnRechargeAll).setVisibility(VISIBLE);
            mRootView.findViewById(R.id.btnRechargeAll).setEnabled(true);
        } else {
            mRootView.findViewById(R.id.btnRechargeAll).setVisibility(INVISIBLE);
            mRootView.findViewById(R.id.btnRechargeAll).setEnabled(false);
        }

        updateInfoText(mDistance, mPortal, mRootView.findViewById(R.id.rechargeScreenPortalInfo));
        if (mDistance > mActionRadiusM) {
            int e = (int) Math.floor(100 * (1 - ((double) mDistance / (500_000 * mGame.getAgent().getLevel()))));
            e = e >= 50 ? e : 0;
            mRootView.<TextView>findViewById(R.id.rechargeEfficiencyInfo).setText(MessageFormat.format("Range efficiency: {0}%", e));
            mRootView.findViewById(R.id.rechargeEfficiencyInfo).setVisibility(VISIBLE);
        } else {
            mRootView.findViewById(R.id.rechargeEfficiencyInfo).setVisibility(INVISIBLE);
        }
        setButtonsEnabled(canRemoteRecharge(mDistance) || mDistance <= mActionRadiusM);
        mRootView.findViewById(R.id.rechargeScreen_back_button).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
    }

    boolean canRemoteRecharge(int dist) {
        if (mPortalKey == null) {
            return false;
        }

        return dist <= mGame.getAgent().getLevel() * 250_000;
    }

    private boolean canRecharge(LinkedResonator reso) {
        if (mGame.getAgent().getEnergy() < 250) { // knob
            return false;
        }
        if (reso != null && reso.energyTotal >= reso.getMaxEnergy()) {
            return false;
        }
        return canRemoteRecharge(mDistance) || mDistance < mActionRadiusM;
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private void onRechargeButtonPressed(@NonNull View view) {
        // FIXME distinguish between local and remote recharge
        int[] slots = getSlots(view);
        if (mDistance <= mActionRadiusM) {
            mGame.intRechargePortal(mPortal, slots, false, rechargeResultHandler);
        } else if (canRemoteRecharge(mDistance)) {
            mGame.intRemoteRechargePortal(mPortalKey, slots, false, rechargeResultHandler);
        }
    }

    @SuppressLint("DefaultLocale")
    @SuppressWarnings("ConstantConditions")
    private boolean onRechargeButtonLongPressed(@NonNull View view) {
        // FIXME distinguish between local and remote recharge
        int[] slots = getSlots(view);
        if (mDistance <= mActionRadiusM) {
            mGame.intRechargePortal(mPortal, slots, true, rechargeResultHandler);
        } else if (canRemoteRecharge(mDistance)) {
            mGame.intRemoteRechargePortal(mPortalKey, slots, true, rechargeResultHandler);
        } else {
            return false;
        }
        return true;
    }

    private int[] getSlots(@NonNull View view) {
        Object tag = view.getTag();
        int[] slots;
        if (tag == null) {
//            slots = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
            slots = mPortal.getPortalResonatorSlots();
        } else {
            slots = new int[]{layoutIdToResoSlot((int) tag)};
        }
        return slots;
    }

    private void onReceiveLocation(Location location) {
        if (location != null) {
            if (location.approximatelyEqualTo(mOldLocation)) {
                return;
            }
            mDistance = (int) location.distanceTo(mPortal.getPortalLocation());
            setButtonsEnabled(canRemoteRecharge(mDistance) || mDistance <= mActionRadiusM);
            updateInfoText(mDistance, mPortal, mRootView.findViewById(R.id.rechargeScreenPortalInfo));
        } else {
            // FIXME is this really a good idea if you have a key?
            setButtonsEnabled(false);
            updateInfoText(999999000, mPortal, mRootView.findViewById(R.id.rechargeScreenPortalInfo));
        }
        mOldLocation = location;
    }

    private void setButtonsEnabled(boolean shouldEnableButton) {
        for (int id : mResoViewIds) {
            mRootView.findViewById(id).findViewById(R.id.widgetActionButton).setEnabled(shouldEnableButton);
        }
        mRootView.findViewById(R.id.btnRechargeAll).setEnabled(shouldEnableButton);
    }

    private int resoSlotToLayoutId(int slot) {
        // not sure if correct - cf intel.js eastAnticlockwiseToNorthClockwise
        // also window.OCTANTS = ['E', 'NE', 'N', 'NW', 'W', 'SW', 'S', 'SE'];
        return switch (slot) {
            case 0 -> R.id.rechargeScreenResonatorE;
            case 1 -> R.id.rechargeScreenResonatorNE;
            case 2 -> R.id.rechargeScreenResonatorN;
            case 3 -> R.id.rechargeScreenResonatorNW;
            case 4 -> R.id.rechargeScreenResonatorW;
            case 5 -> R.id.rechargeScreenResonatorSW;
            case 6 -> R.id.rechargeScreenResonatorS;
            case 7 -> R.id.rechargeScreenResonatorSE;
            default -> {
                Log.e("FragmentDeploy", "Unknown resonator slot: " + slot);
                yield -99999;
                // maybe this should be a throw
            }
        };
    }

    // doesn't matter that these are non-constant because this project isn't a library. caveat.
    @SuppressLint("NonConstantResourceId")
    private int layoutIdToResoSlot(int id) {
        return switch (id) {
            case R.id.rechargeScreenResonatorE -> 0;
            case R.id.rechargeScreenResonatorNE -> 1;
            case R.id.rechargeScreenResonatorN -> 2;
            case R.id.rechargeScreenResonatorNW -> 3;
            case R.id.rechargeScreenResonatorW -> 4;
            case R.id.rechargeScreenResonatorSW -> 5;
            case R.id.rechargeScreenResonatorS -> 6;
            case R.id.rechargeScreenResonatorSE -> 7;
            default -> {
                Log.e("FragmentDeploy", "Unknown resonator widget id: " + id);
                yield -99999;
                // FIXME: maybe this should be a throw
            }
        };
    }

    @NonNull
    @Contract(pure = true)
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

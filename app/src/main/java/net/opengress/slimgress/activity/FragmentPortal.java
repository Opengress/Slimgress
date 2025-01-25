package net.opengress.slimgress.activity;

import static androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_IMAGE_RESOLUTION_NONE;
import static net.opengress.slimgress.ViewHelpers.formatNumberToKLocalised;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getPrettyDistanceString;
import static net.opengress.slimgress.ViewHelpers.getPrettyItemName;
import static net.opengress.slimgress.ViewHelpers.putItemInMap;
import static net.opengress.slimgress.ViewHelpers.saveScreenshot;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Common.Utils;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.dialog.DialogInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// TODO: See if you can squeeze a local linkabaility check or two in here
public class FragmentPortal extends Fragment {

    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();
    private boolean mIsHacking = false;
    private GameEntityPortal mPortal;
    private View mRootView;
    private Location mOldLocation;
    /*
    3, 2, 1, 0
    4, 5, 6, 7
     */
    private final int[] mResoProgressBarViewIds = {
            R.id.activityPortalResoProgressBar1,
            R.id.activityPortalResoProgressBar2,
            R.id.activityPortalResoProgressBar3,
            R.id.activityPortalResoProgressBar4,
            R.id.activityPortalResoProgressBar5,
            R.id.activityPortalResoProgressBar6,
            R.id.activityPortalResoProgressBar7,
            R.id.activityPortalResoProgressBar8,
    };
    private final int[] mResoLabelIds = {
            R.id.activityPortalResoLabel1,
            R.id.activityPortalResoLabel2,
            R.id.activityPortalResoLabel3,
            R.id.activityPortalResoLabel4,
            R.id.activityPortalResoLabel5,
            R.id.activityPortalResoLabel6,
            R.id.activityPortalResoLabel7,
            R.id.activityPortalResoLabel8,
    };

    @SuppressLint({"DefaultLocale", "ObsoleteSdkInt", "SetTextI18n"})
    private void setUpView() {
        if (isDetached() || getContext() == null) {
            return;
        }
        int TEAM_COLOR = 0xFF000000 + mPortal.getPortalTeam().getColour();

        ((TextView) mRootView.findViewById(R.id.portalTitle)).setText(mPortal.getPortalTitle());

        String portalLevel = "L" + Math.max(1, mPortal.getPortalLevel());
        ((TextView) mRootView.findViewById(R.id.portalLevel)).setText(portalLevel);
        int levelColour = getLevelColour(mPortal.getPortalLevel());
        ((TextView) mRootView.findViewById(R.id.portalLevel)).setTextColor(getColourFromResources(getResources(), levelColour));

        ((TextView) mRootView.findViewById(R.id.portalEnergy)).setText(getString(R.string.portal_energy, formatNumberToKLocalised(mPortal.getPortalEnergy())));


        // TODO: link to photostream with portal description, up/downvotes, whatever
        BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
        String desiredResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
        if (Objects.equals(desiredResolution, UNTRANSLATABLE_IMAGE_RESOLUTION_NONE)) {
            Glide.with(this)
                    .load(R.drawable.no_image)
                    .into((ImageView) mRootView.findViewById(R.id.portalImage));
        } else {
            Glide.with(this)
                    .load(mPortal.getPortalImageUrl())
                    .placeholder(R.drawable.no_image)
                    .error(R.drawable.no_image)
                    .into((ImageView) mRootView.findViewById(R.id.portalImage));
        }

        HashSet<String> guids = new HashSet<>();
        for (var reso : mPortal.getPortalResonators()) {
            if (reso != null) {
                guids.add(reso.ownerGuid);
            }
        }
        for (var reso : mPortal.getPortalMods()) {
            if (reso != null) {
                guids.add(reso.installingUser);
            }
        }
        if (mPortal.getOwnerGuid() == null) {
            ((TextView) mRootView.findViewById(R.id.portalOwner)).setText(R.string.uncaptured);
        } else {
            guids.add(mPortal.getOwnerGuid());
        }

        // FIXME everything is hardcoded
        String discoverer = "SYSTEM";
        int discovererTeamColour = 0xFF000000 + mGame.getKnobs().getTeamKnobs().fromString("system").getColour();

        if (!mPortal.getPortalAttribution().isEmpty()) {
            discoverer = mPortal.getPortalAttribution();
        } else if (mPortal.getDiscoverer() != null) {
            discoverer = mPortal.getDiscoverer().getPlain();
            discovererTeamColour = 0xFF000000 + mPortal.getDiscoverer().getTeam().getColour();
            guids.add(mPortal.getDiscoverer().getGUID());
        }

        ((TextView) mRootView.findViewById(R.id.activityPortalCurrentAttributionText)).setText("by " + discoverer);
        ((TextView) mRootView.findViewById(R.id.activityPortalCurrentAttributionText)).setTextColor(discovererTeamColour);

        var unknownGuids = mGame.checkAgentNames(guids);

        if (unknownGuids.isEmpty()) {
            if (mPortal.getOwnerGuid() != null) {
                ((TextView) mRootView.findViewById(R.id.portalOwner)).setText(mGame.getAgentName(mPortal.getOwnerGuid()));
            }
        } else {
            Handler ownerResultHandler = new Handler(msg -> {
                ((TextView) mRootView.findViewById(R.id.portalOwner)).setText(msg.getData().getString(mPortal.getOwnerGuid()));
                HashMap<String, String> names = new HashMap<>();
                for (var guid : guids) {
                    names.put(guid, msg.getData().getString(guid));
                }
                mGame.putAgentNames(names);
                setUpView();
                return false;
            });
            mApp.runInThread_(() -> mGame.intGetNicknamesFromUserGUIDs(guids.toArray(new String[0]), ownerResultHandler));
        }
        ((TextView) mRootView.findViewById(R.id.portalOwner)).setTextColor(TEAM_COLOR);

        for (int i = 0; i < mPortal.getPortalResonators().size(); i++) {
            ProgressBar meter = mRootView.findViewById(mResoProgressBarViewIds[i]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                meter.setProgressTintList(ColorStateList.valueOf(TEAM_COLOR));
            } else {
                Drawable progressDrawable = meter.getProgressDrawable().mutate();
                progressDrawable.setColorFilter(TEAM_COLOR, PorterDuff.Mode.SRC_IN);
                meter.setProgressDrawable(progressDrawable);
            }
            TextView label = mRootView.findViewById(mResoLabelIds[i]);

            GameEntityPortal.LinkedResonator reso = mPortal.getPortalResonator(i);
            if (reso == null) {
                meter.setProgress(0);
                label.setVisibility(View.INVISIBLE);
                continue;
            }

            label.setText(String.format("L%d", reso.level));
            int resoLevelColour = getLevelColour(reso.level);
            label.setTextColor(getColourFromResources(getResources(), resoLevelColour));
            label.setVisibility(View.VISIBLE);
            meter.setProgress((int) ((float) reso.energyTotal / (float) reso.getMaxEnergy() * (float) 100));

        }

    }

    @Override
    public void onResume() {
        super.onResume();
        setUpView();
        checkKeys();
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    public void checkKeys() {
        List<ItemPortalKey> keys = mGame.getInventory().getKeysForPortal(mPortal);
        mRootView.findViewById(R.id.activityPortalKeyButton).setEnabled(!keys.isEmpty());
        ((TextView) mRootView.findViewById(R.id.activityPortalKeyCount)).setText("x" + keys.size());
        mRootView.findViewById(R.id.activityPortalKeyButton).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());

            builder.setTitle("Portal Key");
            builder.setMessage(String.format("You are holding %d keys. This dialog will not prompt for confirmation or ask for quantities.", keys.size()));
            builder.setCancelable(false);
            builder.setPositiveButton("Drop", (dialog, which) -> mGame.intDropItem(keys.get(0), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo eDialog = new DialogInfo(requireActivity());
                    eDialog.setMessage(error).setDismissDelay(1500).show();
                    SlimgressApplication.postPlainCommsMessage("Drop failed: " + error);
                } else {
                    // could say what we dropped
                    SlimgressApplication.postPlainCommsMessage("Drop successful");
                    Toast.makeText(requireActivity(), "Drop successful", Toast.LENGTH_SHORT).show();
                    keys.remove(0);
                }
                checkKeys();
                return false;
            })));
            builder.setNegativeButton("Recycle", (dialog, which) -> mGame.intRecycleItem(keys.get(0), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo eDialog = new DialogInfo(requireActivity());
                    eDialog.setMessage(error).setDismissDelay(1500).show();
                    SlimgressApplication.postPlainCommsMessage("Recycle failed: " + error);
                } else {
                    var res = data.getString("result");
                    String message = String.format("Gained %s XM from recycling a %s", res, keys.get(0).getUsefulName());
                    SlimgressApplication.postPlainCommsMessage(message);
//                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    keys.remove(0);
                }
                checkKeys();
                return false;
            })));
            builder.setNeutralButton("Cancel", (dialog, which) -> {
            });
            builder.show();
        });
    }

    @NonNull
    public static FragmentPortal newInstance(String guid) {
        FragmentPortal fragment = new FragmentPortal();
        Bundle args = new Bundle();
        args.putSerializable("guid", guid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRootView = inflater.inflate(R.layout.activity_portal, container, false);

        String portalGuid = getArguments().getString("guid");
        if (portalGuid != null) {
            mPortal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(portalGuid);
            if (mPortal == null) {
                Log.e("FragmentPortal", "Portal not found for GUID: " + portalGuid);
                finish();
            }
        } else {
            Log.e("FragmentPortal", "No portal GUID provided");
            finish();
        }

        Handler hackResultHandler = new Handler(msg -> {
            Bundle hackresultBundle = generateHackResultBundle(msg.getData());
            mGame.addHackResult(hackresultBundle);
            requireActivity().getSupportFragmentManager().popBackStack(null, POP_BACK_STACK_INCLUSIVE);
            return false;
        });

        mRootView.findViewById(R.id.hackButton).setOnClickListener(v -> {
            mRootView.findViewById(R.id.hackButton).setEnabled(false);
            ((Button) mRootView.findViewById(R.id.hackButton)).setText(R.string.hacking_in_progress);
            mIsHacking = true;
            mGame.intHackPortal(mPortal, hackResultHandler);
        });
        mRootView.findViewById(R.id.hackButton).setOnLongClickListener(v -> {
            // TODO: upgrade to glyph hacking stuff
            mRootView.findViewById(R.id.hackButton).setEnabled(false);
            ((Button) mRootView.findViewById(R.id.hackButton)).setText(R.string.hacking_in_progress);
            mGame.intHackPortal(mPortal, hackResultHandler);
            mIsHacking = true;
            return false;
        });

        mRootView.findViewById(R.id.deployButton).setEnabled(true);
        mRootView.findViewById(R.id.deployButton).setOnClickListener(v -> {
            FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, FragmentDeploy.newInstance(mPortal.getEntityGuid()), "DEPLOY");
            transaction.addToBackStack("DEPLOY");
            transaction.commit();
        });

        // FIXME do not tease the user with this button if they are out of range and have no key
        mRootView.findViewById(R.id.rechargeButton).setEnabled(true);
        mRootView.findViewById(R.id.rechargeButton).setOnClickListener(v -> {
            FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, FragmentRecharge.newInstance(mPortal.getEntityGuid()), "RECHARGE");
            transaction.addToBackStack("RECHARGE");
            transaction.commit();
        });

        mRootView.findViewById(R.id.modButton).setEnabled(true);
        mRootView.findViewById(R.id.modButton).setOnClickListener(v -> {
            FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, FragmentMod.newInstance(mPortal.getEntityGuid()), "MOD");
            transaction.addToBackStack("MOD");
            transaction.commit();
        });

        mRootView.findViewById(R.id.navigateButton).setOnClickListener(v -> {
            String uri = "geo:?q=" + mPortal.getPortalLocation();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        });

        mRootView.findViewById(R.id.activityPortalLinkButton).setOnClickListener(v -> {
            // TODO re-request any that return TIMEOUT
            mGame.intQueryLinkablilityForPortal(mPortal, mGame.getInventory().getItemsOfType(ItemPortalKey.class), new Handler(msg -> {
                String error = Utils.getErrorStringFromAPI(msg.getData());
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(requireActivity());
                    dialog.setMessage(error).setDismissDelay(1500).show();
                    SlimgressApplication.postPlainCommsMessage("Link check failed: " + error);
                    return false;
                }
                @SuppressWarnings("unchecked")
                List<ItemPortalKey> keys = (List<ItemPortalKey>) msg.getData().getSerializable("result");
                assert keys != null;

                if (keys.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(requireActivity());
                    dialog.setMessage("No linkable portals!").setDismissDelay(1500).show();
                    return false;
                }

                Set<String> uniquePortals = new HashSet<>();
                List<ItemPortalKey> uniqueKeys = new ArrayList<>();

                for (ItemPortalKey key : keys) {
                    if (uniquePortals.add(key.getPortalGuid())) {
                        uniqueKeys.add(key);
                    }
                }

                Collections.sort(uniqueKeys, (k1, k2) -> {
                    double d1 = mPortal.getPortalLocation().distanceTo(new Location(k1.getPortalLocation()));
                    double d2 = mPortal.getPortalLocation().distanceTo(new Location(k2.getPortalLocation()));
                    return Double.compare(d1, d2);
                });

                AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
                ;
                builder.setTitle("Select Target Portal (I will fix this in 2029)");
//        builder.setIcon(R.drawable.ic_format_list_bulleted_black_24dp);

                List<String> portalNames = new ArrayList<>();
                for (int i = 0; i < uniqueKeys.size(); i++) {
                    ItemPortalKey key = uniqueKeys.get(i);
                    double distance = mPortal.getPortalLocation().distanceTo(new Location(key.getPortalLocation()));
                    // You can show the distance in meters/kilometers:
                    portalNames.add(key.getPortalTitle() + " (" + getPrettyDistanceString(distance) + ")");
                }

                builder.setItems(portalNames.toArray(new String[0]), (dialogInterface, i) -> {
                    ItemPortalKey selectedKey = uniqueKeys.get(i);
                    // Proceed to link
                    mGame.intLinkPortal(mPortal, selectedKey, new Handler(m2 -> {
                        String e2 = Utils.getErrorStringFromAPI(m2.getData());
                        if (e2 != null && !e2.isEmpty()) {
                            DialogInfo dialog = new DialogInfo(requireActivity());
                            dialog.setMessage(e2).setDismissDelay(1500).show();
                            SlimgressApplication.postPlainCommsMessage("Link failed: " + e2);
                            mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getLinkCreationCost());
                            return false;
                        }
                        int fields = m2.getData().getInt("numFields");
                        int mu = m2.getData().getInt("mu");
                        if (fields == 1) {
                            Toast.makeText(requireActivity(), String.format("Field created: +%d MU", mu), Toast.LENGTH_SHORT).show();
                        } else if (fields == 2) {
                            Toast.makeText(requireActivity(), String.format("Fields created: +%d MU", mu), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireActivity(), "Link established!", Toast.LENGTH_SHORT).show();
                            SlimgressApplication.postPlainCommsMessage("Link established!");
                        }
                        return false;
                    }));
                    Log.d("PORTAL", "LINKING TO PORTAL! It is " + selectedKey.getPortalTitle());
                });

                builder.show();
                return false;
            }));
        });

        mRootView.findViewById(R.id.portalImage).setOnClickListener(v -> {
            FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, FragmentPhotoRate.newInstance(mPortal.getEntityGuid()), "PHOTORATE");
            transaction.addToBackStack("PHOTORATE");
            transaction.commit();
        });

        mRootView.findViewById(R.id.activityPortalShareButton).setOnClickListener(v -> sharePortal());

        mRootView.findViewById(R.id.activityPortalOkButton).setOnClickListener(v -> finish());


        setButtonsEnabled(mGame.getLocation().getLatLng().distanceTo(mPortal.getPortalLocation().getLatLng()) <= mActionRadiusM);
        mApp.getUpdatedEntitiesViewModel().getEntities().observe(getViewLifecycleOwner(), this::checkForUpdates);
        mApp.getLocationViewModel().getLocationData().observe(getViewLifecycleOwner(), this::onReceiveLocation);
        return mRootView;
    }

    private void checkForUpdates(List<GameEntityBase> gameEntityBases) {
        for (GameEntityBase entity : gameEntityBases) {
            if (entity.getEntityGuid().equals(mPortal.getEntityGuid())) {
                setUpView();
                checkKeys();
            }
        }
    }

    private void finish() {
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    @SuppressLint("DefaultLocale")
    private void sharePortal() {
        // FIXME get a better screen - remote portal view or stats-filled mod view
        // FIXME again - maybe add an intel link or something
        View mainView = requireActivity().getWindow().getDecorView();
        mainView.setDrawingCacheEnabled(true);
        Bitmap combinedBitmap = Bitmap.createBitmap(mainView.getDrawingCache());
        mainView.setDrawingCacheEnabled(false);

        File screenshotFile = saveScreenshot(requireActivity().getExternalCacheDir(), combinedBitmap);

        // Share the screenshot
        Uri screenshotUri = FileProvider.getUriForFile(requireActivity(), requireActivity().getPackageName() + ".fileprovider", screenshotFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, screenshotUri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, String.format("Intel report for #Opengress Portal %s", mPortal.getPortalTitle()));
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    private void onReceiveLocation(Location location) {
        if (location == null) {
            setButtonsEnabled(false);
        } else {
            if (location.approximatelyEqualTo(mOldLocation)) {
                return;
            }
            setButtonsEnabled(location.getLatLng().distanceTo(mPortal.getPortalLocation().getLatLng()) <= mActionRadiusM);
        }
        mOldLocation = location;
    }

    private void setButtonsEnabled(boolean shouldEnableButton) {
        mRootView.findViewById(R.id.hackButton).setEnabled(!mIsHacking && shouldEnableButton);
//        boolean isTesting = mGame.getAgent().getNickname().startsWith("MT") || mGame.getAgent().getNickname().startsWith("I_") || mGame.getAgent().getNickname().startsWith("ca");
        boolean canLink = mPortal.getPortalTeam().equals(mGame.getAgent().getTeam())
                && (mPortal.getPortalResonatorCount() >= 8)
                && mPortal.getLinkCapacity() > mPortal.getOriginEdges().size()
                && mGame.getAgent().getEnergy() >= mGame.getKnobs().getXMCostKnobs().getLinkCreationCost();
        mRootView.findViewById(R.id.activityPortalLinkButton).setEnabled(shouldEnableButton && canLink);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private Bundle generateHackResultBundle(@NonNull Bundle data) {
        Bundle bundle = new Bundle();

        ArrayList<String> guids = data.getStringArrayList("guids");
        ArrayList<String> bonusGuids = data.getStringArrayList("bonusGuids");
        String error = data.getString("Error");
        String exception = data.getString("Exception");

        if (error != null) {
            bundle.putString("error", error);
            return bundle;
        }
        if (exception != null) {
            bundle.putString("error", exception);
            return bundle;
        }

        int portalLevel = mPortal.getPortalLevel();
        Team portalTeam = mPortal.getPortalTeam();
        if (portalTeam.equals(mGame.getAgent().getTeam())) {
            mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getPortalHackFriendlyCostByLevel().get(portalLevel - 1));
        } else if (portalTeam.toString().equalsIgnoreCase("neutral")) {
            mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getPortalHackNeutralCostByLevel().get(portalLevel - 1));
        } else {
            mGame.getAgent().subtractEnergy(mGame.getKnobs().getXMCostKnobs().getPortalHackEnemyCostByLevel().get(portalLevel - 1));
        }

        if ((guids == null || guids.isEmpty()) && (bonusGuids == null || bonusGuids.isEmpty())) {
            bundle.putString("error", "Hack acquired no items");
            return bundle;
        }


        HashMap<String, ItemBase> rawItems = (HashMap<String, ItemBase>) data.getSerializable("items");

        HashMap<String, Integer> items = new HashMap<>();
        HashMap<String, Integer> bonusItems = new HashMap<>();
        if (guids != null && !guids.isEmpty()) {
            for (String guid : guids) {
                assert rawItems != null;
                ItemBase item = Objects.requireNonNull(rawItems.get(guid));
                String name = getPrettyItemName(item, getResources());
                putItemInMap(items, name);
                bundle.putSerializable("items", items);
            }
        }
        // this should always be false until I implement glyph hacking
        if (bonusGuids != null && !bonusGuids.isEmpty()) {
            for (String guid : bonusGuids) {
                assert rawItems != null;
                ItemBase item = Objects.requireNonNull(rawItems.get(guid));
                String name = getPrettyItemName(item, getResources());
                putItemInMap(bonusItems, name);
                bundle.putSerializable("bonusItems", items);
            }
        }

        return bundle;
    }
}

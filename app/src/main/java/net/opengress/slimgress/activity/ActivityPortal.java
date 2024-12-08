package net.opengress.slimgress.activity;

import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_IMAGE_RESOLUTION_NONE;
import static net.opengress.slimgress.ViewHelpers.formatNumberToKLocalized;
import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
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
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Common.Location;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemPortalKey;
import net.opengress.slimgress.dialog.DialogInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class ActivityPortal extends AppCompatActivity {

    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();
    private boolean mIsHacking = false;
    private GameEntityPortal mPortal;
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
        int TEAM_COLOR = 0xFF000000 + mPortal.getPortalTeam().getColour();

        ((TextView) findViewById(R.id.portalTitle)).setText(mPortal.getPortalTitle());

        String portalLevel = "L" + Math.max(1, mPortal.getPortalLevel());
        ((TextView) findViewById(R.id.portalLevel)).setText(portalLevel);
        int levelColour = getLevelColour(mPortal.getPortalLevel());
        ((TextView) findViewById(R.id.portalLevel)).setTextColor(getColourFromResources(getResources(), levelColour));

        ((TextView) findViewById(R.id.portalEnergy)).setText(getString(R.string.portal_energy, formatNumberToKLocalized(mPortal.getPortalEnergy())));


        // TODO: link to photostream with portal description, up/downvotes, whatever
        BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
        String desiredResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
        if (Objects.equals(desiredResolution, UNTRANSLATABLE_IMAGE_RESOLUTION_NONE)) {
            Glide.with(this)
                    .load(R.drawable.no_image)
                    .into((ImageView) findViewById(R.id.portalImage));
        } else {
            Glide.with(this)
                    .load(mPortal.getPortalImageUrl())
                    .placeholder(R.drawable.no_image)
                    .error(R.drawable.no_image)
                    .into((ImageView) findViewById(R.id.portalImage));
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
            ((TextView) findViewById(R.id.portalOwner)).setText(R.string.uncaptured);
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

        ((TextView) findViewById(R.id.activityPortalCurrentAttributionText)).setText("by " + discoverer);
        ((TextView) findViewById(R.id.activityPortalCurrentAttributionText)).setTextColor(discovererTeamColour);

        var unknownGuids = mGame.checkAgentNames(guids);

        if (unknownGuids.isEmpty()) {
            if (mPortal.getOwnerGuid() != null) {
                ((TextView) findViewById(R.id.portalOwner)).setText(mGame.getAgentName(mPortal.getOwnerGuid()));
            }
        } else {
            Handler ownerResultHandler = new Handler(msg -> {
                ((TextView) findViewById(R.id.portalOwner)).setText(msg.getData().getString(mPortal.getOwnerGuid()));
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
        ((TextView) findViewById(R.id.portalOwner)).setTextColor(TEAM_COLOR);

        for (int i = 0; i < mPortal.getPortalResonators().size(); i++) {
            ProgressBar meter = findViewById(mResoProgressBarViewIds[i]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                meter.setProgressTintList(ColorStateList.valueOf(TEAM_COLOR));
            } else {
                Drawable progressDrawable = meter.getProgressDrawable().mutate();
                progressDrawable.setColorFilter(TEAM_COLOR, PorterDuff.Mode.SRC_IN);
                meter.setProgressDrawable(progressDrawable);
            }
            TextView label = findViewById(mResoLabelIds[i]);

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

    @SuppressLint("DefaultLocale")
    public void checkKeys() {
        List<ItemPortalKey> keys = mGame.getInventory().getKeysForPortal(mPortal);
        findViewById(R.id.activityPortalKeyButton).setEnabled(!keys.isEmpty());
        findViewById(R.id.activityPortalKeyButton).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Portal Key");
            builder.setMessage(String.format("You are holding %d keys. This dialog will not prompt for confirmation or ask for quantities.", keys.size()));
            builder.setCancelable(false);
            builder.setPositiveButton("Drop", (dialog, which) -> mGame.intDropItem(keys.get(0), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo eDialog = new DialogInfo(this);
                    eDialog.setMessage(error).setDismissDelay(1500).show();
                    SlimgressApplication.postPlainCommsMessage("Drop failed: " + error);
                } else {
                    // could say what we dropped
                    SlimgressApplication.postPlainCommsMessage("Drop successful");
                    Toast.makeText(this, "Drop successful", Toast.LENGTH_SHORT).show();
                    keys.remove(0);
                }
                checkKeys();
                return false;
            })));
            String name = keys.get(0).getUsefulName();
            builder.setNegativeButton("Recycle", (dialog, which) -> mGame.intRecycleItem(keys.get(0), new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo eDialog = new DialogInfo(this);
                    eDialog.setMessage(error).setDismissDelay(1500).show();
                    SlimgressApplication.postPlainCommsMessage("Recycle failed: " + error);
                } else {
                    var res = data.getString("result");
                    String message = String.format("Gained %s XM from recycling a %s", res, name);
                    SlimgressApplication.postPlainCommsMessage(message);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portal);

        String portalGuid = getIntent().getStringExtra("guid");
        if (portalGuid != null) {
            mPortal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(portalGuid);
            if (mPortal != null) {
                setUpView();
            } else {
                Log.e("ActivityPortal", "Portal not found for GUID: " + portalGuid);
                finish();
            }
        } else {
            Log.e("ActivityPortal", "No portal GUID provided");
            finish();
        }

        Handler hackResultHandler = new Handler(msg -> {
            Bundle hackresultBundle = generateHackResultBundle(msg.getData());
            mGame.addHackResult(hackresultBundle);
            finish();
            return false;
        });

        findViewById(R.id.hackButton).setOnClickListener(v -> {
            findViewById(R.id.hackButton).setEnabled(false);
            ((Button) findViewById(R.id.hackButton)).setText(R.string.hacking_in_progress);
            mIsHacking = true;
            mGame.intHackPortal(mPortal, hackResultHandler);
        });
        findViewById(R.id.hackButton).setOnLongClickListener(v -> {
            // TODO: upgrade to glyph hacking stuff
            findViewById(R.id.hackButton).setEnabled(false);
            ((Button) findViewById(R.id.hackButton)).setText(R.string.hacking_in_progress);
            mGame.intHackPortal(mPortal, hackResultHandler);
            mIsHacking = true;
            return false;
        });

        findViewById(R.id.deployButton).setEnabled(true);
        findViewById(R.id.deployButton).setOnClickListener(v -> {
            Intent myIntent = new Intent(getApplicationContext(), ActivityDeploy.class);
            myIntent.putExtra("guid", mPortal.getEntityGuid());
            startActivity(myIntent);
        });

        findViewById(R.id.modButton).setEnabled(true);
        findViewById(R.id.modButton).setOnClickListener(v -> {
            Intent myIntent = new Intent(getApplicationContext(), ActivityMod.class);
            myIntent.putExtra("guid", mPortal.getEntityGuid());
            startActivity(myIntent);
        });

        findViewById(R.id.navigateButton).setOnClickListener(v -> {
            String uri = "geo:?q=" + mPortal.getPortalLocation();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        });

        findViewById(R.id.activityPortalLinkButton).setOnClickListener(v -> {
            Log.d("PORTAL", "link button pressed!");
        });

        findViewById(R.id.activityPortalShareButton).setOnClickListener(v -> sharePortal());

        findViewById(R.id.activityPortalOkButton).setOnClickListener(v -> finish());


        setButtonsEnabled(mGame.getLocation().getLatLng().distanceTo(mPortal.getPortalLocation().getLatLng()) <= mActionRadiusM);
        mApp.getLocationViewModel().getLocationData().observe(this, this::onReceiveLocation);
    }

    @SuppressLint("DefaultLocale")
    private void sharePortal() {
        // FIXME get a better screen - remote portal view or stats-filled mod view
        // FIXME again - maybe add an intel link or something
        View mainView = getWindow().getDecorView();
        mainView.setDrawingCacheEnabled(true);
        Bitmap combinedBitmap = Bitmap.createBitmap(mainView.getDrawingCache());
        mainView.setDrawingCacheEnabled(false);

        File screenshotFile = saveScreenshot(getExternalCacheDir(), combinedBitmap);

        // Share the screenshot
        Uri screenshotUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", screenshotFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, screenshotUri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, String.format("Intel report for #Opengress Portal %s", mPortal.getPortalTitle()));
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    private void onReceiveLocation(Location location) {
        if (location != null) {
            setButtonsEnabled(location.getLatLng().distanceTo(mPortal.getPortalLocation().getLatLng()) <= mActionRadiusM);
        } else {
            setButtonsEnabled(false);
        }
    }

    private void setButtonsEnabled(boolean shouldEnableButton) {
        findViewById(R.id.hackButton).setEnabled(!mIsHacking && shouldEnableButton);
        boolean isTesting = mGame.getAgent().getNickname().startsWith("MT") || mGame.getAgent().getNickname().startsWith("I_");
        findViewById(R.id.activityPortalLinkButton).setEnabled(shouldEnableButton && isTesting);
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
        if (portalTeam.toString().equalsIgnoreCase(mGame.getAgent().getTeam().toString())) {
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

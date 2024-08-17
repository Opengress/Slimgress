package net.opengress.slimgress;

import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
import static net.opengress.slimgress.ViewHelpers.getPrettyItemName;
import static net.opengress.slimgress.ViewHelpers.putItemInMap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public class ActivityPortal extends AppCompatActivity {

    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();
    private final ActivityResultLauncher<Intent> deployActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
//                    Intent data = result.getData();
                    // It might make more sense to just hook up a signal?
                    setUpView();
                }
            }
    );

    private void setUpView() {
        GameEntityPortal portal = mGame.getCurrentPortal();

        ((TextView) findViewById(R.id.portalTitle)).setText(portal.getPortalTitle());

        String portalLevel = "L" + Math.max(1, portal.getPortalLevel());
        ((TextView) findViewById(R.id.portalLevel)).setText(portalLevel);
        int levelColour = getLevelColor(portal.getPortalLevel());
        ((TextView) findViewById(R.id.portalLevel)).setTextColor(getColorFromResources(getResources(), levelColour));


        // FIXME: format this nicely
        ((TextView) findViewById(R.id.portalEnergy)).setText(getString(R.string.portal_energy, portal.getPortalEnergy()));


        // TODO: link to photostream with portal description, up/downvotes, whatever
        Glide.with(this)
                .load(portal.getPortalImageUrl())
                .placeholder(R.drawable.no_image)
                .error(R.drawable.no_image)
                .into((ImageView) findViewById(R.id.portalImage));

        HashSet<String> guids = new HashSet<>();
        for (var reso : portal.getPortalResonators()) {
            if (reso != null) {
                guids.add(reso.ownerGuid);
            }
        }
        if (portal.getOwnerGuid() != null) {
            guids.add(portal.getOwnerGuid());
        }

        var unknownGuids = mGame.checkAgentNames(guids);

        if (unknownGuids.isEmpty()) {
            ((TextView) findViewById(R.id.portalOwner)).setText(mGame.getAgentName(portal.getOwnerGuid()));
        } else {
            Handler ownerResultHandler = new Handler(msg -> {
                ((TextView) findViewById(R.id.portalOwner)).setText(msg.getData().getString(portal.getOwnerGuid()));
                HashMap<String, String> names = new HashMap<>();
                for (var guid : guids) {
                    names.put(guid, msg.getData().getString(guid));
                }
                mGame.setAgentNames(names);
                return false;
            });
            new Thread(() -> mGame.intGetNicknamesFromUserGUIDs(guids.toArray(new String[0]), ownerResultHandler)).start();
        }
        ((TextView) findViewById(R.id.portalOwner)).setTextColor(0xFF000000 + portal.getPortalTeam().getColour());

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("ActivityPortal", "OnActivityResult called");
        setUpView();
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portal);

        setUpView();
        GameEntityPortal portal = mGame.getCurrentPortal();

        Handler hackResultHandler = new Handler(msg -> {
            Bundle hackresultBundle = generateHackResultBundle(msg.getData());
            Intent myIntent = getIntent();
            setResult(RESULT_OK, myIntent);
            myIntent.putExtra("result", hackresultBundle);
            finish();
            return false;
        });

        findViewById(R.id.hackButton).setOnClickListener(v -> {
            findViewById(R.id.hackButton).setEnabled(false);
            ((Button) findViewById(R.id.hackButton)).setText(R.string.hacking_in_progress);
            mGame.intHackPortal(portal, hackResultHandler);
        });
        findViewById(R.id.hackButton).setOnLongClickListener(v -> {
            // TODO: upgrade to glyph hacking stuff
            findViewById(R.id.hackButton).setEnabled(false);
            ((Button) findViewById(R.id.hackButton)).setText(R.string.hacking_in_progress);
            mGame.intHackPortal(portal, hackResultHandler);
            return false;
        });

        // FIXME make this work OK
        findViewById(R.id.deployButton).setEnabled(true);
        findViewById(R.id.deployButton).setOnClickListener(v -> {
            Intent myIntent = new Intent(getApplicationContext(), ActivityDeploy.class);
            deployActivityResultLauncher.launch(myIntent);
        });
//        ((ProgressBar)findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
//        ((ProgressBar)findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
//
//        String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//        ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//        ((TextView)findViewById(R.id.agentinfo)).setTextColor(textColor);

        setButtonsEnabled(mGame.getLocation().getLatLng().distanceToAsDouble(mGame.getCurrentPortal().getPortalLocation().getLatLng()) <= mActionRadiusM);
        mApp.getLocationViewModel().getLocationData().observe(this, this::onReceiveLocation);
    }

    private void onReceiveLocation(Location location) {
        if (location != null) {
            setButtonsEnabled(location.getLatLng().distanceToAsDouble(mGame.getCurrentPortal().getPortalLocation().getLatLng()) <= mActionRadiusM);
        } else {
            setButtonsEnabled(false);
        }
    }

    private void setButtonsEnabled(boolean shouldEnableButton) {
        findViewById(R.id.hackButton).setEnabled(shouldEnableButton);
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

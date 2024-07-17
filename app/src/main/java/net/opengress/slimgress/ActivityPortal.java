package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getImageBitmap;
import static net.opengress.slimgress.ViewHelpers.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;


import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.GameEntity.GameEntityBase;
import net.opengress.slimgress.API.GameEntity.GameEntityControlField;
import net.opengress.slimgress.API.GameEntity.GameEntityLink;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemBase;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// FIXME: if a portal enters/exits range, enable/disable the hack button etc
public class ActivityPortal extends Activity {

    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();
    private Bitmap mBitmap;
    private final int DEPLOY_INTENT_CODE = 1;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBitmap != null) {
            mBitmap.recycle();
        }
    }

    private void setUpView() {
        GameEntityPortal portal = mGame.getCurrentPortal();

        ((TextView)findViewById(R.id.portalTitle)).setText(portal.getPortalTitle());

        String portalLevel = "L" + portal.getPortalLevel();
        ((TextView)findViewById(R.id.portalLevel)).setText(portalLevel);
        // TODO: level colours
//        ((TextView)findViewById(R.id.portalLevel)).setTextColor();

        // FIXME: format this nicely
        ((TextView)findViewById(R.id.portalEnergy)).setText(getString(R.string.portal_energy, portal.getPortalEnergy()));


        // TODO: link to photostream with portal description, up/downvotes, whatever
        // also maybe hardcode this background image
        ((ImageView)findViewById(R.id.portalImage)).setImageBitmap(getBitmapFromAsset("no_image.png", getAssets()));
        new Thread(() -> {
            mBitmap = getImageBitmap(portal.getPortalImageUrl(), getApplicationContext().getCacheDir());
            if (mBitmap != null) {
                runOnUiThread(() -> ((ImageView) findViewById(R.id.portalImage)).setImageBitmap(mBitmap));
            }
        }).start();

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
            new Thread(() -> mGame.intGetNicknamesFromUserGUIDs(guids.toArray(new String[0]),ownerResultHandler)).start();
        }
        ((TextView)findViewById(R.id.portalOwner)).setTextColor(0xFF000000 + portal.getPortalTeam().getColour());

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

        findViewById(R.id.hackButton).setOnClickListener(v -> mGame.intHackPortal(portal, hackResultHandler));
        findViewById(R.id.hackButton).setOnLongClickListener(v -> {
            // TODO: upgrade to glyph hacking stuff
            mGame.intHackPortal(portal, hackResultHandler);
            return false;
        });

        // FIXME make this work OK
        // testing atm, will try it properly later
        if (mGame.getAgent().getNickname().startsWith("MT")) {
            // tough luck if you don't have the agent names loaded up yet :thinking_face:
            findViewById(R.id.deployButton).setEnabled(true);
            findViewById(R.id.deployButton).setOnClickListener(v -> {
                Intent myIntent = new Intent(getApplicationContext(), ActivityDeploy.class);
                startActivityForResult(myIntent, DEPLOY_INTENT_CODE);
            });
        }
//        ((ProgressBar)findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
//        ((ProgressBar)findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
//
//        String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//        ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//        ((TextView)findViewById(R.id.agentinfo)).setTextColor(textColor);

        setButtonsEnabled(mGame.getLocation().getLatLng().distanceToAsDouble(mGame.getCurrentPortal().getPortalLocation().getLatLng()) <= mActionRadiusM);
        mGame.connectSignalLocationUpdated(this::onReceiveLocation);
    }

    private void onReceiveLocation(Location location) {
        setButtonsEnabled(location.getLatLng().distanceToAsDouble(mGame.getCurrentPortal().getPortalLocation().getLatLng()) <= mActionRadiusM);
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
                ItemBase item = Objects.requireNonNull(rawItems.get(guid));
                String name = getPrettyItemName(item);
                putItemInMap(items, name);
                bundle.putSerializable("items", items);
            }
        }
        // this should always be false until I implement glyph hacking
        if (bonusGuids != null && !bonusGuids.isEmpty()) {
            for (String guid : bonusGuids) {
                ItemBase item = Objects.requireNonNull(rawItems.get(guid));
                String name = getPrettyItemName(item);
                putItemInMap(bonusItems, name);
                bundle.putSerializable("bonusItems", items);
            }
        }

        return bundle;
    }

    private void putItemInMap(@NonNull HashMap<String, Integer> items, String name) {
        if (!items.containsKey(name)) {
            items.put(name, 1);
        } else {
            items.put(name, Objects.requireNonNull(items.get(name)));
        }
    }

    @NonNull
    private String getPrettyItemName(@NonNull ItemBase item) {
        String level;
        // rarity will maybe eventually expressed by colour, not text. that's why html
        switch (item.getItemRarity()) {
            case VeryCommon:
                level = "VC ";
                break;
            case Common:
                level = "";
                break;
            case LessCommon:
                level = "LC ";
                break;
            case Rare:
                level = "R ";
                break;
            case VeryRare:
                level = "VR ";
                break;
            case ExtraRare:
                level = "ER ";
                break;
            case None:
            default:
                if (item.getItemLevel() == 0) {
                    level = "";
                } else {
                    level = "L"+item.getItemLevel()+" ";
                }
        }

        return level + item.getDisplayName();
    }

}

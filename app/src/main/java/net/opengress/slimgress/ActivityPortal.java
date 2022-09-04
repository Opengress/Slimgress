package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getImageBitmap;
import static net.opengress.slimgress.ViewHelpers.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemBase;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

// FIXME: if a portal enters/exits range, enable/disable the hack button etc
public class ActivityPortal extends Activity {

    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private final int mActionRadiusM = mGame.getKnobs().getScannerKnobs().getActionRadiusM();

    // TODO: maybe change this to a java executor,
    //  looks cleaner and could put a threadpool in Application class. Eg to display portal keys
    private static class MyTask extends AsyncTask<Void, Void, String> {

        private final WeakReference<ActivityPortal> activityReference;
        Bitmap mBitmap;
        final GameEntityPortal mPortal;

        // only retain a weak reference to the activity
        MyTask(ActivityPortal context, GameEntityPortal thePortal) {
            activityReference = new WeakReference<>(context);
            mPortal = thePortal;
        }

        @NonNull
        @Override
        protected String doInBackground(Void... params) {
            mBitmap = getImageBitmap(mPortal.getPortalImageUrl(), activityReference.get().getApplicationContext().getCacheDir());
            return "task finished";
        }

        @Override
        protected void onPostExecute(String result) {

            // get a reference to the activity if it is still there
            ActivityPortal activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return;

            // modify the activity's UI
            if (mBitmap != null) {
                ((ImageView) activity.findViewById(R.id.portalImage)).setImageBitmap(mBitmap);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portal);

        GameEntityPortal portal = mGame.getCurrentPortal();

//        int textColor;
//        Team team = agent.getTeam();
//        textColor = 0xff000000 + team.getColour();

        ((TextView)findViewById(R.id.portalTitle)).setText(portal.getPortalTitle());

        String portalLevel = "L" + portal.getPortalLevel();
        ((TextView)findViewById(R.id.portalLevel)).setText(portalLevel);
        // TODO: level colours
//        ((TextView)findViewById(R.id.portalLevel)).setTextColor();

        // FIXME: format this nicely
        ((TextView)findViewById(R.id.portalEnergy)).setText(getString(R.string.portal_energy, portal.getPortalEnergy()));

        ((ImageView)findViewById(R.id.portalImage)).setImageBitmap(getBitmapFromAsset("no_image.png", getAssets()));
        new MyTask(this, portal).execute();

        ((TextView)findViewById(R.id.portalOwner)).setText(portal.getOwnerGuid());
        ((TextView)findViewById(R.id.portalOwner)).setTextColor(0xFF000000 + portal.getPortalTeam().getColour());

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

        if (!mGame.getAgent().getNickname().startsWith("MT")) {
            findViewById(R.id.deployButton).setEnabled(true);
            findViewById(R.id.deployButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent myIntent = new Intent(getApplicationContext(), ActivityDeploy.class);
                    startActivity(myIntent);
                }
            });
        }
//        ((ProgressBar)findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
//        ((ProgressBar)findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
//
//        String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//        ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//        ((TextView)findViewById(R.id.agentinfo)).setTextColor(textColor);

        // FIXME there should be some kind of listener or observer I can use for this
        //  -- maybe i can use device orientation sensor
        //  -- I CAN MAYBE USE A SIGNAL
        Handler locationHandler = new Handler();
        Runnable mRunnable = new Runnable(){
            @Override
            public void run() {
                setButtonsEnabled(isPortalInRange());
                locationHandler.postDelayed(this, 250);
            }
        };
        mRunnable.run();
    }

    private void setButtonsEnabled(boolean bool) {
        findViewById(R.id.hackButton).setEnabled(bool);
    }

    private boolean isPortalInRange() {
        return mGame.getLocation().getLatLng().distanceToAsDouble(mGame.getCurrentPortal().getPortalLocation().getLatLng()) <= mActionRadiusM;
    }

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
        // this should always be false until I implement glyph hacking, then always false
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

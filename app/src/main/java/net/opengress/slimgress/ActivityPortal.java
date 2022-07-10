package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getImageBitmap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.GameEntity.GameEntityPortal;
import net.opengress.slimgress.API.Item.ItemBase;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import opengress.slimgress.R;

// FIXME: if a portal enters/exits range, enable/disable the hack button etc
public class ActivityPortal extends Activity {

    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();

    // TODO: maybe change this to a java executor,
    //  looks cleaner and could put a threadpool in Application class. Eg to display portal keys
    private static class MyTask extends AsyncTask<Void, Void, String> {

        private final WeakReference<ActivityPortal> activityReference;
        Bitmap mBitmap;
        GameEntityPortal mPortal;

        // only retain a weak reference to the activity
        MyTask(ActivityPortal context, GameEntityPortal thePortal) {
            activityReference = new WeakReference<>(context);
            mPortal = thePortal;
        }

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
            ((ImageView)activity.findViewById(R.id.portalImage)).setImageBitmap(mBitmap);
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

        ((ImageView)findViewById(R.id.portalImage)).setImageBitmap(getBitmapFromAsset("no_image.png"));
        new MyTask(this, portal).execute();

        ((TextView)findViewById(R.id.portalOwner)).setText(portal.getOwnerGuid());
        ((TextView)findViewById(R.id.portalOwner)).setTextColor(0xFF000000 + portal.getPortalTeam().getColour());

        Handler handler = new Handler(msg -> {
            Log.d("HACKING", msg.getData().toString());
            ArrayList<String> guids = msg.getData().getStringArrayList("guids");
            if (guids.isEmpty()) {
                Toast.makeText(getApplicationContext(),
                                "Hack acquired no items",
                                Toast.LENGTH_LONG)
                        .show();
            } else {
                HashMap<String, ItemBase> items = (HashMap<String, ItemBase>) msg.getData().getSerializable("items");
                ArrayList<String> descriptions = new ArrayList<>();
                for (String guid: guids) {
                    descriptions.add(Objects.requireNonNull(items.get(guid)).getName());
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getLayoutInflater().getContext());
                builder.setItems(descriptions.toArray(new String[0]), (dialogInterface, i) -> {})
                        .setTitle("Hack acquired the following items");
                AlertDialog dialog = builder.create();
                dialog.show();
            }
            return false;
        });

        // FIXME only do it for portals in radius (see earlier todo)
        findViewById(R.id.hackButton).setEnabled(true);
        findViewById(R.id.hackButton).setOnClickListener(v -> {
            mGame.intHackPortal(portal, handler);
        });
        findViewById(R.id.hackButton).setOnLongClickListener(v -> {
            // TODO: upgrade to glyph hacking stuff
            mGame.intHackPortal(portal, handler);
            return false;
        });
//        ((ProgressBar)findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
//        ((ProgressBar)findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
//
//        String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//        ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//        ((TextView)findViewById(R.id.agentinfo)).setTextColor(textColor);
    }

    // FIXME duplicated in ScannerView
    private Bitmap getBitmapFromAsset(String name)
    {
        AssetManager assetManager = getAssets();

        InputStream istr;
        Bitmap bitmap;
        try {
            istr = assetManager.open(name);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            return null;
        }

        return bitmap;
    }

}

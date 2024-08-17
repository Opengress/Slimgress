/*

 Slimgress: Opengress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>
 Copyright (C) 2024 Opengress Team <info@opengress.net>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */

package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getErrorStringFromAPI;
import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
import static net.opengress.slimgress.ViewHelpers.getPrettyItemName;
import static net.opengress.slimgress.ViewHelpers.putItemInMap;
import static net.opengress.slimgress.ViewHelpers.saveScreenshot;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import net.opengress.slimgress.API.Common.Team;
import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Item.ItemBase;
import net.opengress.slimgress.API.Player.Agent;
import net.opengress.slimgress.API.Plext.PlextBase;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ActivityMain extends FragmentActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private static boolean isInForeground = false;
    private boolean isLevellingUp = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // update agent data
        updateAgent();
        mApp.getPlayerDataViewModel().getAgent().observe(this, this::updateAgent);

        // create ops button callback
        final Button buttonOps = findViewById(R.id.buttonOps);
        buttonOps.setOnClickListener(v -> {
            // Perform action on click
            Intent myIntent = new Intent(getApplicationContext(), ActivityOps.class);
            startActivity(myIntent);
        });

        // create comm button callback
        final Button buttonComm = findViewById(R.id.buttonComm);
        buttonComm.setOnClickListener(v -> showComms());

        mApp.getCommsViewModel().getAllMessages().observe(this, this::getCommsMessages);
        mApp.getLevelUpViewModel().getLevelUpMsgId().observe(this, this::levelUp);
    }

    private synchronized void levelUp(Integer level) {
        if (isLevellingUp) {
            Log.d("Main", "Not levelling up, because we are ALREADY DOING THAT");
            return; // Exit if the function is already running
        }
        if (!isActivityInForeground()) {
            Log.d("Main", "Not levelling up, because we are not in the foreground");
            return;
        }
        if (mGame.getLocation() == null) {
            Log.d("Main", "Not levelling up, to be safe, because we have no location");
            return;
        }
        if (mGame.getAgent() == null) {
            Log.d("Main", "Not levelling up, because we can't remember who we are!");
            return;
        }
        isLevellingUp = true;
        try {
            Log.d("Main", "Levelling up! New level: " + level);
            mGame.intLevelUp(level, new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                } else {
                    showLevelUpDialog(generateFieldKitMap(data));
                }
                return false;
            }));
        } finally {
            isLevellingUp = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
    }

    public static boolean isActivityInForeground() {
        return isInForeground;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private HashMap<String, Integer> generateFieldKitMap(@NonNull Bundle data) {
        HashMap<String, Integer> items = new HashMap<>();
        Serializable serializable = data.getSerializable("items");

        if (serializable instanceof HashMap) {
            HashMap<String, ItemBase> rawItems = (HashMap<String, ItemBase>) serializable;

            for (Map.Entry<String, ItemBase> entry : rawItems.entrySet()) {
                String name = getPrettyItemName(entry.getValue(), getResources());
                putItemInMap(items, name);
            }
        }

        return items;
    }

    private void getCommsMessages(List<PlextBase> plexts) {
        PlextBase plext = plexts.get(plexts.size() - 1);
        WidgetCommsLine commsLine = findViewById(R.id.commsOneLiner);
        ((TextView) commsLine.findViewById(R.id.plext_text)).setText(plext.getFormattedText());

        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String formattedTime = sdf.format(new Date(Long.parseLong(plext.getEntityTimestamp())));
        ((TextView) commsLine.findViewById(R.id.plext_time)).setText(formattedTime);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        Objects.requireNonNull(scanner).requestLocationUpdates();
    }

    private void showComms() {
        DialogComms bottomSheet = new DialogComms();
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }

    @SuppressLint("ObsoleteSdkInt")
    private void updateAgent(Agent agent) {
        Log.d("Main/updateAgent", "Updating agent in display!");
        // TODO move some of this style info into onCreate
        int textColor;
        Team team = agent.getTeam();
        textColor = 0xff000000 + team.getColour();
        int levelColor = getColorFromResources(getResources(), getLevelColor(agent.getLevel()));

        ((TextView) findViewById(R.id.agentname)).setText(agent.getNickname());
        ((TextView) findViewById(R.id.agentname)).setTextColor(textColor);

        String agentlevel = "L" + agent.getLevel();
        ((TextView) findViewById(R.id.agentLevel)).setText(agentlevel);
        ((TextView) findViewById(R.id.agentLevel)).setTextColor(levelColor);


        String nextLevel = String.valueOf(Math.min(agent.getLevel() + 1, 8));
        int thisLevelAP;
        try {
            thisLevelAP = mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(agent.getLevel()).getApRequired();
        } catch (Exception ignored) {
            /*
            there's a race condition which can cause a crash here,
            but if we won the race with the wrong code path we can just bail and it should be fine
            :-)
             */
            return;
        }
        int nextLevelAP = mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(nextLevel).getApRequired();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((ProgressBar) findViewById(R.id.agentap)).setMin(thisLevelAP);
        }
        ((ProgressBar) findViewById(R.id.agentap)).setMax(nextLevelAP);
        ((ProgressBar) findViewById(R.id.agentap)).setProgress(agent.getAp());
        Log.d("Main/updateAgent", "New AP: " + agent.getAp());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((ProgressBar) findViewById(R.id.agentap)).getProgressDrawable().setTint(levelColor);
        } else {
            ((ProgressBar) findViewById(R.id.agentap)).getProgressDrawable().setColorFilter(levelColor, PorterDuff.Mode.SRC_IN);
        }

        ((ProgressBar) findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
        ((ProgressBar) findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
        Log.d("Main/updateAgent", "New XM: " + agent.getEnergy());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((ProgressBar) findViewById(R.id.agentxm)).getProgressDrawable().setTint(textColor);
        } else {
            ((ProgressBar) findViewById(R.id.agentxm)).getProgressDrawable().setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        }
        findViewById(R.id.activity_main_header).setOnClickListener(view -> {
            String agentinfo = "AP: " + agent.getAp() + " / " + nextLevelAP + "\nXM: " + agent.getEnergy() + " / " + agent.getEnergyMax();
            Toast.makeText(getApplicationContext(), agentinfo, Toast.LENGTH_LONG).show();
        });

//            String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//            ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//            ((TextView)findViewById(R.id.agentinfo)).setTextColor(0x99999999);
    }

    void updateAgent() {
        // get agent data
        Agent agent = mGame.getAgent();

        if (agent != null) {
            updateAgent(agent);
        }

    }


    public void showLevelUpDialog(HashMap<String, Integer> items) {
        DialogLevelUp dialog = new DialogLevelUp(this);
        int level = mGame.getAgent().getVerifiedLevel();
        dialog.setMessage("LEVEL " + level, getColorFromResources(getResources(), getLevelColor(level)));
        dialog.setCancelable(true); // Allow dialog to be dismissed by tapping outside

        dialog.setOnDismissListener(dialog1 -> showNextDialog(items));

        // Add a share button
        ImageButton shareButton = dialog.findViewById(R.id.share_button);
        shareButton.setOnClickListener(v -> screenshotDialog(dialog));

        dialog.show();
    }

    @SuppressLint("DefaultLocale")
    private void screenshotDialog(Dialog dialog) {
        // Capture the main activity's view
        View mainView = getWindow().getDecorView().findViewById(android.R.id.content);
        mainView.setDrawingCacheEnabled(true);
        Bitmap mainBitmap = Bitmap.createBitmap(mainView.getDrawingCache());
        mainView.setDrawingCacheEnabled(false);

        // Capture the dialog's view
        View dialogView = dialog.getWindow().getDecorView();
        dialogView.setDrawingCacheEnabled(true);
        Bitmap dialogBitmap = Bitmap.createBitmap(dialogView.getDrawingCache());
        dialogView.setDrawingCacheEnabled(false);

        // Combine both bitmaps
        Bitmap combinedBitmap = Bitmap.createBitmap(mainBitmap.getWidth(), mainBitmap.getHeight(), mainBitmap.getConfig());
        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawBitmap(mainBitmap, new Matrix(), null);
        int[] dialogLocation = new int[2];
        dialogView.getLocationOnScreen(dialogLocation);
        canvas.drawBitmap(dialogBitmap, dialogLocation[0], dialogLocation[1], null);

        File screenshotFile = saveScreenshot(getExternalCacheDir(), combinedBitmap);

        // Share the screenshot
        Uri screenshotUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", screenshotFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, screenshotUri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, String.format("I've reached level %d in #opengress!", mGame.getAgent().getVerifiedLevel()));
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    @SuppressLint("DefaultLocale")
    public void showNextDialog(HashMap<String, Integer> items) {
        DialogHackResult newDialog1 = new DialogHackResult(this);
        newDialog1.setTitle(String.format("Receiving Level %d field kit...", mGame.getAgent().getVerifiedLevel()));
        newDialog1.setItems(items);
        newDialog1.show();
    }
}

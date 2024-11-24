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

package net.opengress.slimgress.activity;

import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus;
import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus.ClientMustUpgrade;
import static net.opengress.slimgress.api.Interface.Handshake.PregameStatus.UserMustAcceptTOS;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.InputFilter;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import net.opengress.slimgress.BuildConfig;
import net.opengress.slimgress.Constants;
import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Interface.Interface;
import net.opengress.slimgress.api.Knobs.TeamKnobs;
import net.opengress.slimgress.api.Player.Agent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ActivitySplash extends Activity {
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private Bundle loginBundle;
    private Dialog mFactionChoiceDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ((TextView) findViewById(R.id.splashVersion)).setText(String.format("%s version %s", getText(R.string.slimgress_version_unknown), BuildConfig.VERSION_NAME));

        // authenticate if necessary
        if (!mApp.isLoggedIn()) {
            Intent myIntent = new Intent(getApplicationContext(), ActivityAuth.class);
            startActivityForResult(myIntent, 0);
        } else {
            // start main activity
            Agent agent = mGame.getAgent();
            mGame.putAgentName(agent.getEntityGuid(), agent.getNickname());
            finish();
            startActivity(new Intent(getApplicationContext(), ActivityMain.class));
        }
    }

    protected void performHandshake() {
        performHandshake(false, false);
    }

    void performHandshake(boolean tosAccepted, boolean wantsPromos) {
        HashMap<String, String> params = new HashMap<>();
        if (tosAccepted) {
            params.put("tosAccepted", "1");
            params.put("wantsPromos", wantsPromos ? "true" : "false");
        }
        Handler handler = new Handler(msg -> {
            loginBundle = msg.getData();
            return setUpPlayerAndProceedWithLogin();
        });
        mGame.intHandshake(handler, params);
    }

    private boolean setUpPlayerAndProceedWithLogin() {
        // this will need further updates, like when user needs to select a faction
        if (mGame.getHandshake().isValid() && mGame.getAgent().isAllowedNicknameEdit()) {
            showValidateUsernameDialog(null);
            return false;
        } else if (mGame.getHandshake().isValid() && mGame.getAgent().isAllowedFactionChoice()) {
            showPickFactionDialog();
            return false;
        } else {
            proceedWithLogin();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                mApp.setLoggedIn(true);

                // perform handshake
                performHandshake();
            } else if (resultCode == RESULT_FIRST_USER) {
                // user cancelled authentication
                finish();
            } else {
                // authentication failed
                mApp.setLoggedIn(false);

                // show an information dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.splash_failure_title);
                builder.setMessage(R.string.splash_failure_msg);

                builder.setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        }
    }

    // might change this to be recursive
    private void proceedWithLogin() {
        if (loginBundle.getBoolean("Successful")) {
            SlimgressApplication.postPlainCommsMessage("Agent ID Confirmed. Welcome " + mGame.getAgent().getNickname());
            // start main activity
            ActivitySplash.this.finish();
            ActivitySplash.this.startActivity(new Intent(ActivitySplash.this, ActivityMain.class));
            mApp.postGameScore();
        } else {
            mApp.setLoggedIn(false);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setTitle("Handshake error");
            PregameStatus status = mGame.getHandshake().getPregameStatus();
            if (status == ClientMustUpgrade) {
                builder.setMessage("Your client software is out of date. You must update the app to play.");
                builder.setPositiveButton("Update in-app", (dialog, which) -> {
                    downloadAndInstallClientUpdate();
//                                finish();
                });
                builder.setNegativeButton("Download update in browser", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://opengress.net/downloads/"));
                    startActivity(browserIntent);
                    finish();
                });
            } else if (status == UserMustAcceptTOS) {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_terms, null);
                builder.setView(dialogView);

                CheckBox emailOptIn = dialogView.findViewById(R.id.emailOptIn);
                emailOptIn.setChecked(loginBundle.getBoolean("MaySendPromoEmail"));
                builder.setMessage(Html.fromHtml("Please review and accept our updated <a href='https://opengress.net/terms-of-service'>Terms of Service</a> to continue."));
                builder.setPositiveButton("Accept", (dialog, which) -> {
                    // around we go!
                    performHandshake(true, emailOptIn.isChecked());
                });
                builder.setNegativeButton("Decline", (dialog, which) -> {
                    finish();
                });
            } else if (Objects.equals(loginBundle.getString("Error"), "Expired user session")) {
                builder.setMessage(R.string.session_expired_log_in_again);
                builder.setNegativeButton("OK", (dialog, which) -> {
                    mApp.setLoggedIn(false);
                    SharedPreferences prefs = getSharedPreferences(getApplicationInfo().packageName, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.remove(Constants.PREFS_SERVER_SESSION_NAME);
                    editor.remove(Constants.PREFS_SERVER_SESSION_ID);
                    editor.apply();
                    Intent myIntent = new Intent(getApplicationContext(), ActivityAuth.class);
                    startActivityForResult(myIntent, 0);
                });
            } else {
                builder.setMessage(loginBundle.getString("Error"));
                builder.setNegativeButton("OK", (dialog, which) -> finish());
            }
            Dialog dialog = builder.create();
            dialog.show();
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void downloadAndInstallClientUpdate() {
        File downloads = getApplicationContext().getExternalCacheDir();

        Thread gfgThread = new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .followRedirects(false)
                    .build();
            try {
                Request request = new Request.Builder().url("https://opengress.net/downloads/slimgress.apk")
                        .header("User-Agent", Interface.mUserAgent)
                        .header("Accept", "application/vnd.android.package-archive")
                        .build();
                Response response;
                response = client.newCall(request).execute();
                ResponseBody responseBody = response.body();
                double length = Double.parseDouble(Objects.requireNonNull(response.header("Content-Length", "1")));
                File apkFile = new File(downloads + "/slimgress.apk");
                apkFile.delete();
                OutputStream outputStream = new FileOutputStream(apkFile);
                try (BufferedInputStream input = new BufferedInputStream(responseBody.byteStream())) {
                    byte[] dataBuffer = new byte[1024];
                    int readBytes;
                    long totalBytes = 0;
                    while ((readBytes = input.read(dataBuffer)) != -1) {
                        totalBytes += readBytes;
                        outputStream.write(dataBuffer, 0, readBytes);
                        onDownloadUpdateProgress(totalBytes / length * 100.0);
                    }
                } catch (Exception e) {
                    // download failed. do something.
                    e.printStackTrace();
                }

                try {

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    if (Build.VERSION.SDK_INT >= 24) {
                        intent.setDataAndType(FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", apkFile), "application/vnd.android.package-archive");
                    } else {
                        intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                    }
                    try {
                        startActivityForResult(intent, 500);
                    } catch (Exception e) {
                        Log.e("Splash/Installer", Objects.requireNonNull(e.getMessage()));
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    Log.e("Splash/Installer", Objects.requireNonNull(e.getMessage()));
                    e.printStackTrace();
                }

                response.close();

            } catch (Exception e) {
                // download failed. do something
                e.printStackTrace();
            }
        });

        synchronized (this) {
            gfgThread.start();
        }

    }

    private void onDownloadUpdateProgress(double v) {
        // TODO find some way to report progress - notification?
        Log.i("Splash/DownloadProgress", String.valueOf(v));
    }

    private void showPickFactionDialog() {
        mApp.setLoggedIn(false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle("Choose your faction carefully");

        // Create a container for the buttons (e.g., LinearLayout)
        ScrollView scrollView = new ScrollView(this); // Add scrolling support for many factions
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);
        scrollView.addView(container);

        // Create buttons for each faction dynamically
        for (var faction : mGame.getKnobs().getTeamKnobs().getTeams().values()) {
            if (!faction.isPlayable()) {
                continue;
            }
            Button factionButton = new Button(this);
            factionButton.setText(faction.getName());
            factionButton.setBackgroundColor(0xff000000 + faction.getColour());
            factionButton.setTextColor(Color.WHITE);
            factionButton.setPadding(16, 16, 16, 16);
            factionButton.setTextSize(18);
            factionButton.setOnClickListener(v -> {
                showFactionConfirmationDialog(faction);
                mFactionChoiceDialog.dismiss();
            });

            // Add a margin between buttons
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            factionButton.setLayoutParams(params);

            // Add the button to the container
            container.addView(factionButton);
        }

        builder.setView(scrollView);

        mFactionChoiceDialog = builder.create();
        mFactionChoiceDialog.show();
    }

    void showFactionConfirmationDialog(TeamKnobs.TeamType chosenFaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Your Choice");
        builder.setMessage("Are you sure you want to choose " + chosenFaction.getName() + "?\n" +
                "Once confirmed, this decision is final.");
        builder.setCancelable(false);

        builder.setPositiveButton("Yes", (dialog, which) -> {
            var team = new Team(chosenFaction);
            mGame.intChooseFaction(team, new Handler(msg -> {

                if (msg.getData().keySet().contains("Error")) {
                    Toast.makeText(getApplicationContext(), msg.getData().getString("Error"), Toast.LENGTH_LONG).show();
                    showPickFactionDialog();
                    return false;
                }

                dialog.dismiss();
                mGame.getAgent().setAllowedFactionChoice(false);
                mGame.getAgent().setTeam(team);
                setUpPlayerAndProceedWithLogin();
                return true;
            }));
        });

        builder.setNegativeButton("No", (dialog, which) -> {
            dialog.dismiss();
            showPickFactionDialog();
        });

        builder.create().show();
    }

    private void showValidateUsernameDialog(String error) {
        mApp.setLoggedIn(false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle("Codename required");
        builder.setMessage(Objects.requireNonNullElse(error, "Create an agent name. This is the name other agents will know you by.")+"\n\nType a new agent name.");

        // Set an EditText view to get user input
        final EditText input = getEditTextInput();
        builder.setView(input);

        builder.setPositiveButton("Transmit", (dialog, whichButton) -> {
            String value = input.getText().toString().trim();
            // Do something with value!
            mGame.intValidateNickname(value, new Handler(msg -> {

                if (msg.getData().keySet().contains("Error")) {
                    // do something with error
                    showValidateUsernameDialog(msg.getData().getString("Error"));
                    return false;
                }

                showPersistUsernameDialog(value);
                return false;
            }));
        });

        Dialog dialog = builder.create();
        dialog.show();
    }

    private @NonNull EditText getEditTextInput() {
        final EditText input = new EditText(this);
        // this filter probably misses some edge cases
        input.setFilters(new InputFilter[] {(source, start, end, dest, dstart, dend) -> {

            int maxLength = 16;

            if (source.length() > maxLength-1) {
                return source.subSequence(0,maxLength);
            } else if (input.length() > maxLength-1) {
                return "";
            } else {
                for (int i = start; i < end; i++) {
                    if (!Character.isLetterOrDigit(source.charAt(i)) && !Character.toString(source.charAt(i)).equals("_")) {
                        return "";
                    }
                }
            }

            return null;
        }});
        return input;
    }

    private void showPersistUsernameDialog(String name) {
        mApp.setLoggedIn(false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle("Success");
        builder.setMessage("Codename valid. Please confirm:\n\n"+name);


        builder.setPositiveButton("Confirm", (dialog, whichButton) -> {
            // Do something with value!
            mGame.intPersistNickname(name, new Handler(msg -> {

                if (msg.getData().keySet().contains("Error")) {
                    showValidateUsernameDialog(msg.getData().getString("Error"));
                    return false;
                }

                dialog.dismiss();
                // i think we just assume that this is good
                mGame.getAgent().setNickname(name);
                mGame.getAgent().setAllowedNicknameEdit(false);
                setUpPlayerAndProceedWithLogin();
                return true;
            }));
        });

        builder.setNegativeButton("Retry", (dialog, whichButton) -> showValidateUsernameDialog(null));

        Dialog dialog = builder.create();
        dialog.show();
    }


}

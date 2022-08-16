/*

 Slimgress: Ingress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>

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

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Interface.Handshake;
import net.opengress.slimgress.API.Interface.Interface;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ActivitySplash extends Activity {
    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();

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
            finish();
            startActivity(new Intent(getApplicationContext(), ActivityMain.class));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final Context context = this;

        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                mApp.setLoggedIn(true);

                // perform handshake
                mGame.intHandshake(new Handler(msg -> {
                    Bundle data1 = msg.getData();

                    if (data1.getBoolean("Successful")) {
                        // start main activity
                        ActivitySplash.this.finish();
                        ActivitySplash.this.startActivity(new Intent(ActivitySplash.this, ActivityMain.class));
                    } else {
                        mApp.setLoggedIn(false);

                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle("Handshake error");
                        if (mGame.getHandshake().getPregameStatus() == Handshake.PregameStatus.ClientMustUpgrade) {
                            builder.setCancelable(false);

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
                        } else {
                            builder.setMessage(data1.getString("Error"));
                            builder.setNegativeButton("OK", (dialog, which) -> finish());
                        }
                        Dialog dialog = builder.create();
                        dialog.show();
                    }

                    return true;
                }));
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
                        Log.e("ActivitySplash/Installer", e.getMessage());
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    Log.e("ActivitySplash/Installer", e.getMessage());
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
        // TODO find some way to report progress
        Log.d("ActivitySplash/DownloadProgress", String.valueOf(v));
    }


}

package net.opengress.slimgress.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import net.opengress.slimgress.BuildConfig;
import net.opengress.slimgress.R;
import net.opengress.slimgress.api.Interface.Interface;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DownloadService extends Service {

    public static final String CHANNEL_ID = "DownloadChannel";
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private long lastNotificationTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Build the initial notification
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading Update")
                .setContentText("Download in progress")
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, false);

        startForeground(1, notificationBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start the download in a new thread
        new Thread(this::downloadUpdate).start();
        return START_NOT_STICKY;
    }

    /*
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    if (!getPackageManager().canRequestPackageInstalls()) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        // Handle result and continue installation
    } else {
        // Proceed with installation
    }
} else {
    // Proceed with installation
}
     */

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void downloadUpdate() {
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .build();
        try {
            Request request = new Request.Builder().url("https://opengress.net/downloads/slimgress.apk")
                    .header("User-Agent", Interface.mUserAgent)
                    .header("Accept", "application/vnd.android.package-archive")
                    .build();
            Response response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            double length = Double.parseDouble(Objects.requireNonNull(response.header("Content-Length", "1")));
            Log.d("SERVICE", "Got file length: " + length);
            File apkFile = new File(getExternalCacheDir() + "/slimgress.apk");
            apkFile.delete();
            try (OutputStream outputStream = new FileOutputStream(apkFile); BufferedInputStream input = new BufferedInputStream(responseBody.byteStream())) {
                byte[] dataBuffer = new byte[1024];
                int readBytes;
                long totalBytes = 0;
                int lastProgress = 0;
                long lastUpdateTime = System.currentTimeMillis();

                while ((readBytes = input.read(dataBuffer)) != -1) {
                    totalBytes += readBytes;
                    outputStream.write(dataBuffer, 0, readBytes);
                    int progress = (int) ((totalBytes * 100) / length);

                    // Update if progress increased by 5% or if 1 second has passed
                    if (progress - lastProgress >= 5 || System.currentTimeMillis() - lastUpdateTime >= 1000) {
                        updateProgress(progress);
                        lastProgress = progress;
                        lastUpdateTime = System.currentTimeMillis();
                    }
                }
            } catch (Exception e) {
                // download failed. do something.
                e.printStackTrace();
            }

            try {

                // TODO: - report errors to user, and handle the failed installation case
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

                if (Build.VERSION.SDK_INT >= 24) {
                    intent.setDataAndType(FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", apkFile), "application/vnd.android.package-archive");
                } else {
                    intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
                }
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("Splash/Installer", Objects.requireNonNull(e.getMessage()));
                    e.printStackTrace();
                }

            } catch (Exception e) {
                Log.e("Splash/Installer", Objects.requireNonNull(e.getMessage()));
                e.printStackTrace();
            }

            downloadComplete();
            response.close();

        } catch (Exception e) {
            // download failed. do something
            e.printStackTrace();
        }
    }

    private void updateProgress(int progress) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNotificationTime < 1000) {
            // Less than 1 second since last update, skip this update
            return;
        }
        lastNotificationTime = currentTime;

        // Update the notification
        notificationBuilder.setProgress(100, progress, false);
        notificationManager.notify(1, notificationBuilder.build());

        // Update the UI ProgressBar in ActivitySplash
        Intent intent = new Intent("DownloadProgress");
        intent.putExtra("progress", progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void downloadComplete() {
        // Update the notification to show completion
        notificationBuilder.setContentText("Download complete")
                .setProgress(0, 0, false)
                .setOngoing(false);
        notificationManager.notify(1, notificationBuilder.build());

        Intent intent = new Intent("DownloadProgress");
        intent.putExtra("progress", -1);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        stopForeground(false);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = "Download Updates";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

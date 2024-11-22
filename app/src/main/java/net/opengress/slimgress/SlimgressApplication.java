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

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;

import androidx.lifecycle.ViewModelProvider;

import net.opengress.slimgress.activity.ActivityMain;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Plext.PlextBase;
import net.opengress.slimgress.api.ViewModels.APGainsViewModel;
import net.opengress.slimgress.api.ViewModels.CommsViewModel;
import net.opengress.slimgress.api.ViewModels.InventoryViewModel;
import net.opengress.slimgress.api.ViewModels.LevelUpViewModel;
import net.opengress.slimgress.api.ViewModels.LocationViewModel;
import net.opengress.slimgress.api.ViewModels.PlayerDamagesViewModel;
import net.opengress.slimgress.api.ViewModels.PlayerDataViewModel;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SlimgressApplication extends Application {
    private static SlimgressApplication mSingleton;
    private boolean mLoggedIn = false;
    protected GameState mGame;
    private LocationViewModel mLocationViewModel;
    private InventoryViewModel mInventoryViewModel;
    private APGainsViewModel mAPGainsViewModel;
    private PlayerDamagesViewModel mPlayerDamagesViewModel;
    private PlayerDataViewModel mPlayerDataViewModel;
    private CommsViewModel mAllCommsViewModel;
    private CommsViewModel mFactionCommsViewModel;
    private LevelUpViewModel mLevelUpViewModel;
    private ActivityMain mMainActivity;
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService mScheduledExecutorService = Executors.newScheduledThreadPool(1);

    @Override
    public void onCreate() {
        super.onCreate();
        mLocationViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(LocationViewModel.class);
        mInventoryViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(InventoryViewModel.class);
        mAPGainsViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(APGainsViewModel.class);
        mPlayerDamagesViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(PlayerDamagesViewModel.class);
        mPlayerDataViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(PlayerDataViewModel.class);
        mAllCommsViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(CommsViewModel.class);
        mFactionCommsViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(CommsViewModel.class);
        mLevelUpViewModel = new ViewModelProvider.AndroidViewModelFactory(this).create(LevelUpViewModel.class);

        mSingleton = this;
        mGame = new GameState();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        mExecutorService.shutdown();
        mScheduledExecutorService.shutdown();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // (ONLY) release (non-debug) builds use ACRA, because debug builds => developing => crashes
        if (!BuildConfig.DEBUG) {
            ACRA.init(this, new CoreConfigurationBuilder()
                    //core configuration:
                    .withBuildConfigClass(BuildConfig.class)
                    .withReportFormat(StringFormat.JSON)
                    .withPluginConfigurations(
                            new HttpSenderConfigurationBuilder()
                                    //required. Https recommended
                                    .withUri("https://opengress.net/acra")
                                    .build()
                    )
            );
        }

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .permitDiskReads()
                    .permitDiskWrites()
                    .penaltyLog()
//                    .penaltyDeath()
                    .build());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                        .detectActivityLeaks()
                        .detectLeakedClosableObjects()
                        .detectLeakedRegistrationObjects()
                        .detectLeakedSqlLiteObjects()
                        .detectContentUriWithoutPermission()
//                        .detectUntaggedSockets()
                        .detectFileUriExposure()
                        .detectCleartextNetwork()
                        .penaltyLog()
//                        .penaltyDeath()
                        .build());
            }
            // android P:
            //                        .detectNonSdkApiUsage()
        }

        // this should never actually work when ACRA is not init-ed, and that seems harmless
        /*
        i am very interested in this information, but i don't want it every time.
        maybe i can add a "participate in device survey" button in OPS or something.
        i do get most of the useful stuff in handshake. hmm.
         */
//        ACRA.getErrorReporter().putCustomData("reason", "startup");
//        ACRA.getErrorReporter().handleSilentException(null);
//        ACRA.getErrorReporter().clearCustomData();

    }

    public void postGameScore() {
        long currentTimeMillis = System.currentTimeMillis();
        long unixEpochMillis = 0;
        long fiveHoursMillis = 5 * 60 * 60 * 1000;

        long timeDifferenceMillis = currentTimeMillis - unixEpochMillis;
        long remainingMillis = fiveHoursMillis - (timeDifferenceMillis % fiveHoursMillis);
        schedule_(this::postGameScore, remainingMillis, TimeUnit.MILLISECONDS);
        runInThread_(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> mGame.intGetGameScore(new Handler(msg -> {
                var enl = msg.getData().getInt("EnlightenedScore");
                var res = msg.getData().getInt("ResistanceScore");
                mAllCommsViewModel.addMessage(PlextBase.createByScores(enl, res));
                return true;
            })));
        });
    }

    public static SlimgressApplication getInstance() {
        return mSingleton;
    }

    public static <T> Future<T> runInThread(Callable<T> task) {
        return getInstance().runInThread_(task);
    }

    public static Future<?> runInThread(Runnable task) {
        return getInstance().runInThread_(task);
    }

    public <T> Future<T> runInThread_(Callable<T> task) {
        return getExecutorService().submit(task);
    }

    public Future<?> runInThread_(Runnable task) {
        return getExecutorService().submit(task);
    }

    public static <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                                  long delay, TimeUnit unit) {
        return getInstance().schedule_(callable, delay, unit);
    }

    public static ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return getInstance().schedule_(command, delay, unit);
    }

    public <V> ScheduledFuture<V> schedule_(Callable<V> callable,
                                            long delay, TimeUnit unit) {
        return getScheduler().schedule(callable, delay, unit);
    }

    public ScheduledFuture<?> schedule_(Runnable command, long delay, TimeUnit unit) {
        return getScheduler().schedule(command, delay, unit);
    }

    public ScheduledExecutorService getScheduler() {
        return mScheduledExecutorService;
    }

    public GameState getGame() {
        return mGame;
    }

    public void setMainActivity(ActivityMain activity) {
        mMainActivity = activity;
    }

    public ActivityMain getMainActivity() {
        return mMainActivity;
    }

    public LocationViewModel getLocationViewModel() {
        return mLocationViewModel;
    }

    public InventoryViewModel getInventoryViewModel() {
        return mInventoryViewModel;
    }

    public APGainsViewModel getAPGainsModel() {
        return mAPGainsViewModel;
    }

    public PlayerDamagesViewModel getPlayerDamagesModel() {
        return mPlayerDamagesViewModel;
    }

    public PlayerDataViewModel getPlayerDataViewModel() {
        return mPlayerDataViewModel;
    }

    public CommsViewModel getAllCommsViewModel() {
        return mAllCommsViewModel;
    }

    public CommsViewModel getFactionCommsViewModel() {
        return mFactionCommsViewModel;
    }

    public LevelUpViewModel getLevelUpViewModel() {
        return mLevelUpViewModel;
    }

    public ExecutorService getExecutorService() {
        return mExecutorService;
    }

    public boolean isLoggedIn() {
        return mLoggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        mLoggedIn = loggedIn;
    }

    public static void postPlainCommsMessage(String msg) {
        getInstance().getAllCommsViewModel().addMessage(PlextBase.createByPlainText(PlextBase.PlextType.PlayerGenerated, msg));
    }
}

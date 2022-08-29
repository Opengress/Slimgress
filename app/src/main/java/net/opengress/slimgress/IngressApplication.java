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

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.data.StringFormat;

public class IngressApplication extends Application
{
    private static IngressApplication mSingleton;
    private boolean mLoggedIn = false;
    protected GameState mGame;

    @Override
    public void onCreate()
    {
        super.onCreate();

        mSingleton = this;
        mGame = new GameState();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // (ONLY) release (non-debug) builds use ACRA, because debug builds => developing => crashes
        if (BuildConfig.BUILD_TYPE != "debug") {
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

    public static IngressApplication getInstance()
    {
        return mSingleton;
    }

    public GameState getGame()
    {
        return mGame;
    }

    public boolean isLoggedIn()
    {
        return mLoggedIn;
    }

    public void setLoggedIn(boolean loggedIn)
    {
        mLoggedIn = loggedIn;
    }
}

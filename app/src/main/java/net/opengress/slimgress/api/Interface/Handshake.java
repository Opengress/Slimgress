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

package net.opengress.slimgress.api.Interface;

import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Knobs.KnobsBundle;
import net.opengress.slimgress.api.Player.Agent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Handshake
{
    public interface Callback
    {
        void handle(Handshake handshake);
    }

    public enum PregameStatus
    {
        ClientUpgradeRecommended,
        ClientMustUpgrade,
        NoActionsRequired,
        UserRequiresActivation,
        UserMustAcceptTOS
    }

    // final, but stupid javac thinks it's uninitialized even though it's initialized in every code path
    private PregameStatus mPregameStatus;
    private final String mServerVersion;
    private final String mNickname;
    private Agent mAgent = null;
    private KnobsBundle mKnobs = null;
    private BulkPlayerStorage mStorage = null;
    private final String mErrorFromServer;

    public Handshake(JSONObject json) throws JSONException
    {
        JSONObject result = json.optJSONObject("result");

        if (result != null) {

            mErrorFromServer = null;

            // get BulkPlayerStorage
            JSONObject storage = result.optJSONObject("storage");
            if (storage != null) {
                mStorage = new BulkPlayerStorage(storage);
            }


            JSONObject pregameStatus = result.optJSONObject("pregameStatus");
            if (pregameStatus != null) {
                String pregameStatusString = pregameStatus.optString("action");
                switch (pregameStatusString) {
                    case "CLIENT_UPGRADE_RECOMMENDED" ->
                            mPregameStatus = PregameStatus.ClientUpgradeRecommended;
                    case "CLIENT_MUST_UPGRADE" -> mPregameStatus = PregameStatus.ClientMustUpgrade;
                    case "NO_ACTIONS_REQUIRED" -> mPregameStatus = PregameStatus.NoActionsRequired;
                    case "USER_REQUIRES_ACTIVATION" ->
                            mPregameStatus = PregameStatus.UserRequiresActivation;
                    case "USER_MUST_ACCEPT_TOS" -> mPregameStatus = PregameStatus.UserMustAcceptTOS;
                    default ->
                            throw new RuntimeException("unknown pregame status " + pregameStatus);
                }
            }

            // get knobs
            JSONObject knobs = result.optJSONObject("initialKnobs");
            if (knobs != null) {
                mKnobs = new KnobsBundle(knobs);
            }

            // get player entity
            mNickname = result.optString("nickname");
            JSONArray playerEntity = result.optJSONArray("playerEntity");
            if (playerEntity != null) {
                mAgent = new Agent(playerEntity, mNickname, mKnobs.getTeamKnobs());
            }

            mServerVersion = result.optString("serverVersion");

            // storage

        } else {
            mPregameStatus = PregameStatus.NoActionsRequired;
            mServerVersion = "";
            mNickname = "";
            mErrorFromServer = json.isNull("error") ? null : json.getString("error");
        }
    }

    public boolean isValid()
    {
        // not sure if this logic is really correct. probably.
        return mAgent != null &&
                mPregameStatus == PregameStatus.NoActionsRequired;
    }

    public PregameStatus getPregameStatus()
    {
        return mPregameStatus;
    }

    public String getServerVersion()
    {
        return mServerVersion;
    }

    public String getNickname()
    {
        return mNickname;
    }

    public String getErrorFromServer() { return mErrorFromServer; }

    public Agent getAgent()
    {
        return mAgent;
    }

    public KnobsBundle getKnobs()
    {
        return mKnobs;
    }

    public BulkPlayerStorage getBulkPlayerStorage() {
        return mStorage;
    }
}

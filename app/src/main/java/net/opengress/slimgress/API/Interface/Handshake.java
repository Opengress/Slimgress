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

package net.opengress.slimgress.API.Interface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.opengress.slimgress.API.Knobs.KnobsBundle;
import net.opengress.slimgress.API.Player.Agent;

public class Handshake
{
    public interface Callback
    {
        void handle(Handshake handshake);
    }

    public enum PregameStatus
    {
        ClientMustUpgrade,
        NoActionsRequired
    }

    // final, but stupid javac thinks it's uninitialized even though it's initialized in every code path
    private PregameStatus mPregameStatus;
    private final String mServerVersion;
    private final String mNickname;
    private Agent mAgent = null;
    private KnobsBundle mKnobs = null;

    public Handshake(JSONObject json) throws JSONException
    {
        JSONObject result = json.optJSONObject("result");

        if (result != null) {

            JSONObject pregameStatus = result.optJSONObject("pregameStatus");
            if (pregameStatus != null) {
                String pregameStatusString = pregameStatus.optString("action");
                if (pregameStatusString.equals("CLIENT_MUST_UPGRADE"))
                    mPregameStatus = PregameStatus.ClientMustUpgrade;
                else if (pregameStatusString.equals("NO_ACTIONS_REQUIRED"))
                    mPregameStatus = PregameStatus.NoActionsRequired;
                else
                    throw new RuntimeException("unknown pregame status " + pregameStatus);
            }

            mServerVersion = result.optString("serverVersion");

            // storage

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
        } else {
            mPregameStatus = PregameStatus.NoActionsRequired;
            mServerVersion = "";
            mNickname = "";
        }
    }

    public boolean isValid()
    {
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

    public Agent getAgent()
    {
        return mAgent;
    }

    public KnobsBundle getKnobs()
    {
        return mKnobs;
    }
}

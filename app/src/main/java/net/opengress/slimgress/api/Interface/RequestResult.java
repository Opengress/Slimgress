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

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RequestResult {
    private final Handler mResultHandler;
    private Bundle mBundle;
    private final Message mMessage;

    public RequestResult(Handler handler) {
        mResultHandler = handler;
        mMessage = new Message();
    }

    private void handleException(String exception) {
        Log.e("RequestResult.Callback", exception);
        initBundle();
        mBundle.putString("Exception", exception);
    }

    public void handleError(String error) {
        Log.e("RequestResult.Callback", error);
        initBundle();
        mBundle.putString("Error", error);
    }

    public void handleGameBasket(GameBasket gameBasket) {
        // not implemented
    }

    public void handleResult(JSONObject result) {
        // not implemented
    }

    public void handleResult(JSONArray result) {
        // not implemented
    }

    public void handleResult(String result) {
        // not implemented
    }

    public void initBundle() {
        if (mBundle == null) {
            mBundle = new Bundle();
        }
    }

    public Bundle getData() {
        initBundle();
        return mBundle;
    }

    public void finished() {
        mMessage.setData(mBundle);
        if (mResultHandler != null) {
            mResultHandler.sendMessage(mMessage);
        }
    }

    public static void handleRequest(JSONObject json, RequestResult result) {
        if (result == null) {
            throw new RuntimeException("invalid result object");
        }

        try {
            // handle exception string if available
            String excString = json.optString("exception");
            if (!excString.isEmpty()) {
                result.handleException(excString);
            }

            // handle error code if available
            String error = json.optString("error");
            if (!error.isEmpty()) {
                result.handleError(error);
            } else if (json.has("error")) {
                Log.w("RequestResult", "request contains an unknown error type");
            }

            // handle game basket if available
            JSONObject gameBasket = json.optJSONObject("gameBasket");
            if (gameBasket != null) {
                result.handleGameBasket(new GameBasket(gameBasket));
            }

            // handle result if available
            JSONObject resultObj = json.optJSONObject("result");
            JSONArray resultArr = json.optJSONArray("result");
            String resultStr = json.optString("result");
            if (resultObj != null) {
                result.handleResult(resultObj);
            } else if (resultArr != null) {
                result.handleResult(resultArr);
            } else {
                result.handleResult(resultStr);
            }

            result.finished();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

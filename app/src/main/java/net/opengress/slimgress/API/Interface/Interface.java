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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.opengress.slimgress.API.Common.Location;
import net.opengress.slimgress.BuildConfig;

import android.os.Build;
import android.util.Log;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Interface
{

    public enum AuthSuccess
    {
        Successful,
        TokenExpired,
        UnknownError
    }

    private final OkHttpClient mClient;
    private String mCookie;

    // ingress api definitions
    private static final String mApiVersion = String.valueOf(BuildConfig.VERSION_CODE);
    private static final String mApiBase = "opengress.net";
    private static final String mApiBaseURL = "https://" + mApiBase + "/";
    private static final String mApiHandshake = "handshake?json=";
    private static final String mApiRequest = "rpc/";
    // would like to use android resources here... will figure that out later
    public static final String mUserAgent = "Opengress/Slimgress (API dev)";

    // kludge: interface can say the right things about collecting globs without asking GameState
    private ArrayList<String> mSlurpableXMParticles;

    public Interface()
    {
        mClient = new OkHttpClient.Builder()
                .addInterceptor(
                        new DefaultRequestInterceptor("application/json"))
                .addInterceptor(chain -> {
                    final Request original = chain.request();
                    final Request fixed = original.newBuilder()
//                            .addHeader("Cookie", mCookie)
                            .build();
                    return chain.proceed(fixed);
                })
                .followRedirects(false)
                .build();
    }

    public AuthSuccess authenticate(String session_name, String session_id)
    {
        mCookie = String.join("=", session_name, session_id);
        return AuthSuccess.Successful;
    }

    public void handshake(final Handshake.Callback callback)
    {
        new Thread(() -> {
            JSONObject params = new JSONObject();
            try {
                // set handshake parameters
                params.put("adversarySoftwareVersion", mApiVersion);
                params.put("deviceSoftwareVersion", Build.VERSION.SDK_INT);
                params.put("deviceHardwareVersion", Build.MODEL);
                params.put("deviceOperatingSystem", "Android");

                // TODO:
                /*params.put("activationCode", "");
                params.put("tosAccepted", "1");
                params.put("a", "");*/

                String paramString = params.toString();
                paramString = URLEncoder.encode(paramString, "UTF-8");

                String handshake = mApiBaseURL + mApiHandshake + paramString;

                Request get = new Request.Builder()
                        .url(handshake)
                        .header("Accept-Charset", "utf-8")
                        .header("Cache-Control", "max-age=0")
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .header("Accept-Encoding", "gzip")
                        .header("User-Agent", mUserAgent)
                        .header("Host", mApiBase)
                        .header("Connection", "Keep-Alive")
                        .addHeader("Cookie", mCookie)
                        .build();

                // do handshake
                Response response;
                synchronized(Interface.this) {
                    Log.d("Interface", "executing handshake");
                    response = mClient.newCall(get).execute();
                }


                String content = Objects.requireNonNull(response.body()).string();

                    // check for content type json
                    if (!Objects.requireNonNull(response.header("Content-Type")).contains("application/json"))
                        throw new RuntimeException("content type is not json");

                    // leaving this here in case it's ever implemented serverside for some reason
                    content = content.replace("while(1);", "");

                    // handle handshake data
                    callback.handle(new Handshake(new JSONObject(content)));

                    Log.d("Interface", "handshake finished");

            }
            catch (Exception e) {
                Log.e("Interface", e.getMessage());
                e.printStackTrace();
                try {
                    callback.handle(new Handshake(new JSONObject()));
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    public void request(final Handshake handshake, final String requestString, final Location playerLocation,
            final JSONObject requestParams, final RequestResult result) throws InterruptedException
    {
        if (!handshake.isValid())
            throw new RuntimeException("handshake is not valid");

        new Thread(() -> {

            // set additional parameters
            JSONObject params = new JSONObject();
            if (requestParams != null) {
                if (requestParams.has("params"))
                    params = requestParams;
                else {
                    try {
                        params.put("params", requestParams);

                        // add persistent request parameters
                        if (playerLocation != null) {
                            String loc = playerLocation.getLatLng().toDoubleString();
                            params.getJSONObject("params").put("playerLocation", loc);
                            params.getJSONObject("params").put("location", loc);
                        }
                        params.getJSONObject("params").put("knobSyncTimestamp", getCurrentTimestamp());

                        // TODO: check that this works
                        JSONArray collectedEnergy = new JSONArray(mSlurpableXMParticles);

                        params.getJSONObject("params").put("energyGlobGuids", collectedEnergy);
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            else {
                try {
                    params.put("params", null);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            // create post
            String postString = mApiBaseURL + mApiRequest + requestString;
            RequestBody body = RequestBody.create(params.toString(), MediaType.get("application/json"));
            Request post = new Request.Builder()
                    .post(body)
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Accept-Encoding", "gzip")
                    .header("User-Agent", mUserAgent)
                    .header("Host", mApiBase)
                    .header("Connection", "Keep-Alive")
                    .addHeader("Cookie", mCookie)
                    .url(postString).build();

            // execute and get the response.
            try {
                String content = null;

                synchronized(Interface.this) {
                    try (Response response = mClient.newCall(post).execute()) {

                        if (response.code() == 401) {
                            // TODO: work out what to do... a simple, ugly idea: dialog and quit
                            // token expired or similar. player isn't logged in
//                        isAuthenticated = false;
                            result.handleError("You are not logged in. Restart the application.");
                            result.finished();
                        } else {
                            content = Objects.requireNonNull(response.body()).string();
                        }
                    }
                }

                // handle request result
                if (content != null) {
//                    Log.d("Interface.request", content);
                    JSONObject json = new JSONObject(content);
                    RequestResult.handleRequest(json, result);
                }
            }
            catch (Exception e) {
                result.handleError(e.getMessage());
                result.finished();
                e.printStackTrace();
            }
        }).start();
    }


    public void setSlurpableParticles(ArrayList<String> slurpableParticles) {
        mSlurpableXMParticles = slurpableParticles;
    }

    private long getCurrentTimestamp()
    {
        return (new Date()).getTime();
    }
}

/**********************************************************************

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

package com.norman0406.slimgress.API.Interface;

import java.net.URLEncoder;
import java.util.Date;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.norman0406.slimgress.API.Common.Location;

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

    private String sessionName;
    private String sessionId;

    // ingress api definitions
    private static final String mApiVersion = "2013-08-07T00:06:39Z a52083df5202 opt";
    private static final String mApiBase = "opengress.net";
    private static final String mApiBaseURL = "https://" + mApiBase + "/";
    private static final String mApiLogin = "_ah/login?continue=http://localhost/&auth=";
    private static final String mApiHandshake = "handshake?json=";
    private static final String mApiRequest = "rpc/";

    public Interface()
    {
        mClient = new OkHttpClient.Builder()
                .addInterceptor(
                        new DefaultRequestInterceptor("application/json"))
                .addInterceptor(chain -> {
                    final Request original = chain.request();
                    final Request fixed = original.newBuilder()
                            .addHeader("Cookie", String.join("=", sessionName, sessionId))
                            .build();
                    return chain.proceed(fixed);
                })
                .followRedirects(false)
                .build();
    }

    public AuthSuccess authenticate(String session_name, String session_id)
    {
//        FutureTask<AuthSuccess> future = new FutureTask<AuthSuccess>(() -> {
//            // see http://blog.notdot.net/2010/05/Authenticating-against-App-Engine-from-an-Android-app
//            // also use ?continue= (?)
//
//            String login = mApiBaseURL + mApiLogin + token;
//            Request get = new Request.Builder()
//                    .url(login)
//                    .build();
//
//            try {
//                Response response;
//                synchronized(Interface.this) {
////                        mClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
//                    Log.i("Interface", "executing authentication");
//                    response = mClient.newCall(get).execute();
//                }
//                @SuppressWarnings("unused")
//                String content = response.body().string();
//
//                if (response.code() == 401) {
//                    // the token has expired
//                    Log.i("Interface", "401: authentication token has expired");
//                    return AuthSuccess.TokenExpired;
//                }
//                else if (response.code() != 302) {
//                    // Response should be a redirect
//                    Log.i("Interface", "unknown error: " + response.message());
//                    return AuthSuccess.UnknownError;
//                }
//                else {
//                    // get cookie
//                    synchronized(Interface.this) {
//                        for(Cookie cookie : mClient.cookieJar().loadForRequest(HttpUrl.get(login))) {
//                            if(cookie.name().equals("SACSID")) {	// secure cookie! (ACSID is non-secure http cookie)
//                                mCookie = cookie.value();
//                            }
//                        }
//                    }
//
//                    if (mCookie == null) {
//                        Log.i("Interface", "authentication token has expired");
//                        return AuthSuccess.TokenExpired;
//                    }
//
//                    Log.i("Interface", "authentication successful");
//                    return AuthSuccess.Successful;
//                }
//            }
//            catch (Exception e) {
//                e.printStackTrace();
//            }
////                catch (IOException e) {
////                    e.printStackTrace();
////                }
//            finally {
//                synchronized(Interface.this) {
////                        mClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
//                }
//            }
//            return AuthSuccess.Successful;
//        });
//
//        // start thread
//        new Thread(future).start();
//
//        // obtain authentication return value
//        AuthSuccess retVal = AuthSuccess.UnknownError;
//        try {
//            retVal = future.get();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }
//
//        return retVal;
        sessionName = session_name;
        sessionId = session_id;
        return AuthSuccess.Successful;
    }

    public void handshake(final Handshake.Callback callback)
    {
        new Thread(() -> {
            JSONObject params = new JSONObject();
            try {
                // set handshake parameters
                params.put("nemesisSoftwareVersion", mApiVersion);
                params.put("deviceSoftwareVersion", Build.VERSION.RELEASE);

                // TODO:
                /*params.put("activationCode", "");
                params.put("tosAccepted", "1");
                params.put("a", "");*/

                String paramString = params.toString();
                paramString = URLEncoder.encode(paramString, "UTF-8");

                String handshake = mApiBaseURL + mApiHandshake + paramString;
                System.err.println(handshake);

                Request get = new Request.Builder()
                        .url(handshake)
                        .header("Accept-Charset", "utf-8")
                        .header("Cache-Control", "max-age=0")
                        .addHeader("Cookie", String.join("=", sessionName, sessionId))
                        .build();

                // do handshake
                Response response;
                synchronized(Interface.this) {
                    Log.i("Interface", "executing handshake");
                    response = mClient.newCall(get).execute();
                }
                assert(response != null);


                    String content = response.body().string();

                    // check for content type json
                    if (!Objects.requireNonNull(response.header("Content-Type")).contains("application/json"))
                        throw new RuntimeException("content type is not json");

                    content = content.replace("while(1);", "");

                    // handle handshake data
                    callback.handle(new Handshake(new JSONObject(content)));

                    Log.i("Interface", "handshake finished");

            }
            catch (Exception e) {
                e.printStackTrace();
            }
//                catch (IOException e) {
//                    e.printStackTrace();
//                }
//                catch (JSONException e) {
//                    e.printStackTrace();
//                }
        }).start();
    }

    public void request(final Handshake handshake, final String requestString, final Location playerLocation,
            final JSONObject requestParams, final RequestResult result) throws InterruptedException
    {
        if (!handshake.isValid() || handshake.getXSRFToken().length() == 0)
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
                            String loc = String.format("%08x,%08x", playerLocation.getLatitude(), playerLocation.getLongitude());
                            params.getJSONObject("params").put("playerLocation", loc);
                            params.getJSONObject("params").put("location", loc);
                        }
                        params.getJSONObject("params").put("knobSyncTimestamp", getCurrentTimestamp());

                        JSONArray collectedEnergy = new JSONArray();

                        // TODO: add collected energy guids

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
                    .header("User-Agent", "Nemesis (gzip)")
                    .header("X-XsrfToken", handshake.getXSRFToken())
                    .header("Host", mApiBase)
                    .header("Connection", "Keep-Alive")
                    .addHeader("Cookie", String.join("=", sessionName, sessionId))
                    .url(postString).build();

            // execute and get the response.
            try {
                Response response;
                String content = null;

                synchronized(Interface.this) {
                    response = mClient.newCall(post).execute();

                    if (response.code() == 401) {
                        // token expired or similar
                        //isAuthenticated = false;
//                            response.getEntity().consumeContent();
                    }
                    else {
//                            HttpEntity entity = response.getEntity();
//
//                            // decompress gzip if necessary
//                            Header contentEncoding = entity.getContentEncoding();
//                            if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip"))
//                                content = decompressGZIP(entity);
//                            else
//                                content = EntityUtils.toString(entity);
//
//                            entity.consumeContent();
                        content = response.body().string();
                    }
                }

                // handle request result
                if (content != null) {
                    System.out.println(content);
                    JSONObject json = new JSONObject(content);
                    RequestResult.handleRequest(json, result);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
//                catch (IOException e) {
//                    e.printStackTrace();
//                }
//                catch (JSONException e) {
//                    e.printStackTrace();
//                }
        }).start();
    }

    private long getCurrentTimestamp()
    {
        return (new Date()).getTime();
    }
}

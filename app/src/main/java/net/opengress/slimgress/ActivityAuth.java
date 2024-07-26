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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import net.opengress.slimgress.API.Game.GameState;
import net.opengress.slimgress.API.Interface.Interface;

import org.json.JSONObject;

import java.util.Objects;

public class ActivityAuth extends Activity {
    private final IngressApplication mApp = IngressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private int mNumAttempts = 0;
    private static final int mMaxNumAttempts = 2;


    class MyJavaScriptInterface {

        @SuppressWarnings({"UnusedAssignment", "unused"})
        @JavascriptInterface
        public void claim(String claimed) {
            String cookieName = null;
            String id = null;
            try {
                JSONObject params = new JSONObject(claimed);
                cookieName = params.getString("name");
                id = params.getString("value");
            } catch (Exception e) {
                e.printStackTrace();
                authFailed();
                return;
            }
            authFinished(cookieName, id);
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        ((TextView) findViewById(R.id.login)).setText(getString(R.string.auth_login));
        authorize();

//        Intent myIntent = getIntent();
//        setResult(RESULT_OK, myIntent);
//        finish();
    }

    private void authorize() {
        // check first if user is already logged in

        if (isLoggedIn()) {
            // user is already logged in, get login data
            SharedPreferences prefs = getSharedPreferences(getApplicationInfo().packageName, 0);
            final String sessionName = prefs.getString("session_name", null);
            final String sessionId = prefs.getString("session_id", null);

            // update username string
            runOnUiThread(() -> ((TextView) findViewById(R.id.username)).setText(sessionName));

            authFinished(sessionName, sessionId);

        } else {
            selectAccount();
        }
    }

    private void selectAccount() {
        authenticateUser();
    }

    private boolean isLoggedIn() {
        String accountName;
        String accountToken;
        // check if login data exists
        SharedPreferences prefs = getSharedPreferences(getApplicationInfo().packageName, 0);
        accountName = prefs.getString("session_name", null);
        accountToken = prefs.getString("session_id", null);

        return accountName != null && accountToken != null;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void authenticateUser() {

        WebView myWebView = new WebView(getLayoutInflater().getContext());
        myWebView.getSettings().setJavaScriptEnabled(true);
        myWebView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        myWebView.getSettings().setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(myWebView, true);
        myWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        myWebView.addJavascriptInterface(new MyJavaScriptInterface(), "textClaimer");

        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !Objects.requireNonNull(request.getUrl().getPath()).startsWith("/embed") && !request.getUrl().getPath().startsWith("/login") && !request.getUrl().getPath().startsWith("/auth");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (!url.contains("opengress.net/login?id=")) {

                    String js = "window.doAuthInWebview = function(user) {\n" +
                            "        var u = user.auth_data;\n" +
                            "        var authUrl = 'https://opengress.net/login';\n" +
                            "        authUrl += (authUrl.indexOf('?') >= 0) ? '&' : '?';\n" +
                            "        var params = [];\n" +
                            "        for (var key in u) {\n" +
                            "            params.push(key + '=' + encodeURIComponent(u[key]));\n" +
                            "        }\n" +
                            "        authUrl += params.join('&') + '&webView';\n" +
                            "        console.log(authUrl);\n" +
                            // inject here
                            "        location.href = authUrl;\n" +
                            "    }\n" +
                            "\n" +
                            "    ;if (document.location.href == 'https://opengress.net/login') {\n" +
                            "        let el = document.getElementById('telegram-login-OPRIESTbot');" +
                            "        if (el) {" +
                            "           location.assign(el.src);" +
                            "        }\n" +
                            "    } else if (document.getElementsByClassName('widget_frame_base')[0]) {\n" +
                            "        origin = 'https://opengress.net';\n" +
//                        "        //postMessage = window.doAuthInWebview;\n" +
//                        "        // window.parent = window;\n" +
                            "    }\n" +
//                        "    //\n" +
                            "\n" +
                            "    var auth = function() {\n" +
                            "        console.log('**************TRYING AUTH***************');\n" +
                            "        var theUrl ='https://oauth.telegram.org/auth?bot_id=' + TWidgetLogin.botId + (TWidgetLogin.paramsEncoded ? '&' + TWidgetLogin.paramsEncoded : '');\n" +
                            "        var xhr = new XMLHttpRequest();\n" +
                            "        xhr.onload = function(response) {\n" +
                            "           ; if(this.responseXML.title) {\n" +
                            "                console.log('**************HAVE TITLE***************');\n" +
                            "                location.href = theUrl;\n" +
                            "            } else {\n" +
                            "                console.log('**************NO HAVE TITLE***************');\n" +
                            "                TWidgetLogin.getAuth();\n" +
                            "            }\n" +
                            "        }\n" +
                            "        xhr.open('GET', theUrl);\n" +
                            "        xhr.responseType = 'document';\n" +
                            "        xhr.send();\n" +
                            "\n" +
                            "    }\n" +
                            "\n" +
                            "    var onAuth = function(origin, authData, init) {\n" +
                            "        ;if (authData) {\n" +
                            "            var data = {event: 'auth_user', auth_data: authData};\n" +
                            "        } else {\n" +
                            "            var data = {event: 'unauthorized'};\n" +
                            "        }\n" +
                            "       ; if (init) {\n" +
                            "            data.init = true;\n" +
                            "        }\n" +
//                        "        //alert(data);\n" +
                            "        window.doAuthInWebview(data);\n" +
                            "    };\n" +
                            "\n" +
                            "\n" +
                            "\n" +
                            "    ;if (typeof TWidgetLogin == 'object') {\n" +
                            "        window.TWidgetLogin.auth = auth;\n" +
                            "        window.TWidgetLogin.onAuth = onAuth;\n" +
                            "    }\n" +
                            "\n" +
                            "\n" +
//                        "    //window.addEventListener('message', doAuthInWebview, false);\n" +
                            "\n" +
                            "\n" +
                            "    function checkAuth() {\n" +
//                        "        alert('**************CHECKING AUTH***************');\n" +
                            "        clearTimeout(window.authTimeout);\n" +
                            "        window.authTimeout = setTimeout(function doCheckAuth() {\n" +
                            "            ajax('/auth/login?bot_id=392271520&origin=https%3A%2F%2Fopengress.net&request_access=write', {}, function(result) {\n" +
                            "               ; if (result) {\n" +
//                        "                    //location.reload();\n" +
//                        "                    alert('**************LOOKS GOOD NOW WHAT***************');\n" +
                            "                    window.history.back();\n" +
                            "                } else {\n" +
                            "                    checkAuth();\n" +
                            "                }\n" +
                            "            }, function (xhr) {\n" +
                            "                cancelConfirmation();\n" +
                            "                showLoginError(xhr.responseText);\n" +
                            "            });\n" +
                            "        }, 700);\n" +
                            "    }console.log(location.href);";

                    view.evaluateJavascript(js, s -> {
                        // log s string etc
                        System.out.println("Finished running login script");
                    });
                }
            }

        });

        setContentView(myWebView);
        myWebView.loadUrl("https://opengress.net/login");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                // authorize again to obtain the code
                authorize();
            } else {
                // something went wrong
                authFailed();
            }
        }
    }

    public void authFinished(final String session_name, final String session_id) {
        mNumAttempts++;
        System.out.println("AUTH FINISHED");

        new Thread(() -> {
            // authenticate ingress
            Interface.AuthSuccess success = mGame.intAuthenticate(session_name, session_id);

            if (success == Interface.AuthSuccess.Successful) {

                // save login data
                SharedPreferences prefs = getSharedPreferences(getApplicationInfo().packageName, 0);
                Editor editor = prefs.edit();
                editor.putString("session_name", session_name);
                editor.putString("session_id", session_id);
                editor.apply();

                // switch to main activity and set token result
                Intent myIntent = getIntent();
                setResult(RESULT_OK, myIntent);
                finish();
            } else if (success == Interface.AuthSuccess.TokenExpired) {
                // token expired, refresh and get a new one
                if (mNumAttempts > mMaxNumAttempts) {
                    authFailed();
                } else {
                    authenticateUser();
                }
            } else {
                // some error occurred
                authFailed();
            }
        }).start();

    }

    public void authFailed() {
        // clear login data
        SharedPreferences prefs = getSharedPreferences(getApplicationInfo().packageName, 0);
        String sessionName = prefs.getString("session_name", null);
        String sessionId = prefs.getString("session_id", null);

        if (sessionName == null || sessionId == null) {
            Editor editor = prefs.edit();
            if (sessionName == null) {
                editor.remove("session_name");
            }
            if (sessionId == null) {
                editor.remove("session_id");
            }
            editor.apply();
        }

        // switch to main activity
        Intent myIntent = getIntent();
        setResult(RESULT_CANCELED, myIntent);
        finish();
    }
}

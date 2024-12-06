package net.opengress.slimgress.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

public class NetworkMonitor {
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver networkReceiver;
    private static Boolean hasConnection;

    public void registerNetworkMonitor(@NonNull Context context, Runnable onLostConnection, Runnable onRegainedConnection) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+: Use registerDefaultNetworkCallback
            setupNetworkCallback(onLostConnection, onRegainedConnection);
            cm.registerDefaultNetworkCallback(networkCallback);
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            // API 23: Use a custom NetworkRequest
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            setupNetworkCallback(onLostConnection, onRegainedConnection);
            cm.registerNetworkCallback(request, networkCallback);
        } else {
            // For API level 21–22
            networkReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean isConnected = hasInternetConnectionCold(context);
                    if (isConnected) {
                        connectionRestored(onRegainedConnection);
                    } else {
                        connectionLost(onLostConnection);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            context.registerReceiver(networkReceiver, filter);
        }
    }

    private void setupNetworkCallback(Runnable onLostConnection, Runnable onRegainedConnection) {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLost(@NonNull Network network) {
                connectionLost(onLostConnection);
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);
                boolean validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                if (validated) {
                    connectionRestored(onRegainedConnection);
                } else {
                    connectionLost(onLostConnection);
                }
            }

            @Override
            public void onAvailable(@NonNull Network network) {
                connectionRestored(onRegainedConnection);
            }
        };
    }

    private static void connectionLost(@NonNull Runnable onLostConnection) {
        hasConnection = false;
        onLostConnection.run();
    }

    private static void connectionRestored(@NonNull Runnable onRegainedConnection) {
        hasConnection = true;
        onRegainedConnection.run();
    }

    public void unregisterNetworkMonitor(Context context) {
        hasConnection = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && networkCallback != null) {
                cm.unregisterNetworkCallback(networkCallback);
            }
        } else {
            if (networkReceiver != null) {
                context.unregisterReceiver(networkReceiver);
            }
        }
    }

    public static boolean hasInternetConnectionCold(Context context) {
        if (hasConnection != null) {
            return hasConnection;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // For API level 23 and above
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
                return capabilities != null &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnected();
            }
        }
        return false;
    }
}

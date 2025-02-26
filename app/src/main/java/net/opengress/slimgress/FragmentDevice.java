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

import static androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION;
import static net.opengress.slimgress.Constants.BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE_DEFAULT;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_KEY_SORT_VISIBLE;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_LEVEL_FILTER_VISIBLE;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_RARITY_FILTER_VISIBLE;
import static net.opengress.slimgress.Constants.PREFS_INVENTORY_SEARCH_BOX_VISIBLE;
import static net.opengress.slimgress.Constants.UNTRANSLATABLE_MAP_TILE_SOURCE_BLANK;
import static net.opengress.slimgress.ViewHelpers.setUpSpinner;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import net.opengress.slimgress.activity.ActivityMain;
import net.opengress.slimgress.activity.FragmentCredits;
import net.opengress.slimgress.api.BulkPlayerStorage;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.dialog.DialogInfo;
import net.opengress.slimgress.service.DownloadService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FragmentDevice extends Fragment {
    private final GameState mGame = SlimgressApplication.getInstance().getGame();
    private SharedPreferences mPrefs;
    private View mRootView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mRootView = inflater.inflate(R.layout.fragment_device, container, false);

        mPrefs = SlimgressApplication.getInstance().getApplicationContext().getSharedPreferences(requireContext().getApplicationInfo().packageName, Context.MODE_PRIVATE);

        mRootView.findViewById(R.id.device_button_credits).setEnabled(true);
        mRootView.findViewById(R.id.device_button_credits).setOnClickListener(v -> {
            FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.fragment_container, new FragmentCredits(), "CREDITS");
            transaction.addToBackStack("CREDITS");
            transaction.commit();
        });

        mRootView.findViewById(R.id.device_button_force_sync).setEnabled(true);
        mRootView.findViewById(R.id.device_button_force_sync).setOnClickListener(v -> {
            ((ActivityMain) requireActivity()).forceSync();
            requireActivity().getSupportFragmentManager().popBackStack(null, POP_BACK_STACK_INCLUSIVE);
        });

        mRootView.findViewById(R.id.device_button_update).setEnabled(true);
        mRootView.findViewById(R.id.device_button_update).setOnClickListener(v -> mGame.intGetLatestSlimgressVersion(new Handler(msg -> {
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            if (error != null && !error.isEmpty()) {
                FragmentActivity act = getActivity();
                if (act != null) {
                    DialogInfo dialog = new DialogInfo(act);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                }
            }
            int latest = Integer.parseInt(msg.getData().getString("version"));
            if (latest > BuildConfig.VERSION_CODE) {
                showUpdateDialog();
            } else {
                new DialogInfo(getContext()).setDismissDelay().setMessage("There is no update to install.").show();
            }
            return false;
        })));

        mRootView.findViewById(R.id.device_button_profile_link).setEnabled(false);

        // features
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_search, PREFS_INVENTORY_SEARCH_BOX_VISIBLE, false);
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_key_sort, PREFS_INVENTORY_KEY_SORT_VISIBLE, true);
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_level_filter, PREFS_INVENTORY_LEVEL_FILTER_VISIBLE, false);
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_rarity_filter, PREFS_INVENTORY_RARITY_FILTER_VISIBLE, false);

        // performance
        BulkPlayerStorage storage = mGame.getBulkPlayerStorage();
        // FIXME hardcoded
        String[] imageResolutions = {"Original", "None", "640x480", "1366x768", "1920x1080"};
        Spinner imageResolutionSpinner = setUpSpinner(imageResolutions, mRootView, R.id.device_spinner_performance_image_size);
        String previouslySelectedResolution = storage.getString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, BULK_STORAGE_DEVICE_IMAGE_RESOLUTION_DEFAULT);
        int selectedResolution = Arrays.asList(imageResolutions).indexOf(previouslySelectedResolution);
        // Default to 0 if no previous selection - NB sane default in prefs.getString
        imageResolutionSpinner.setSelection(Math.max(selectedResolution, 0));
        imageResolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean ready = false;
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (!ready) {
                    ready = true;
                    return;
                }

                String selectedResolution = imageResolutions[i];

                // this one is actually saved on the server! A first for Opengress!
                storage.putString(BULK_STORAGE_DEVICE_IMAGE_RESOLUTION, selectedResolution);
                storage.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        List<String> mapSources = new ArrayList<>(mGame.getKnobs().getMapCompositionRootKnobs().getMapProviders().keySet());
        mapSources.add(0, UNTRANSLATABLE_MAP_TILE_SOURCE_BLANK);
        Spinner mapSourceSpinner = setUpSpinner(mapSources.toArray(new String[0]), mRootView, R.id.device_spinner_performance_tile_source);
        String previouslySelectedSource = mPrefs.getString(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT);
        int selectedIndex = mapSources.indexOf(previouslySelectedSource);
        // Default to 0 if no previous selection - NB sane default in prefs.getString
        mapSourceSpinner.setSelection(Math.max(selectedIndex, 0));

        mapSourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String selectedSource = mapSources.get(i);
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putString(PREFS_DEVICE_TILE_SOURCE, selectedSource);
                editor.apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mRootView.findViewById(R.id.device_checkbox_performance_high_precision_compass).setEnabled(false);
        // Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR or Sensor.TYPE_ROTATION_VECTOR
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_performance_high_precision_compass)).setChecked(true);

        // telegram notifications
        mRootView.findViewById(R.id.device_checkbox_telegram_game_notifications).setEnabled(false);
        mRootView.findViewById(R.id.device_checkbox_telegram_news).setEnabled(false);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_telegram_game_notifications)).setChecked(true);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_telegram_news)).setChecked(true);

        // android notifications
        mRootView.findViewById(R.id.device_checkbox_notification_mentioned_comm).setEnabled(false);
        mRootView.findViewById(R.id.device_checkbox_notification_attack).setEnabled(false);
        mRootView.findViewById(R.id.device_checkbox_notification_recruiting).setEnabled(false);
        mRootView.findViewById(R.id.device_checkbox_notification_news).setEnabled(false);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_notification_mentioned_comm)).setChecked(true);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_notification_attack)).setChecked(true);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_notification_recruiting)).setChecked(true);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_notification_news)).setChecked(true);

        // FIXME this is probably not how we get this information...
        ((TextView) mRootView.findViewById(R.id.device_text_user)).setText(String.format("%s (%s)", mGame.getAgent().getNickname(), "Telegram"));

        ((TextView) mRootView.findViewById(R.id.device_build_number_text)).setText(String.format("%s %s %s", BuildConfig.VERSION_NAME, BuildConfig.BUILD_TIME, BuildConfig.BUILD_TYPE));

        return mRootView;
    }

    private void showUpdateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setCancelable(false);
        builder.setTitle("Update available");
        builder.setMessage("An update is available. Install it now for the best experience.");
        builder.setPositiveButton("Update in-app", (dialog, which) -> {
            // Start a foreground service for the download
            Intent serviceIntent = new Intent(getContext(), DownloadService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireActivity().startForegroundService(serviceIntent);
            } else {
                requireActivity().startService(serviceIntent);
            }
        });
        builder.setNegativeButton("Download update in browser", (dialog, which) -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://opengress.net/downloads/"));
            startActivity(browserIntent);
        });
        builder.setNeutralButton("Ignore", (dialog, which) -> dialog.dismiss());
        Dialog dialog = builder.create();
        dialog.show();
    }

    void setUpPrefsCheckBox(int resource, String preference, boolean defaultValue) {
        ((CheckBox) mRootView.findViewById(resource)).setChecked(mPrefs.getBoolean(preference, defaultValue));
        ((CheckBox) mRootView.findViewById(resource)).setOnCheckedChangeListener((val, val2) -> {
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putBoolean(preference, val2);
            editor.apply();
        });
    }
}

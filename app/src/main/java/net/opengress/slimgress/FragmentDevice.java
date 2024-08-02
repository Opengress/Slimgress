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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import net.opengress.slimgress.API.Game.GameState;

public class FragmentDevice extends Fragment
{
    private final GameState mGame = IngressApplication.getInstance().getGame();
    private SharedPreferences mPrefs;
    private View mRootView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {

        mRootView = inflater.inflate(R.layout.fragment_device,
                container, false);

        mPrefs = requireActivity().getSharedPreferences(requireActivity().getApplicationInfo().packageName, Context.MODE_PRIVATE);

        mRootView.findViewById(R.id.device_button_credits).setEnabled(true);
        mRootView.findViewById(R.id.device_button_credits).setOnClickListener(v -> {
            Intent myIntent = new Intent(getContext(), ActivityCredits.class);
            startActivity(myIntent);
        });

        mRootView.findViewById(R.id.device_button_profile_link).setEnabled(false);

        // features
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_search, Constants.PREFS_INVENTORY_SEARCH_BOX_VISIBLE, false);
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_key_sort, Constants.PREFS_INVENTORY_KEY_SORT_VISIBLE, true);
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_level_filter, Constants.PREFS_INVENTORY_LEVEL_FILTER_VISIBLE, false);
        setUpPrefsCheckBox(R.id.device_checkbox_features_inventory_rarity_filter, Constants.PREFS_INVENTORY_RARITY_FILTER_VISIBLE, false);

        // performance
        mRootView.findViewById(R.id.device_checkbox_performance_load_images_network).setEnabled(false);
        mRootView.findViewById(R.id.device_checkbox_performance_load_map_tiles_network).setEnabled(false);
        mRootView.findViewById(R.id.device_checkbox_performance_high_precision_compass).setEnabled(false);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_performance_load_images_network)).setChecked(true);
        ((CheckBox) mRootView.findViewById(R.id.device_checkbox_performance_load_map_tiles_network)).setChecked(true);
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

    void setUpPrefsCheckBox(int resource, String preference, boolean defaultValue) {
        ((CheckBox) mRootView.findViewById(resource)).setChecked(mPrefs.getBoolean(preference, defaultValue));
        ((CheckBox) mRootView.findViewById(resource)).setOnCheckedChangeListener((val, val2) -> {
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putBoolean(preference, val2);
            editor.apply();
        });
    }
}

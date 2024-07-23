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

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class FragmentDevice extends Fragment
{

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {

        final View rootView = inflater.inflate(R.layout.fragment_device,
                container, false);

        rootView.findViewById(R.id.buttonCredits).setEnabled(true);
        rootView.findViewById(R.id.buttonCredits).setOnClickListener(v -> {
            Intent myIntent = new Intent(getContext(), ActivityCredits.class);
            startActivity(myIntent);
        });

        ((TextView) rootView.findViewById(R.id.buildNumberText)).setText(String.format("%s %s (%s)", getText(R.string.build_number), BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE));

        return rootView;
    }
}

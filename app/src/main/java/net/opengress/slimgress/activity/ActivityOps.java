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

package net.opengress.slimgress.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import net.opengress.slimgress.FragmentDevice;
import net.opengress.slimgress.FragmentInventory;
import net.opengress.slimgress.FragmentUser;
import net.opengress.slimgress.R;

public class ActivityOps extends AppCompatActivity implements TabLayout.OnTabSelectedListener {
    SectionsPagerAdapter mSectionsPagerAdapter;
    ViewPager2 mViewPager;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ops);

        // Set up the action bar.
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the app.
        mSectionsPagerAdapter = new SectionsPagerAdapter(this);

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        // Set up the TabLayout with the ViewPager.
        TabLayout tabLayout = findViewById(R.id.tabs);
        new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0 -> tab.setText(R.string.ops_inventory);
                        case 1 -> tab.setText(R.string.ops_user);
                        case 2 -> tab.setText(R.string.ops_device);
                    }
                }).attach();
        tabLayout.addOnTabSelectedListener(this);
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        // Auto-generated method stub
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        // Auto-generated method stub
    }

    public static class SectionsPagerAdapter extends FragmentStateAdapter {
        public SectionsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position)
        {
            // getItem is called to instantiate the fragment for the given page.
            // Return a DummySectionFragment (defined as a static inner class
            // below) with the page number as its lone argument.

            Fragment fragment = null;
            if (position == 0)
                fragment = new FragmentInventory();
            else if (position == 1)
                fragment = new FragmentUser();
            else if (position == 2)
                fragment = new FragmentDevice();

            // can be used to give arguments to the fragment
            /*Bundle args = new Bundle();
            args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, position + 1);
            fragment.setArguments(args);*/

            assert fragment != null;
            return fragment;
        }

        @Override
        public int getItemCount()
        {
            return 3;
        }

    }
}

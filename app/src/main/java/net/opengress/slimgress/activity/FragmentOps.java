package net.opengress.slimgress.activity;

import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import net.opengress.slimgress.FragmentDevice;
import net.opengress.slimgress.FragmentInventory;
import net.opengress.slimgress.FragmentPasscode;
import net.opengress.slimgress.FragmentScore;
import net.opengress.slimgress.FragmentUser;
import net.opengress.slimgress.R;

public class FragmentOps extends Fragment implements TabLayout.OnTabSelectedListener {
    private ViewPager2 mViewPager;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        View rootView = inflater.inflate(R.layout.activity_ops, container, false);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the app.
        OpsPagerAdapter opsPagerAdapter = new OpsPagerAdapter(this);

        // Set up the ViewPager with the sections adapter.
        mViewPager = rootView.findViewById(R.id.pager);
        mViewPager.setAdapter(opsPagerAdapter);

        // Set up the TabLayout with the ViewPager.
        TabLayout tabLayout = rootView.findViewById(R.id.tabs);
        /*
        - inventory     ./
        - store         x
        - agent         ./ (user)
        - intel         - (score)
        - [absent]      - intel/map
        - missions      - [this would be a bit of a reach because it'd need an editor etc]
        - training      - [was initially considered out of scope, might be doable]
        - recruit[0]    - [was initially probably out of scope, could be useful...??]
        - community     - [probably a good idea... primarily just links anyway...]
        - passcode      - [will almost certainly do this]
        - device        ./
         */
        new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0 -> tab.setText(R.string.ops_inventory);
                        case 1 -> tab.setText(R.string.ops_user);
                        case 2 -> tab.setText(R.string.ops_tab_name_score);
                        case 3 -> tab.setText(R.string.passcode);
                        case 4 -> tab.setText(R.string.ops_device);
                    }
                }).attach();
        tabLayout.addOnTabSelectedListener(this);

        rootView.findViewById(R.id.activity_ops_back_button).setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        return rootView;
    }

    @Override
    public void onTabSelected(@NonNull TabLayout.Tab tab) {
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

    public static class OpsPagerAdapter extends FragmentStateAdapter {
        private final SparseArray<Fragment> mFragmentCache = new SparseArray<>();

        public OpsPagerAdapter(@NonNull FragmentOps fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position)
        {
            Fragment fragment = mFragmentCache.get(position);
            if (fragment == null) {
                fragment = createNewFragment(position);
                mFragmentCache.put(position, fragment);
            }
            return fragment;
        }

        @Override
        public int getItemCount()
        {
            return 5;
        }

        private Fragment createNewFragment(int position) {
            return switch (position) {
                case 0 -> new FragmentInventory();
                case 1 -> new FragmentUser();
                case 2 -> new FragmentScore();
                case 3 -> new FragmentPasscode();
                case 4 -> new FragmentDevice();
                default -> throw new IllegalArgumentException("Invalid position: " + position);
            };
        }

    }
}

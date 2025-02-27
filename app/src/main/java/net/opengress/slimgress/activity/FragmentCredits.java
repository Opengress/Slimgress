package net.opengress.slimgress.activity;

import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE;
import static net.opengress.slimgress.Constants.PREFS_DEVICE_TILE_SOURCE_DEFAULT;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;

public class FragmentCredits extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View rootView = inflater.inflate(R.layout.activity_credits, container, false);
        String html = String.format(getString(R.string.credits), getMapAttribution());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ((TextView) rootView.findViewById(R.id.creditsText)).setText(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));
        } else {
            ((TextView) rootView.findViewById(R.id.creditsText)).setText(Html.fromHtml(html));
        }
        // Crash on Galaxy S3 with OS 4.3 (currently unsupported device) suspected due to LinkMovementMethod
        ((TextView) rootView.findViewById(R.id.creditsText)).setMovementMethod(LinkMovementMethod.getInstance());
        rootView.findViewById(R.id.activityCreditsOkButton).setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        return rootView;
    }

    private String getMapAttribution() {
        var prefs = SlimgressApplication.getInstance().getApplicationContext().getSharedPreferences(requireContext().getApplicationInfo().packageName, Context.MODE_PRIVATE);
        String previouslySelectedSource = prefs.getString(PREFS_DEVICE_TILE_SOURCE, PREFS_DEVICE_TILE_SOURCE_DEFAULT);
        var providers = SlimgressApplication.getInstance().getGame().getKnobs().getMapCompositionRootKnobs().getMapProviders();
        var thisMapProvider = providers.get(previouslySelectedSource);
        if (thisMapProvider == null) {
            return "";
        }
        return thisMapProvider.getAttribution();
    }

}

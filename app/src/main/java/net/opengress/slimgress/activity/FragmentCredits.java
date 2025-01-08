package net.opengress.slimgress.activity;

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

public class FragmentCredits extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View rootView = inflater.inflate(R.layout.activity_credits, container, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ((TextView) rootView.findViewById(R.id.creditsText)).setText(Html.fromHtml(getString(R.string.credits), Html.FROM_HTML_MODE_LEGACY));
        } else {
            ((TextView) rootView.findViewById(R.id.creditsText)).setText(Html.fromHtml(getString(R.string.credits)));
        }
        // FIXME crash on Galaxy S3 with OS 4.3 (WHY? haven't tested)
        ((TextView) rootView.findViewById(R.id.creditsText)).setMovementMethod(LinkMovementMethod.getInstance());
        rootView.findViewById(R.id.activityCreditsOkButton).setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        return rootView;
    }

}

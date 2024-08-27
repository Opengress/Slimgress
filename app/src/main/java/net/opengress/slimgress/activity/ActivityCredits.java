package net.opengress.slimgress.activity;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import net.opengress.slimgress.R;

public class ActivityCredits extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credits);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ((TextView)findViewById(R.id.creditsText)).setText(Html.fromHtml(getString(R.string.credits), Html.FROM_HTML_MODE_LEGACY));
        } else {
            ((TextView)findViewById(R.id.creditsText)).setText(Html.fromHtml(getString(R.string.credits)));
        }
        // FIXME crash on Galaxy S3 with OS 4.3 (WHY? haven't tested)
        ((TextView)findViewById(R.id.creditsText)).setMovementMethod(LinkMovementMethod.getInstance());
    }

}

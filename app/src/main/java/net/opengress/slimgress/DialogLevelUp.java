package net.opengress.slimgress;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class DialogLevelUp extends Dialog {
    public DialogLevelUp(Context context) {
        super(context);
        setContentView(R.layout.dialog_levelup);

        Window window = getWindow();
        if (window != null) {
            window.setDimAmount(0.25F);
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());
            layoutParams.gravity = Gravity.BOTTOM;
            layoutParams.y = 100; // offset from bottom
            window.setAttributes(layoutParams);
            // make default dialog background go away
            window.setBackgroundDrawable(new ColorDrawable(0));
        }
    }

    public void setMessage(String msg, int color) {
        ((TextView) findViewById(R.id.level_up_hero_text)).setText(msg);
        ((TextView) findViewById(R.id.level_up_hero_text)).setTextColor(color);
    }

}

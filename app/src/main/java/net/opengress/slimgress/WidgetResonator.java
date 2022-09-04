package net.opengress.slimgress;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;


public class WidgetResonator extends LinearLayout {

    public WidgetResonator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // Inflate the custom widget layout xml file.
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        layoutInflater.inflate(R.layout.widget_resonator, this);

    }
}

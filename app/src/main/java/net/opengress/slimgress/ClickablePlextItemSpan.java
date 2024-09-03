package net.opengress.slimgress;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

public class ClickablePlextItemSpan extends ClickableSpan {
    private final String mData;
    private final int mColour;
    private final PlextSpanClickListener mListener;

    public ClickablePlextItemSpan(String data, int colour, PlextSpanClickListener listener) {
        mData = data;
        mColour = colour;
        mListener = listener;
    }

    @Override
    public void onClick(@NonNull View widget) {
        // Call the callback method
        if (mListener != null) {
            mListener.onSpanClick(mData);
        }
    }


    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        super.updateDrawState(ds);
        ds.setColor(mColour); // Set the text color
        ds.setUnderlineText(false); // Remove underline
    }

}

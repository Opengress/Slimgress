package net.opengress.slimgress;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;


public class WidgetLongPressButton extends AppCompatButton {

    private final static boolean BAR_GOES_ON_TOP = true;
    private final int barHeight = 6;
    private int progress = 0;      // Current progress
    private int maxProgress = 100; // Maximum progress

    private Paint barPaint;
    private Paint barBackgroundPaint;

    private static final int LONG_PRESS_DURATION_MS = ViewConfiguration.getLongPressTimeout();
    private final Choreographer choreographer = Choreographer.getInstance();
    private Choreographer.FrameCallback frameCallback;
    private long pressStartTime;

    private boolean wantsProgressBar = false;

    public WidgetLongPressButton(Context context) {
        super(context);
        init();
    }

    public WidgetLongPressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WidgetLongPressButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {

        // Paint for the progress portion
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(0xFF990000);

        // Paint for the background portion behind the progress
        barBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barBackgroundPaint.setColor(0xFFCCCCCC);

        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startProgressCountdown();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!isTouchInsideView(v, event)) {
                        stopProgressCountdown();
                        setProgress(0);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopProgressCountdown();
                    setProgress(0);
                    break;
            }
            return false;
        });
    }

    private boolean isTouchInsideView(View v, MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        return x >= 0 && x <= v.getWidth() && y >= 0 && y <= v.getHeight();
    }

    @Override
    public void setOnLongClickListener(@Nullable OnLongClickListener l) {
        super.setOnLongClickListener(l);
        wantsProgressBar = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (wantsProgressBar) {
            int alpha = (progress < 1 || progress > 99) ? 0 : (int) (255 * 0.9f);
            barPaint.setAlpha(alpha);
            barBackgroundPaint.setAlpha(alpha);
            if (BAR_GOES_ON_TOP) {
                drawUpTop(canvas);
            } else {
                drawDownAtBottom(canvas);
            }
        }
    }

    private void drawUpTop(Canvas canvas) {
        // Draw a thin bar at the top of the button to show progress
        int width = getWidth();

        // Draw the background for the bar
        // Height of the progress bar in pixels
        //        canvas.drawRect(0, 0, width, barHeight, barBackgroundPaint);

        // Draw the progress portion
        float ratio = (float) progress / maxProgress;
        int progressWidth = (int) (width * ratio);
        canvas.drawRect(0, 0, progressWidth, barHeight, barPaint);
    }

    private void drawDownAtBottom(Canvas canvas) {
        // Draw a thin bar at the bottom of the button to show progress
        int width = getWidth();
        int height = getHeight();

        // Draw the background for the bar
        // Height of the progress bar in pixels
        int barTop = height - barHeight;
//        canvas.drawRect(0, barTop, width, height, barBackgroundPaint);

        // Draw the progress portion
        float ratio = (float) progress / maxProgress;
        int progressWidth = (int) (width * ratio);
        canvas.drawRect(0, barTop, progressWidth, height, barPaint);
    }

    /**
     * Set the current progress of this button's tiny progress bar.
     */
    public void setProgress(int progress) {
        // Constrain progress to [0, maxProgress].
        if (progress < 0) {
            progress = 0;
        } else if (progress > maxProgress) {
            progress = maxProgress;
        }
        this.progress = progress;
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    /**
     * Sets the maximum value for the progress bar.
     */
    public void setMaxProgress(int max) {
        if (max < 1) {
            max = 1; // avoid divide-by-zero
        }
        maxProgress = max;
        // Optionally reset progress if beyond new max
        if (progress > maxProgress) {
            progress = maxProgress;
        }
        invalidate();
    }

    private void startProgressCountdown() {
        // If there's already a callback, remove it first (in case user quickly taps again).
        if (frameCallback != null) {
            choreographer.removeFrameCallback(frameCallback);
        }

        pressStartTime = System.currentTimeMillis();

        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                long elapsed = System.currentTimeMillis() - pressStartTime;
                float fraction = (float) elapsed / LONG_PRESS_DURATION_MS;
                fraction = Math.min(fraction, 1f);

                // Update the custom progress bar
                setProgress((int) (fraction * getMaxProgress()));

                // Keep animating until we reach the target fraction
                if (fraction < 1f) {
                    choreographer.postFrameCallback(this);
                } //else it's finished and we can do something else like hide the bar
            }
        };

        choreographer.postFrameCallback(frameCallback);
    }

    private void stopProgressCountdown() {
        // Remove any pending callbacks
        if (frameCallback != null) {
            choreographer.removeFrameCallback(frameCallback);
            frameCallback = null;
        }

        // Optionally reset progress
        setProgress(0);
    }
}

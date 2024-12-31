package net.opengress.slimgress;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Map;

public class PieChartView extends View {
    private Map<String, Integer> mScores;
    private Map<String, Integer> mColours;
    private final Paint mPaint;
    private final RectF mRect;

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRect = new RectF();
    }

    public void setData(Map<String, Integer> scores, Map<String, Integer> colors) {
        mScores = scores;
        mColours = colors;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRect.set(
                (float) getWidth() / 2 - (Math.min(getWidth() / 2, getHeight() / 2) - 20),
                (float) getHeight() / 2 - (Math.min(getWidth() / 2, getHeight() / 2) - 20),
                (float) getWidth() / 2 + Math.min(getWidth() / 2, getHeight() / 2) - 20,
                (float) getHeight() / 2 + Math.min(getWidth() / 2, getHeight() / 2) - 20
        );
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mScores == null || mScores.isEmpty()) {
            return;
        }

        float startAngle = 90;
        float total = 0;
        for (int value : mScores.values()) {
            total += value;
        }

        for (Map.Entry<String, Integer> entry : mScores.entrySet()) {
            float sweepAngle = 360f * (entry.getValue() / total);
            mPaint.setColor(mColours.containsKey(entry.getKey()) ? mColours.get(entry.getKey()) : 0xFF888888);
            canvas.drawArc(mRect, startAngle, sweepAngle, true, mPaint);

            startAngle += sweepAngle;
        }
    }
}

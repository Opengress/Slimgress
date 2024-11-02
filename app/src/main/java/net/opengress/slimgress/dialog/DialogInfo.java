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

package net.opengress.slimgress.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DialogInfo extends Dialog
{
    private ScheduledFuture<?> mDismissTask;
    private WeakReference<Context> mContextRef;

    public DialogInfo(Context context)
    {
        super(context);
        setContentView(R.layout.dialog_infobox);
        mContextRef = new WeakReference<>(context);

        Objects.requireNonNull(getWindow()).setWindowAnimations(R.style.FadeAnimation);
        //getWindow().setBackgroundDrawable(new ColorDrawable(android.R.color.transparent));
        getWindow().setBackgroundDrawable(new ColorDrawable(0));

        // set additional parameters
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.dimAmount = 0.0f;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        getWindow().setAttributes(lp);

        findViewById(R.id.message).setVisibility(View.INVISIBLE);
        findViewById(R.id.title).setVisibility(View.INVISIBLE);
    }

    public DialogInfo setMessage(String msg)
    {
        ((TextView)findViewById(R.id.message)).setText(msg);
        findViewById(R.id.message).setVisibility(View.VISIBLE);
        return this;
    }

    public DialogInfo setTitle(String title)
    {
        ((TextView)findViewById(R.id.title)).setText(title);
        findViewById(R.id.title).setVisibility(View.VISIBLE);
        return this;
    }

    public DialogInfo setDismissDelay()
    {
        return setDismissDelay(3000);
    }

    public DialogInfo setDismissDelay(int delay)
    {
        // automatically dismiss dialog after x seconds
        setOnShowListener(dialog -> mDismissTask = SlimgressApplication.schedule(dialog::dismiss, delay, TimeUnit.MILLISECONDS));
        return this;
    }

    public DialogInfo setTouchable(boolean isTouchable)
    {
        if (isTouchable) {
            Objects.requireNonNull(getWindow()).addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }
        else {
            Objects.requireNonNull(getWindow()).clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }

        return this;
    }

    @Override
    public void show() {
        Context context = mContextRef.get();
        if (context instanceof Activity && !((Activity) context).isFinishing()) {
            super.show();
        } else {
            Toast.makeText(SlimgressApplication.getInstance(), ((TextView) findViewById(R.id.message)).getText(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (mDismissTask != null && !mDismissTask.isDone()) {
            mDismissTask.cancel(true);
        }
        super.onDetachedFromWindow();
    }
}

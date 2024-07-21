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

package net.opengress.slimgress;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Html;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class DialogHackResult extends Dialog
{

    public DialogHackResult(Context context)
    {
        super(context);
        setContentView(R.layout.dialog_hack_result);

        Objects.requireNonNull(getWindow()).setWindowAnimations(R.style.FadeAnimation);
        //getWindow().setBackgroundDrawable(new ColorDrawable(android.R.color.transparent));
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        getWindow().setGravity(Gravity.BOTTOM);

        // set additional parameters
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.dimAmount = 0.0f;
        getWindow().setAttributes(lp);

        findViewById(R.id.message).setVisibility(View.INVISIBLE);
        findViewById(R.id.title).setVisibility(View.INVISIBLE);
        RecyclerView itemView = findViewById(R.id.items);
        if (itemView != null) {
            itemView.setVisibility(View.INVISIBLE);
        }

        setDismissDelay();
    }

    // to be used in case of error eg hack acquired no items
    public void setMessage(String msg)
    {
        ((TextView)findViewById(R.id.message)).setText(msg);
        findViewById(R.id.message).setVisibility(View.VISIBLE);
    }

    // to carry text like "Acquired Items:" or "Bonus items:"
    public DialogHackResult setTitle(String title)
    {
        ((TextView)findViewById(R.id.title)).setText(title);
        if (title.charAt(0) == 'B') {
            ((TextView) findViewById(R.id.title)).setTextColor(0xff660000);
        }
        findViewById(R.id.title).setVisibility(View.VISIBLE);
        return this;
    }

    public void setDismissDelay()
    {
        setDismissDelay(3000);
    }

    public void setDismissDelay(int delay)
    {
        // automatically dismiss dialog after 3 seconds
        setOnShowListener(dialog -> new Timer().schedule(new TimerTask() {
            @Override
            public void run()
            {
                dialog.dismiss();
            }
        }, delay));
    }

    // to be used when hack acquired items!
    public void setItems(HashMap<String, Integer> data) {

        HashMap<String, Pair<String, Integer>> items = new HashMap<>();
        Set<String> strings = data.keySet();

        for (String string : strings) {
            items.put(string, new Pair<>(string, data.get(string)));
        }

        RecyclerView itemView = findViewById(R.id.items);
        itemView.setLayoutManager(new GridLayoutManager(getContext(), 2, RecyclerView.VERTICAL, false));
        HackItemViewAdapter adapter = new HackItemViewAdapter(getContext(), items);
        itemView.setVisibility(View.VISIBLE);
        itemView.setAdapter(adapter);

    }

    private static class HackItemViewAdapter extends RecyclerView.Adapter<HackItemViewAdapter.ViewHolder> {

        private final Pair<String, Integer>[] mData;
        private final LayoutInflater mInflater;

        // data is passed into the constructor
        @SuppressWarnings("unchecked")
        public HackItemViewAdapter(Context context, HashMap<String, Pair<String, Integer>> items) {
            this.mInflater = LayoutInflater.from(context);
            this.mData = (Pair<String, Integer>[]) items.values().toArray(new Pair[0]);
        }

        // inflates the row layout from xml when needed
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = mInflater.inflate(R.layout.hacked_item_layout, parent, false);
            return new ViewHolder(view);
        }

        // binds the data to the TextView in each row
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Pair<String, Integer> item = mData[position];
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                holder.quantityField.setText(Html.fromHtml(item.second +"x", Html.FROM_HTML_MODE_LEGACY));
                holder.descriptionField.setText(Html.fromHtml(item.first, Html.FROM_HTML_MODE_LEGACY));
            } else {
                holder.quantityField.setText(Html.fromHtml(item.second +"x"));
                holder.descriptionField.setText(Html.fromHtml(item.first));
            }
        }

        // total number of rows
        @Override
        public int getItemCount() {
            return mData.length;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView quantityField;
            final TextView descriptionField;

            ViewHolder(View itemView) {
                super(itemView);
                quantityField = itemView.findViewById(R.id.itemQuantity);
                descriptionField = itemView.findViewById(R.id.itemDescription);
            }
        }

    }
}

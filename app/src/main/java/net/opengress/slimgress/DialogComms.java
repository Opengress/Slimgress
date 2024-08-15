package net.opengress.slimgress;

import static net.opengress.slimgress.API.Common.Utils.getErrorStringFromAPI;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import net.opengress.slimgress.API.Plext.PlextBase;
import net.opengress.slimgress.API.ViewModels.CommsViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class DialogComms extends BottomSheetDialogFragment {

    private YourAdapter mAdaptor;
    private CommsViewModel mCommsViewModel;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_Dark_BottomSheetDialog);
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setContentView(R.layout.dialog_comms);
        Objects.requireNonNull(dialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);


//        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
//        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
//
//        DisplayMetrics displayMetrics = new DisplayMetrics();
//        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
//        int screenHeight = displayMetrics.heightPixels;
//
//        behavior.setPeekHeight(screenHeight / 2);
//        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        TabLayout tabLayout = dialog.findViewById(R.id.tabs);
        RecyclerView view = dialog.findViewById(R.id.recyclerView);
        assert view != null;
        view.setLayoutManager(new LinearLayoutManager(getContext()));
        view.setNestedScrollingEnabled(true);

        // Initialize ViewModel
        mCommsViewModel = SlimgressApplication.getInstance().getCommsViewModel();

        // Set initial data
        mAdaptor = new YourAdapter(new ArrayList<>());
        view.setAdapter(mAdaptor);

        assert tabLayout != null;

        // Observe the LiveData from ViewModel
        mCommsViewModel.getAllMessages().observe(this, messages -> {
            if (tabLayout.getSelectedTabPosition() == 0) {
                mAdaptor.updateData(messages);
            }
        });
        mCommsViewModel.getFactionMessages().observe(this, messages -> {
            if (tabLayout.getSelectedTabPosition() == 1) {
                mAdaptor.updateData(messages);
            }
        });
        mCommsViewModel.getAlertMessages().observe(this, messages -> {
            if (tabLayout.getSelectedTabPosition() == 2) {
                mAdaptor.updateData(messages);
            }
        });
        mCommsViewModel.getInfoMessages().observe(this, messages -> {
            if (tabLayout.getSelectedTabPosition() == 3) {
                mAdaptor.updateData(messages);
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0 -> mAdaptor.updateData(mCommsViewModel.getAllMessages().getValue());
                    case 1 -> mAdaptor.updateData(mCommsViewModel.getFactionMessages().getValue());
                    case 2 -> mAdaptor.updateData(mCommsViewModel.getAlertMessages().getValue());
                    case 3 -> mAdaptor.updateData(mCommsViewModel.getInfoMessages().getValue());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        Button sendButton = dialog.findViewById(R.id.button);
        EditText input = dialog.findViewById(R.id.input);
        assert sendButton != null;
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Handler handler = new Handler(msg -> {
                    var data = msg.getData();
                    String error = getErrorStringFromAPI(data);
                    if (error != null && !error.isEmpty()) {
                        DialogInfo dialog = new DialogInfo(getContext());
                        dialog.setMessage(error).setDismissDelay(1500).show();
                    } else {
                        // get plexts, probably, and...
                        assert input != null;
                        input.setText("");
                    }
                    return false;
                });
                assert input != null;
                SlimgressApplication.getInstance().getGame().intSendMessage(input.getText().toString(), tabLayout.getSelectedTabPosition() == 1, handler);
            }
        });

        return dialog;
    }

    public static class YourAdapter extends RecyclerView.Adapter<YourAdapter.ViewHolder> {
        private List<PlextBase> data;

        public YourAdapter(List<PlextBase> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.widget_comms_line, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (data == null) {
                holder.textView.setText(R.string.nothing_to_display);
                holder.timeView.setText("");
                return;
            }
            PlextBase plext = data.get(position);
            holder.textView.setText(plext.getFormattedText());
            holder.textView.setMovementMethod(LinkMovementMethod.getInstance());

            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            String formattedTime = sdf.format(new Date(Long.parseLong(plext.getEntityTimestamp())));
            holder.timeView.setText(formattedTime);
        }

        @Override
        public int getItemCount() {
            return data == null ? 1 : data.size();
        }

        public void updateData(List<PlextBase> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            TextView timeView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.plext_text);
                timeView = itemView.findViewById(R.id.plext_time);
            }
        }
    }
}

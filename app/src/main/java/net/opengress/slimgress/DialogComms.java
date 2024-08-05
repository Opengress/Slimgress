package net.opengress.slimgress;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import net.opengress.slimgress.API.ViewModels.CommsViewModel;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DialogComms extends BottomSheetDialogFragment {

    private RecyclerView mRecyclerView;
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

        TabLayout tabLayout = dialog.findViewById(R.id.tabs);
        mRecyclerView = dialog.findViewById(R.id.recyclerView);
        assert mRecyclerView != null;
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize ViewModel
        mCommsViewModel = IngressApplication.getInstance().getCommsViewModel();

        // Set initial data
        mAdaptor = new YourAdapter(new ArrayList<>());
        mRecyclerView.setAdapter(mAdaptor);

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
                    case 0:
                        mAdaptor.updateData(mCommsViewModel.getAllMessages().getValue());
                        break;
                    case 1:
                        mAdaptor.updateData(mCommsViewModel.getFactionMessages().getValue());
                        break;
                    case 2:
                        mAdaptor.updateData(mCommsViewModel.getAlertMessages().getValue());
                        break;
                    case 3:
                        mAdaptor.updateData(mCommsViewModel.getInfoMessages().getValue());
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        return dialog;
    }

    public class YourAdapter extends RecyclerView.Adapter<YourAdapter.ViewHolder> {
        private List<SimpleEntry<Long, String>> data;

        public YourAdapter(List<SimpleEntry<Long, String>> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.comms_line, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SimpleEntry<Long, String> entry = data.get(position);
            holder.textView.setText(entry.getValue());
        }

        @Override
        public int getItemCount() {
            return data == null ? 0 : data.size();
        }

        public void updateData(List<SimpleEntry<Long, String>> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.plext_text);
            }
        }
    }
}

package net.opengress.slimgress.dialog;

import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import net.opengress.slimgress.R;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Plext.PlextBase;
import net.opengress.slimgress.api.ViewModels.CommsViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class DialogComms extends BottomSheetDialogFragment {

    private YourAdapter mAdaptor;
    private CommsViewModel mAllCommsViewModel;
    private CommsViewModel mFactionCommsViewModel;
    private static boolean mIsInFactionTab = false;
    private final Handler mTimerHandler = new Handler();
    private Runnable mTimerRunnable;
    private boolean mIsTimerRunning = false;
    private int commsRadiusKM = 50;
    private BottomSheetDialog mDialog;
    private final Handler commsRefreshHandler = new Handler(msg -> {
        if (mIsInFactionTab) {
            mAdaptor.updateData(mFactionCommsViewModel.getMessages().getValue());
        } else {
            mAdaptor.updateData(mAllCommsViewModel.getMessages().getValue());
        }
        RecyclerView view = mDialog.findViewById(R.id.recyclerView);
        assert view != null;
        view.scrollToPosition(mAdaptor.getItemCount() - 1);
        return true;
    });

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Material3_Dark_BottomSheetDialog);
        mDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        mDialog.setContentView(R.layout.dialog_comms);
        Objects.requireNonNull(mDialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        mDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        TabLayout tabLayout = mDialog.findViewById(R.id.tabs);
        RecyclerView view = mDialog.findViewById(R.id.recyclerView);
        assert view != null;
        view.setLayoutManager(new LinearLayoutManager(getContext()));
        view.setNestedScrollingEnabled(true);

        // Initialize ViewModel
        mAllCommsViewModel = SlimgressApplication.getInstance().getAllCommsViewModel();
        mFactionCommsViewModel = SlimgressApplication.getInstance().getFactionCommsViewModel();

        // Set initial data
        mAdaptor = new YourAdapter(new ArrayList<>());
        view.setAdapter(mAdaptor);

        assert tabLayout != null;

        // Observe the LiveData from ViewModel
        mAllCommsViewModel.getMessages().observe(this, messages -> {
            if (tabLayout.getSelectedTabPosition() == 0) {
                mAdaptor.updateData(messages);
            }
        });
        mFactionCommsViewModel.getMessages().observe(this, messages -> {
            if (tabLayout.getSelectedTabPosition() == 1) {
                mAdaptor.updateData(messages);
            }
        });

        mTimerRunnable = new Runnable() {
            @Override
            public void run() {
                new Thread(() -> SlimgressApplication.getInstance().getGame().intLoadCommunication(commsRadiusKM, mIsInFactionTab, commsRefreshHandler)).start();
                mTimerHandler.postDelayed(this, 15000);
            }
        };

        Button sendButton = mDialog.findViewById(R.id.button);
        EditText input = mDialog.findViewById(R.id.input);
        assert sendButton != null;
        assert input != null;

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0 -> {
                        sendButton.setEnabled(true);
                        input.setEnabled(true);
                        mIsInFactionTab = false;
                        mAdaptor.updateData(mAllCommsViewModel.getMessages().getValue());
                        view.scrollToPosition(mAdaptor.getItemCount() - 1);
                    }
                    case 1 -> {
                        sendButton.setEnabled(true);
                        input.setEnabled(true);
                        mIsInFactionTab = true;
                        mAdaptor.updateData(mFactionCommsViewModel.getMessages().getValue());
                        view.scrollToPosition(mAdaptor.getItemCount() - 1);
                    }
                }
                new Thread(() -> SlimgressApplication.getInstance().getGame().intLoadCommunication(commsRadiusKM, mIsInFactionTab, commsRefreshHandler)).start();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Deactivate the button if the text field is empty or contains more than 512 characters
                sendButton.setEnabled(s.length() > 0 && s.length() <= 512);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (sendButton.isEnabled()) {
                    sendButton.performClick();
                }
                return true;
            }
            return false;
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (sendButton.isEnabled()) {
                    sendButton.performClick();
                }
                return true;
            }
            return false;
        });

        sendButton.setOnClickListener(v -> {
            Handler handler = new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog1 = new DialogInfo(getContext());
                    dialog1.setMessage(error).setDismissDelay(1500).show();
                } else {
                    // get plexts, probably, and...
                    input.setText("");
                    sendButton.setEnabled(true);
                    new Thread(() -> SlimgressApplication.getInstance().getGame().intLoadCommunication(commsRadiusKM, mIsInFactionTab, commsRefreshHandler)).start();
                }
                return false;
            });
            sendButton.setEnabled(false);
            SlimgressApplication.getInstance().getGame().intSendMessage(input.getText().toString(), tabLayout.getSelectedTabPosition() == 1, handler);
        });

        return mDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Start the timer when the dialog is shown
        if (!mIsTimerRunning) {
            startTimer();
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        stopTimer();
        super.onDismiss(dialog);
    }

    private void startTimer() {
        if (!mIsTimerRunning) {
            mTimerHandler.post(mTimerRunnable); // Start the timer
            mIsTimerRunning = true;
        }
    }

    private void stopTimer() {
        if (mIsTimerRunning) {
            mTimerHandler.removeCallbacks(mTimerRunnable); // Stop the timer
            mIsTimerRunning = false;
        }
    }

    public static class YourAdapter extends RecyclerView.Adapter<YourAdapter.ViewHolder> {
        private List<PlextBase> mPlexts;

        public YourAdapter(List<PlextBase> plexts) {
            mPlexts = plexts;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.widget_comms_line, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (mPlexts == null) {
                holder.textView.setText(R.string.nothing_to_display);
                holder.timeView.setText("");
                return;
            }
            PlextBase plext = mPlexts.get(position);
            holder.textView.setText(plext.getFormattedText(mIsInFactionTab));
            holder.textView.setMovementMethod(LinkMovementMethod.getInstance());

            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            String formattedTime = sdf.format(new Date(Long.parseLong(plext.getEntityTimestamp())));
            holder.timeView.setText(formattedTime);
            if (plext.atMentionsPlayer()) {
                holder.timeView.setTextAppearance(SlimgressApplication.getInstance(), R.style.PlextTimeMentionedTextView);
            } else {
                holder.timeView.setTextAppearance(SlimgressApplication.getInstance(), R.style.PlextTimeTextView);
            }
        }

        @Override
        public int getItemCount() {
            return mPlexts == null ? 1 : mPlexts.size();
        }

        public void updateData(List<PlextBase> newData) {
            this.mPlexts = newData;
            notifyDataSetChanged();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;
            final TextView timeView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.plext_text);
                timeView = itemView.findViewById(R.id.plext_time);
            }
        }
    }
}

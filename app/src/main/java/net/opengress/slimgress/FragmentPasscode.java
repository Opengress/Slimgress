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

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;
import static net.opengress.slimgress.ViewHelpers.putItemInMap;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.opengress.slimgress.api.Item.ItemBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FragmentPasscode extends Fragment {

    private View mV;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mV = inflater.inflate(R.layout.fragment_passcode,
                container, false);


        Handler handler = new Handler(Looper.getMainLooper());
        Runnable task = new Runnable() {
            private int mCurrentRotation = 0;

            @Override
            public void run() {
                mCurrentRotation += 5; // Rotate by 10 degrees each step
                if (mCurrentRotation >= 360) {
                    mCurrentRotation -= 360;
                }
                mV.findViewById(R.id.passcodeThrobber).setRotation(mCurrentRotation);

                // Schedule the next frame
                handler.postDelayed(this, 16); // Approx. 60 FPS
            }
        };


        mV.<EditText>findViewById(R.id.passcodeTextField).addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence text, int start, int before, int after) {
                mV.findViewById(R.id.passcodeSubmitButton).setEnabled(text.length() > 0);
            }

            public void afterTextChanged(Editable editable) {
            }

            public void beforeTextChanged(CharSequence text, int start, int before, int after) {
            }
        });

        mV.<EditText>findViewById(R.id.passcodeTextField).setOnEditorActionListener((view, action, event) -> {
            if (view.length() < 1) {
                mV.findViewById(R.id.passcodeSubmitButton).setEnabled(false);
                return false;
            }
            if (action == IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KEYCODE_ENTER && event.getAction() == ACTION_DOWN)) {
                mV.findViewById(R.id.passcodeSubmitButton).performClick();
                return true;
            }
            return false;
        });
        mV.findViewById(R.id.passcodeSubmitButton).setOnClickListener(v -> {
            String str = mV.<EditText>findViewById(R.id.passcodeTextField).getText().toString();
            if (str.isEmpty()) {
                return;
            }
            mV.findViewById(R.id.passcodeResultSection).setVisibility(INVISIBLE);
            mV.findViewById(R.id.redemptionStatusText).setVisibility(VISIBLE);
            // FIXME use colour from pallette
            mV.<TextView>findViewById(R.id.redemptionStatusText).setTextColor(0xFF00FFFF);
            mV.<TextView>findViewById(R.id.redemptionStatusText).setText(R.string.validating);
            startAnimation(handler, task);
            SlimgressApplication.getInstance().getGame().intRedeemReward(str, new Handler(msg -> {
                stopAnimation(handler, task);
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    // FIXME use colour from pallette
                    mV.<TextView>findViewById(R.id.redemptionStatusText).setTextColor(0xFFDD0000);
                    mV.<TextView>findViewById(R.id.redemptionStatusText).setText(error);
                } else {
                    Context ctx = getContext();
                    if (ctx != null) {
                        ((InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mV.<EditText>findViewById(R.id.passcodeTextField).getWindowToken(), 0);
                    }
                    mV.<TextView>findViewById(R.id.redemptionStatusText).setTextColor(0xFF00FFFF);
                    mV.<TextView>findViewById(R.id.redemptionStatusText).setText(R.string.passcode_confirmed);
                    mV.findViewById(R.id.passcodeResultSection).setVisibility(VISIBLE);
                    // FIXME use theme colours
                    mV.<TextView>findViewById(R.id.redemptionLootText).setTextColor(0xFF999900);

                    long AP = data.getLong("apAward", 0);
                    long XM = data.getLong("xmAward", 0);
                    @SuppressLint("DefaultLocale") String text = "Gained: \n" + (AP > 0 ? String.format("%d AP\n", AP) : "");
                    mV.<TextView>findViewById(R.id.redemptionLootText).setText(getItemLootString(text, XM, (ArrayList<ItemBase>) data.getSerializable("inventoryAward"), (ArrayList<String>) data.getSerializable("additionalAwards")));
                }
                return true;
            }));
        });

        mV.findViewById(R.id.redeemButton).setOnClickListener(v -> {
            v.setVisibility(GONE);
            mV.findViewById(R.id.passcodeMainScreen).setVisibility(VISIBLE);
        });

        return mV;
    }

    private void startAnimation(Handler handler, Runnable task) {
        mV.findViewById(R.id.passcodeThrobber).setVisibility(VISIBLE);
        Context ctx = getContext();
        if (ctx != null && Settings.Global.getFloat(
                ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f) == 0) {
            handler.post(task);
        }
    }

    private void stopAnimation(Handler handler, Runnable task) {
        mV.findViewById(R.id.passcodeThrobber).setVisibility(GONE);
        Context ctx = getContext();
        if (ctx != null && Settings.Global.getFloat(
                ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f) == 0) {
            handler.removeCallbacks(task);
        }
    }

    @NonNull
    private String getItemLootString(String header, long XM, ArrayList<ItemBase> items, ArrayList<String> extras) {

        StringBuilder out = new StringBuilder(header);

        if (XM > 0) {
            out.append(XM).append(" XM\n");
        }

        if (extras != null && !extras.isEmpty()) {
            for (String item : extras) {
                out.append(item).append("\n");
            }
        }

        if (items != null && !items.isEmpty()) {

            HashMap<String, Integer> tmp = new HashMap<>();
            if (!items.isEmpty()) {
                for (ItemBase item : items) {
                    String name = item.getUsefulName();
                    putItemInMap(tmp, name);
                }
            }

            for (Map.Entry<String, Integer> item : tmp.entrySet()) {
                out.append(item.getKey());
                if (item.getValue() > 1) {
                    out.append(" (").append(item.getValue()).append(")");
                }
                out.append("\n");
            }
        }
        return out.toString();
    }
}

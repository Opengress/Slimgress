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

package net.opengress.slimgress.activity;

import static net.opengress.slimgress.ViewHelpers.getColorFromResources;
import static net.opengress.slimgress.ViewHelpers.getImageForUltrastrikeLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForXMPLevel;
import static net.opengress.slimgress.ViewHelpers.getLevelColor;
import static net.opengress.slimgress.ViewHelpers.getPrettyItemName;
import static net.opengress.slimgress.ViewHelpers.putItemInMap;
import static net.opengress.slimgress.ViewHelpers.saveScreenshot;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;
import static net.opengress.slimgress.api.Common.Utils.notBouncing;
import static net.opengress.slimgress.api.Plext.PlextBase.PlextType.PlayerGenerated;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.UncontainedCarouselStrategy;

import net.opengress.slimgress.FireCarouselAdapter;
import net.opengress.slimgress.InventoryListItem;
import net.opengress.slimgress.R;
import net.opengress.slimgress.ScannerView;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.WidgetCommsLine;
import net.opengress.slimgress.api.Common.Team;
import net.opengress.slimgress.api.Game.GameState;
import net.opengress.slimgress.api.Game.Inventory;
import net.opengress.slimgress.api.Interface.Damage;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemWeapon;
import net.opengress.slimgress.api.Player.Agent;
import net.opengress.slimgress.api.Plext.PlextBase;
import net.opengress.slimgress.dialog.DialogComms;
import net.opengress.slimgress.dialog.DialogHackResult;
import net.opengress.slimgress.dialog.DialogInfo;
import net.opengress.slimgress.dialog.DialogLevelUp;

import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ActivityMain extends FragmentActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private final SlimgressApplication mApp = SlimgressApplication.getInstance();
    private final GameState mGame = mApp.getGame();
    private static boolean isInForeground = false;
    private boolean isLevellingUp = false;
    private RecyclerView mRecyclerView;
    private InventoryListItem mCurrentFireItem;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mApp.setMainActivity(null);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // update agent data
        updateAgent();
        mApp.getPlayerDataViewModel().getAgent().observe(this, this::updateAgent);

        mRecyclerView = findViewById(R.id.fire_carousel_recycler_view);
        CarouselLayoutManager manager = new CarouselLayoutManager(new UncontainedCarouselStrategy());
        manager.setCarouselAlignment(CarouselLayoutManager.ALIGNMENT_CENTER);
        mRecyclerView.setLayoutManager(manager);
        new CarouselSnapHelper().attachToRecyclerView(mRecyclerView);

        // create ops button callback
        final Button buttonOps = findViewById(R.id.buttonOps);
        buttonOps.setOnClickListener(v -> {
            // Perform action on click
            Intent myIntent = new Intent(getApplicationContext(), ActivityOps.class);
            startActivity(myIntent);
        });

        // create comm button callback
        final Button buttonComm = findViewById(R.id.buttonComm);
        buttonComm.setOnClickListener(v -> showComms());

        mApp.getAllCommsViewModel().getMessages().observe(this, this::getCommsMessages);
        mApp.getLevelUpViewModel().getLevelUpMsgId().observe(this, this::levelUp);
        mApp.setMainActivity(this);
    }

    private synchronized void levelUp(Integer level) {
        if (isLevellingUp || !notBouncing("levelUp", 10000)) {
            Log.d("Main", "Not levelling up, because we are ALREADY DOING THAT");
            return;
        }
        if (!isActivityInForeground()) {
            Log.d("Main", "Not levelling up, because we are not in the foreground");
            return;
        }
        if (mGame.getLocation() == null) {
            Log.d("Main", "Not levelling up, to be safe, because we have no location");
            return;
        }
        if (mGame.getAgent() == null) {
            Log.d("Main", "Not levelling up, because we can't remember who we are!");
            return;
        }
        isLevellingUp = true;
        try {
            Log.d("Main", "Levelling up! New level: " + level);
            mGame.intLevelUp(level, new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                    isLevellingUp = false;
                } else {
                    showLevelUpDialog(generateFieldKitMap(data));
                }
                return false;
            }));
        } catch (Exception e) {
            isLevellingUp = false;
            throw e;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
    }

    public static boolean isActivityInForeground() {
        return isInForeground;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private HashMap<String, Integer> generateFieldKitMap(@NonNull Bundle data) {
        HashMap<String, Integer> items = new HashMap<>();
        Serializable serializable = data.getSerializable("items");

        if (serializable instanceof HashMap) {
            HashMap<String, ItemBase> rawItems = (HashMap<String, ItemBase>) serializable;

            for (Map.Entry<String, ItemBase> entry : rawItems.entrySet()) {
                String name = getPrettyItemName(entry.getValue(), getResources());
                putItemInMap(items, name);
            }
        }

        return items;
    }

    private void getCommsMessages(List<PlextBase> plexts) {
        if (plexts.isEmpty()) {
            // can't do anything with this
            return;
        }
        PlextBase plext = plexts.get(plexts.size() - 1);
        WidgetCommsLine commsLine = findViewById(R.id.commsOneLiner);
        ((TextView) commsLine.findViewById(R.id.plext_text)).setText(plext.getFormattedText(false));
        ((TextView) commsLine.findViewById(R.id.plext_text)).setMaxLines(1);
        ((TextView) commsLine.findViewById(R.id.plext_text)).setSingleLine(true);
        ((TextView) commsLine.findViewById(R.id.plext_text)).setEllipsize(TextUtils.TruncateAt.END);

        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String formattedTime = sdf.format(new Date(Long.parseLong(plext.getEntityTimestamp())));
        ((TextView) commsLine.findViewById(R.id.plext_time)).setText(formattedTime);
        if (plext.atMentionsPlayer()) {
            ((TextView) commsLine.findViewById(R.id.plext_time)).setTextAppearance(this, R.style.PlextTimeMentionedTextView);
        } else {
            ((TextView) commsLine.findViewById(R.id.plext_time)).setTextAppearance(this, R.style.PlextTimeTextView);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        Objects.requireNonNull(scanner).requestLocationUpdates();
    }

    private void showComms() {
        DialogComms bottomSheet = new DialogComms();
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }

    @SuppressLint("ObsoleteSdkInt")
    private void updateAgent(Agent agent) {
//        Log.d("Main/updateAgent", "Updating agent in display!");
        // TODO move some of this style info into onCreate
        int textColor;
        Team team = agent.getTeam();
        textColor = 0xff000000 + team.getColour();
        int levelColor = getColorFromResources(getResources(), getLevelColor(agent.getLevel()));

        ((TextView) findViewById(R.id.agentname)).setText(agent.getNickname());
        ((TextView) findViewById(R.id.agentname)).setTextColor(textColor);

        String agentlevel = "L" + agent.getLevel();
        ((TextView) findViewById(R.id.agentLevel)).setText(agentlevel);
        ((TextView) findViewById(R.id.agentLevel)).setTextColor(levelColor);


        String nextLevel = String.valueOf(Math.min(agent.getLevel() + 1, 8));
        int thisLevelAP;
        try {
            thisLevelAP = mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(agent.getLevel()).getApRequired();
        } catch (Exception ignored) {
            /*
            there's a race condition which can cause a crash here,
            but if we won the race with the wrong code path we can just bail and it should be fine
            :-)
             */
            return;
        }
        int nextLevelAP = mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(nextLevel).getApRequired();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((ProgressBar) findViewById(R.id.agentap)).setMin(thisLevelAP);
        }
        ((ProgressBar) findViewById(R.id.agentap)).setMax(nextLevelAP);
        ((ProgressBar) findViewById(R.id.agentap)).setProgress(agent.getAp());
//        Log.d("Main/updateAgent", "New AP: " + agent.getAp());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((ProgressBar) findViewById(R.id.agentap)).getProgressDrawable().setTint(levelColor);
        } else {
            ((ProgressBar) findViewById(R.id.agentap)).getProgressDrawable().setColorFilter(levelColor, PorterDuff.Mode.SRC_IN);
        }

        ((ProgressBar) findViewById(R.id.agentxm)).setMax(agent.getEnergyMax());
        ((ProgressBar) findViewById(R.id.agentxm)).setProgress(agent.getEnergy());
//        Log.d("Main/updateAgent", "New XM: " + agent.getEnergy());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ((ProgressBar) findViewById(R.id.agentxm)).getProgressDrawable().setTint(textColor);
        } else {
            ((ProgressBar) findViewById(R.id.agentxm)).getProgressDrawable().setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        }
        findViewById(R.id.activity_main_header).setOnClickListener(view -> {
            String agentinfo = "AP: " + agent.getAp() + " / " + nextLevelAP + "\nXM: " + agent.getEnergy() + " / " + agent.getEnergyMax();
            Toast.makeText(getApplicationContext(), agentinfo, Toast.LENGTH_LONG).show();
        });

//            String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//            ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//            ((TextView)findViewById(R.id.agentinfo)).setTextColor(0x99999999);
    }

    public void updateAgent() {
        // get agent data
        Agent agent = mGame.getAgent();

        if (agent != null) {
            updateAgent(agent);
        }

    }


    @SuppressLint("DefaultLocale")
    public void showLevelUpDialog(HashMap<String, Integer> items) {
        DialogLevelUp dialog = new DialogLevelUp(this);
        int level = mGame.getAgent().getVerifiedLevel();
        dialog.setMessage(String.format("LEVEL %d", level), getColorFromResources(getResources(), getLevelColor(level)));
        dialog.setCancelable(true); // Allow dialog to be dismissed by tapping outside

        dialog.setOnDismissListener(dialog1 -> {
            showNextDialog(items);
            isLevellingUp = false;
        });

        // Add a share button
        ImageButton shareButton = dialog.findViewById(R.id.share_button);
        shareButton.setOnClickListener(v -> screenshotDialog(dialog));

        dialog.show();
    }

    @SuppressLint("DefaultLocale")
    private void screenshotDialog(Dialog dialog) {
        // Capture the main activity's view
        View mainView = getWindow().getDecorView().findViewById(android.R.id.content);
        mainView.setDrawingCacheEnabled(true);
        Bitmap mainBitmap = Bitmap.createBitmap(mainView.getDrawingCache());
        mainView.setDrawingCacheEnabled(false);

        // Capture the dialog's view
        View dialogView = Objects.requireNonNull(dialog.getWindow()).getDecorView();
        dialogView.setDrawingCacheEnabled(true);
        Bitmap dialogBitmap = Bitmap.createBitmap(dialogView.getDrawingCache());
        dialogView.setDrawingCacheEnabled(false);

        // Combine both bitmaps
        Bitmap combinedBitmap = Bitmap.createBitmap(mainBitmap.getWidth(), mainBitmap.getHeight(), Objects.requireNonNull(mainBitmap.getConfig()));
        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawBitmap(mainBitmap, new Matrix(), null);
        int[] dialogLocation = new int[2];
        dialogView.getLocationOnScreen(dialogLocation);
        canvas.drawBitmap(dialogBitmap, dialogLocation[0], dialogLocation[1], null);

        File screenshotFile = saveScreenshot(getExternalCacheDir(), combinedBitmap);

        // Share the screenshot
        Uri screenshotUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", screenshotFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, screenshotUri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, String.format("I've reached level %d in #opengress!", mGame.getAgent().getVerifiedLevel()));
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    @SuppressLint("DefaultLocale")
    public void showNextDialog(HashMap<String, Integer> items) {
        DialogHackResult newDialog1 = new DialogHackResult(this);
        newDialog1.setTitle(String.format("Receiving Level %d field kit...", mGame.getAgent().getVerifiedLevel()));
        newDialog1.setItems(items);
        newDialog1.show();
    }

    public List<InventoryListItem> getWeaponsList() {
        Inventory inv = mGame.getInventory();

        List<InventoryListItem> weaponList = new LinkedList<>();

        // get xmp weapon items
        for (int level = 1; level <= 8; level++) {
            List<ItemBase> items = inv.getItems(ItemBase.ItemType.WeaponXMP, level);

            String descr = "L" + level + " XMP";
            if (!items.isEmpty()) {
                ArrayList<String> weapons = new ArrayList<>();
                for (ItemBase item : items) {
                    weapons.add(item.getEntityGuid());
                }
                int drawable = getImageForXMPLevel(level);
                InventoryListItem weapon = new InventoryListItem(descr, ItemBase.ItemType.WeaponXMP, AppCompatResources.getDrawable(this, drawable), drawable, weapons, items.get(0).getItemRarity(), level);
                weaponList.add(weapon);
            }
        }

        // get ultrastrike weapon items
        for (int level = 1; level <= 8; level++) {
            List<ItemBase> items = inv.getItems(ItemBase.ItemType.WeaponUltraStrike, level);

            String descr = "L" + level + " UltraStrike";
            if (!items.isEmpty()) {
                ArrayList<String> weapons = new ArrayList<>();
                for (ItemBase item : items) {
                    weapons.add(item.getEntityGuid());
                }
                int drawable = getImageForUltrastrikeLevel(level);
                InventoryListItem weapon = new InventoryListItem(descr, ItemBase.ItemType.WeaponUltraStrike, AppCompatResources.getDrawable(this, drawable), drawable, weapons, items.get(0).getItemRarity(), level);
                weaponList.add(weapon);
            }
        }

        return weaponList;
    }

    @SuppressLint("RestrictedApi")
    public void showFireMenu(GeoPoint p) {


        List<InventoryListItem> arrayList = getWeaponsList();

        FireCarouselAdapter adapter = new FireCarouselAdapter(ActivityMain.this, arrayList);
        mRecyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener((imageView, item) -> {
            // FIXME set this to USE for other types of items
            mCurrentFireItem = item;
            findViewById(R.id.fire_carousel_button_fire).setEnabled(true);
        });


        var anchor = findViewById(R.id.buttonComm);
        PopupMenu popup = new PopupMenu(ActivityMain.this, anchor);
        popup.getMenuInflater().inflate(R.menu.map, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_fire_xmp) {
                    findViewById(R.id.fire_carousel_layout).setVisibility(View.VISIBLE);
                    findViewById(R.id.fire_carousel_button_fire).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
//                            flashMap();
                            // FIXME handle the case of cubes/flipcards/whatever
                            fireBurster(mCurrentFireItem.getLevel(), mCurrentFireItem.getType());
//                            Log.d("MAIN", "Firing "+mCurrentFireItem.getPrettyDescription());
                            mCurrentFireItem.remove(mCurrentFireItem.getFirstID());
                            int index = arrayList.indexOf(mCurrentFireItem);
                            int newIndex = 0;
                            if (mCurrentFireItem.getQuantity() == 0) {
                                newIndex = (index < 1) ? 0 : index - 1;
//                                mRecyclerView.scrollToPosition(newIndex);
                                arrayList.remove(mCurrentFireItem);
                                adapter.notifyItemRemoved(index);
                            } else {
                                adapter.notifyItemChanged(index);
                            }
                            if (arrayList.isEmpty()) {
                                findViewById(R.id.fire_carousel_button_fire).setEnabled(false);
                                return;
                            }
                            if (mCurrentFireItem.getQuantity() == 0) {
                                mCurrentFireItem = arrayList.get(newIndex);
                                adapter.setSelectedPosition(newIndex);
                                mRecyclerView.scrollToPosition(newIndex);
//                                Objects.requireNonNull(mRecyclerView.findViewHolderForAdapterPosition(newIndex)).itemView.setBackgroundResource(R.drawable.fire_carousel_selected_item_background);

                                adapter.notifyItemChanged(newIndex);
                            }
                        }
                    });
                    findViewById(R.id.fire_carousel_button_done).setOnClickListener(v -> {
                        findViewById(R.id.fire_carousel_layout).setVisibility(View.GONE);
                        findViewById(R.id.fire_carousel_button_fire).setEnabled(false);
                    });
                    return true;
                } else if (itemId == R.id.action_new_portal) {
                    String url = "https://opengress.net/new/?remote";
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                    return true;
                } else if (itemId == R.id.action_navigate) {
                    String uri = "geo:?q=" + p.getLatitude() + "," + p.getLongitude();
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                    return true;
                } else if (itemId == R.id.action_opr) {
                    String url = "https://opengress.net/opr/go";
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                    return true;
                } else {
                    return false;
                }
            }
        });
        popup.show();
    }

    private void flashMap() {
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        // Invert colors
        assert scanner != null;
        scanner.getMap().getOverlayManager().getTilesOverlay().setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0
        })));
        scanner.getMap().invalidate();

        // Revert colors after a short delay
        new Handler().postDelayed(() -> {
            scanner.getMap().getOverlayManager().getTilesOverlay().setColorFilter(null);
            scanner.getMap().invalidate();
        }, 30);
    }

    private void fireBurster(int level, ItemBase.ItemType type) {
        ItemBase item = Objects.requireNonNull(mGame.getInventory().findItem(mCurrentFireItem.getFirstID()));
        ItemBase.ItemType itemType = item.getItemType();
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        assert scanner != null;
        // todo: rate limiting etc per knobs

        if (item.getItemType() == ItemBase.ItemType.WeaponXMP || item.getItemType() == ItemBase.ItemType.WeaponUltraStrike) {

            if (mGame.getKnobs().getXMCostKnobs().getXmpFiringCostByLevel().get(item.getItemLevel() - 1) > mGame.getAgent().getEnergy()) {
                DialogInfo dialog = new DialogInfo(this);
                dialog.setMessage(getString(R.string.you_don_t_have_enough_xm)).setDismissDelay(1500).show();
                return;
            }

            mGame.intFireWeapon((ItemWeapon) item, new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                }
                ArrayList<Damage> damages = data.getParcelableArrayList("damages");
                if (damages == null || damages.isEmpty()) {
                    mApp.getAllCommsViewModel().addMessage(PlextBase.createByPlainText(PlayerGenerated, getString(R.string.missed_all_resonators)));
                    return true;
                }
                for (var damage : damages) {
                    scanner.displayDamage(damage.getDamageAmount(), damage.getTargetGuid(), damage.getTargetSlot(), damage.isCriticalHit());
                }

                // when i put the drawing code (currently below) in here, it doesn't draw.

                return true;
            }));

            // FIXME this should actually only be displayed if the weapon truly fires??
            int range = switch (type) {
                case WeaponXMP -> mGame.getKnobs().getWeaponRangeKnobs().getXmpDamageRange(level);
                case WeaponUltraStrike ->
                        mGame.getKnobs().getWeaponRangeKnobs().getUltraStrikeDamageRange(level);
                default -> 40;
            };
            scanner.fireBurster(range);
        }
    }

    public void damagePlayer(int amount, String attackerGuid) {
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        assert scanner != null;
        scanner.displayPlayerDamage(amount, attackerGuid);
    }

    public void refreshDisplay() {
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        assert scanner != null;
        scanner.updateScreen(new Handler(Looper.getMainLooper()));
    }
}

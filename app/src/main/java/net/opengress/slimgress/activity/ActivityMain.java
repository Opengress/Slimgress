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

import static net.opengress.slimgress.ViewHelpers.getColourFromResources;
import static net.opengress.slimgress.ViewHelpers.getImageForUltrastrikeLevel;
import static net.opengress.slimgress.ViewHelpers.getImageForXMPLevel;
import static net.opengress.slimgress.ViewHelpers.getLevelColour;
import static net.opengress.slimgress.ViewHelpers.getPrettyItemName;
import static net.opengress.slimgress.ViewHelpers.putItemInMap;
import static net.opengress.slimgress.ViewHelpers.saveScreenshot;
import static net.opengress.slimgress.api.Common.Utils.getErrorStringFromAPI;
import static net.opengress.slimgress.api.Common.Utils.notBouncing;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.carousel.CarouselLayoutManager;
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
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Interface.Damage;
import net.opengress.slimgress.api.Item.ItemBase;
import net.opengress.slimgress.api.Item.ItemFlipCard;
import net.opengress.slimgress.api.Item.ItemPowerCube;
import net.opengress.slimgress.api.Item.ItemWeapon;
import net.opengress.slimgress.api.Player.Agent;
import net.opengress.slimgress.api.Plext.PlextBase;
import net.opengress.slimgress.dialog.DialogComms;
import net.opengress.slimgress.dialog.DialogHackResult;
import net.opengress.slimgress.dialog.DialogInfo;
import net.opengress.slimgress.dialog.DialogLevelUp;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.PropertyFactory;

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
    private String mSelectedPortalGuid;
    private String mSelectedFlipCardGuid;
    private TextView mSelectTargetText;
    private Button mConfirmButton;
    private Button mCancelButton;
    private ActivityResultLauncher<Intent> mOpsActivityResultLauncher;
    private ScannerView mScannerView;
    private boolean isFireCarouselVisible = false;
    private boolean isPortalPickerVisible = false;

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
        mApp.setMainActivity(this);

        setUpFireCarousel();

        // create ops button callback
        mOpsActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onOpsResult
        );
        final Button buttonOps = findViewById(R.id.buttonOps);
        buttonOps.setOnClickListener(v -> {
            // Perform action on click
            Intent myIntent = new Intent(getApplicationContext(), ActivityOps.class);
            mOpsActivityResultLauncher.launch(myIntent);
        });

        // create comm button callback
        final Button buttonComm = findViewById(R.id.buttonComm);
        buttonComm.setOnClickListener(v -> showComms());

        mApp.getAllCommsViewModel().getMessages().observe(this, this::getCommsMessages);
        mApp.getLevelUpViewModel().getLevelUpMsgId().observe(this, this::levelUp);

        var listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Agent agent = mGame.getAgent();
                String nextLevel = String.valueOf(Math.min(agent.getLevel() + 1, 8));
                int nextLevelAP = mGame.getKnobs().getPlayerLevelKnobs().getLevelUpRequirement(nextLevel).getApRequired();
                String agentinfo = "AP: " + agent.getAp() + " / " + nextLevelAP + "\nXM: " + agent.getEnergy() + " / " + agent.getEnergyMax();
                Toast.makeText(ActivityMain.this.getApplicationContext(), agentinfo, Toast.LENGTH_LONG).show();
            }
        };
        findViewById(R.id.agentap).setOnClickListener(listener);
        findViewById(R.id.agentxm).setOnClickListener(listener);
        findViewById(R.id.agentxm).setOnClickListener(listener);
        findViewById(R.id.imageView).setOnClickListener(listener);
        findViewById(R.id.agentLevel).setOnClickListener(listener);
        findViewById(R.id.agentname).setOnClickListener(listener);

        // update agent data last because maybe race condition?
        updateAgent(mGame.getAgent());
        mApp.getPlayerDataViewModel().getAgent().observe(this, this::updateAgent);

    }

    @SuppressLint("RestrictedApi")
    private void setUpFireCarousel() {
        mRecyclerView = findViewById(R.id.fire_carousel_recycler_view);
        CarouselLayoutManager manager = new CarouselLayoutManager(new UncontainedCarouselStrategy());
        manager.setCarouselAlignment(CarouselLayoutManager.ALIGNMENT_CENTER);
        mRecyclerView.setLayoutManager(manager);
        new LinearSnapHelper().attachToRecyclerView(mRecyclerView);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setNestedScrollingEnabled(false);

        mSelectTargetText = findViewById(R.id.select_target_text);
        mConfirmButton = findViewById(R.id.btn_flipcard_confirm);
        mCancelButton = findViewById(R.id.btn_flipcard_cancel);

        mConfirmButton.setOnClickListener(v -> {
            if (mSelectedPortalGuid != null && mSelectedFlipCardGuid != null) {
                flipPortal(mSelectedPortalGuid, mSelectedFlipCardGuid);
            }
            resetSelection();
        });

        mCancelButton.setOnClickListener(v -> resetSelection());

        // Disable change animations
        RecyclerView.ItemAnimator animator = mRecyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFireCarouselVisible) {
                    hideFireCarousel();
                } else if (isPortalPickerVisible) {
                    resetSelection();
                } else {
                    // Allow default system behavior (finish the activity)
                    setEnabled(false); // Disable this callback to let the system handle it
                    getOnBackPressedDispatcher().onBackPressed(); // Call the dispatcher manually
                }
            }
        });
    }

    private void onOpsResult(ActivityResult activityResult) {
        // this is nice to have, but probably unneeded so i could turn it off to speed the game up
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        Objects.requireNonNull(scanner).updateWorld();
    }

    private synchronized void levelUp(int level) {
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
                    SlimgressApplication.postPlainCommsMessage("Level up! You are now level " + level);
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
        if (mGame.getKnobs() == null) {
            Intent intent = new Intent(this, ActivitySplash.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);
            finish();
        }
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

    private void getCommsMessages(@NonNull List<PlextBase> plexts) {
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
//        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
//        Objects.requireNonNull(scanner).requestLocationUpdates();
    }

    private void showComms() {
        DialogComms bottomSheet = new DialogComms();
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }

    @SuppressLint("ObsoleteSdkInt")
    private void updateAgent(Agent agent) {
        runOnUiThread(() -> {
            int textColor;
            Team team = agent.getTeam();
            textColor = 0xff000000 + team.getColour();
            int levelColor = getColourFromResources(getResources(), getLevelColour(agent.getLevel()));

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

//            String agentinfo = "AP: " + agent.getAp() + " / XM: " + (agent.getEnergy() * 100 / agent.getEnergyMax()) + " %";
//            ((TextView)findViewById(R.id.agentinfo)).setText(agentinfo);
//            ((TextView)findViewById(R.id.agentinfo)).setTextColor(0x99999999);
        });
    }


    @SuppressLint("DefaultLocale")
    public void showLevelUpDialog(HashMap<String, Integer> items) {
        hideFireCarousel();
        resetSelection();
        DialogLevelUp dialog = new DialogLevelUp(this);
        int level = mGame.getAgent().getVerifiedLevel();
        dialog.setMessage(String.format("LEVEL %d", level), getColourFromResources(getResources(), getLevelColour(level)));
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
    private void screenshotDialog(@NonNull Dialog dialog) {
        MapView mapView = mScannerView.getMap();
        mapView.getMapAsync(map -> {
            map.snapshot(snapshotBitmap -> {
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

                // 2. Combine the map snapshot with the UI
                Bitmap combinedBitmap = Bitmap.createBitmap(mainBitmap.getWidth(), mainBitmap.getHeight(), Objects.requireNonNull(mainBitmap.getConfig()));
                Canvas canvas = new Canvas(combinedBitmap);

                int[] mapViewScreenLoc = new int[2];
                mapView.getLocationOnScreen(mapViewScreenLoc);
                canvas.drawBitmap(snapshotBitmap, mapViewScreenLoc[0], mapViewScreenLoc[1], null);

                canvas.drawBitmap(mainBitmap, new Matrix(), null);
                int[] dialogLocation = new int[2];
                dialogView.getLocationOnScreen(dialogLocation);
                canvas.drawBitmap(dialogBitmap, dialogLocation[0], dialogLocation[1], null);

                // 3. Save or share combinedBitmap
                File screenshotFile = saveScreenshot(getExternalCacheDir(), combinedBitmap);
                // Share the screenshot
                Uri screenshotUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", screenshotFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, screenshotUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, String.format("I've reached level %d in #Opengress!", mGame.getAgent().getVerifiedLevel()));
                startActivity(Intent.createChooser(shareIntent, "Share via"));
            });
        });
    }

    @SuppressLint("DefaultLocale")
    public void showNextDialog(HashMap<String, Integer> items) {
        DialogHackResult newDialog1 = new DialogHackResult(this);
        newDialog1.setTitle(String.format("Receiving Level %d field kit...", mGame.getAgent().getVerifiedLevel()));
        newDialog1.setItems(items);
        newDialog1.setDismissDelay(0);
        newDialog1.show();
    }

    public List<InventoryListItem> getWeaponsList() {
        Inventory inv = mGame.getInventory();

        List<InventoryListItem> weaponList = new LinkedList<>();

        // get xmp power cube items
        for (int level = 1; level <= mGame.getAgent().getLevel(); level++) {
            List<ItemBase> items = inv.getItems(ItemBase.ItemType.PowerCube, level);

            String descr = "L" + level + " XMP";
            if (!items.isEmpty()) {
                ArrayList<String> weapons = new ArrayList<>();
                for (ItemBase item : items) {
                    weapons.add(item.getEntityGuid());
                }
                int drawable = getImageForXMPLevel(level);
                InventoryListItem weapon = new InventoryListItem(descr, ItemBase.ItemType.PowerCube, AppCompatResources.getDrawable(this, drawable), drawable, weapons, items.get(0).getItemRarity(), level);
                weaponList.add(weapon);
            }
        }

        // get xmp weapon items
        for (int level = 1; level <= mGame.getAgent().getLevel(); level++) {
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
        for (int level = 1; level <= mGame.getAgent().getLevel(); level++) {
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

        // get flipcard items
        List<ItemBase> adas = inv.getFlipCards(ItemFlipCard.FlipCardType.Ada);
        List<ItemBase> jarvises = inv.getFlipCards(ItemFlipCard.FlipCardType.Jarvis);

        String descr = "ADA Refactor";
        if (!adas.isEmpty()) {
            ArrayList<String> weapons = new ArrayList<>();
            for (var item : adas) {
                weapons.add(item.getEntityGuid());
            }
            InventoryListItem weapon = new InventoryListItem(descr, ItemBase.ItemType.FlipCard, AppCompatResources.getDrawable(this, R.drawable.ada), R.drawable.ada, weapons, adas.get(0).getItemRarity());
            weapon.setFlipCardType(ItemFlipCard.FlipCardType.Ada);
            weaponList.add(weapon);
        }

        descr = "Jarvis Virus";
        if (!jarvises.isEmpty()) {
            ArrayList<String> weapons = new ArrayList<>();
            for (var item : jarvises) {
                weapons.add(item.getEntityGuid());
            }
            InventoryListItem weapon = new InventoryListItem(descr, ItemBase.ItemType.FlipCard, AppCompatResources.getDrawable(this, R.drawable.jarvis), R.drawable.jarvis, weapons, jarvises.get(0).getItemRarity());
            weapon.setFlipCardType(ItemFlipCard.FlipCardType.Jarvis);
            weaponList.add(weapon);
        }

        return weaponList;
    }

    @SuppressLint("RestrictedApi")
    public void showFireMenu(LatLng p) {

        var anchor = findViewById(R.id.buttonComm);
        PopupMenu popup = new PopupMenu(ActivityMain.this, anchor);
        popup.getMenuInflater().inflate(R.menu.map, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_fire_xmp) {
                return showFireCarousel(null);
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
        });
        popup.show();
    }

    boolean showFireCarousel(InventoryListItem selectedItem) {
        List<InventoryListItem> arrayList = getWeaponsList();

        FireCarouselAdapter adapter = new FireCarouselAdapter(ActivityMain.this, arrayList);
        mRecyclerView.setAdapter(adapter);

        // Logic to select the relevant item in the carousel
        int selectedIndex = -1;

        // 0. If we already selected something, grab that
        if (mCurrentFireItem != null) {
            selectedIndex = findItemByDescription(arrayList, mCurrentFireItem.getPrettyDescription());
        }

        // 1. If there's a selectedItem, find it in the list
        if (mCurrentFireItem == null && selectedItem != null) {
            selectedIndex = findItemByDescription(arrayList, selectedItem.getPrettyDescription());
        }

        // 2. If no selectedItem or not found, prioritize finding XMP, then UltraStrike, then PowerCube, or default to first item
        if (mCurrentFireItem == null && selectedIndex == -1) {
            int selectedXMPIndex = -1;
            int selectedUltraStrikeIndex = -1;
            int selectedPowerCubeIndex = -1;

            for (int i = 0; i < arrayList.size(); i++) {
                InventoryListItem item = arrayList.get(i);

                // Track highest level XMP
                if (item.getType() == ItemBase.ItemType.WeaponXMP) {
                    if (selectedXMPIndex == -1 || item.getLevel() > arrayList.get(selectedXMPIndex).getLevel()) {
                        selectedXMPIndex = i;
                    }
                }

                // Track highest level UltraStrike
                else if (item.getType() == ItemBase.ItemType.WeaponUltraStrike) {
                    if (selectedUltraStrikeIndex == -1 || item.getLevel() > arrayList.get(selectedUltraStrikeIndex).getLevel()) {
                        selectedUltraStrikeIndex = i;
                    }
                }

                // Track highest level PowerCube
                else if (item.getType() == ItemBase.ItemType.PowerCube) {
                    if (selectedPowerCubeIndex == -1 || item.getLevel() > arrayList.get(selectedPowerCubeIndex).getLevel()) {
                        selectedPowerCubeIndex = i;
                    }
                }
            }

            // Set the selected index based on priority: XMP > UltraStrike > PowerCube
            if (selectedXMPIndex != -1) {
                selectedIndex = selectedXMPIndex;
            } else if (selectedUltraStrikeIndex != -1) {
                selectedIndex = selectedUltraStrikeIndex;
            } else if (selectedPowerCubeIndex != -1) {
                selectedIndex = selectedPowerCubeIndex;
            }

            // If no special items found, fall back to the first item in the list
            if (selectedIndex == -1 && !arrayList.isEmpty()) {
                selectedIndex = 0;
            }
        }

        // Set the adapter and scroll to the selected index
        if (selectedIndex != -1) {
            mCurrentFireItem = arrayList.get(selectedIndex);
            adapter.setSelectedPosition(selectedIndex);
            mRecyclerView.scrollToPosition(selectedIndex);
            findViewById(R.id.fire_carousel_button_fire).setEnabled(true);
            if (mCurrentFireItem.getType() == ItemBase.ItemType.WeaponXMP || mCurrentFireItem.getType() == ItemBase.ItemType.WeaponUltraStrike) {
                ((Button) findViewById(R.id.fire_carousel_button_fire)).setText(R.string.fire_button_text);
            } else {
                ((Button) findViewById(R.id.fire_carousel_button_fire)).setText(R.string.fire_button_text_use);
            }
        }

        // Adapter item click listener
        adapter.setOnItemClickListener((imageView, item) -> {
            mCurrentFireItem = item;
            findViewById(R.id.fire_carousel_button_fire).setEnabled(true);
            if (item.getType() == ItemBase.ItemType.WeaponXMP || item.getType() == ItemBase.ItemType.WeaponUltraStrike) {
                ((Button) findViewById(R.id.fire_carousel_button_fire)).setText(R.string.fire_button_text);
            } else {
                ((Button) findViewById(R.id.fire_carousel_button_fire)).setText(R.string.fire_button_text_use);
            }
        });

        // Show the carousel layout
        findViewById(R.id.fire_carousel_layout).setVisibility(View.VISIBLE);
        isFireCarouselVisible = true;

        // Fire button click logic
        findViewById(R.id.fire_carousel_button_fire).setOnClickListener(v -> {
            if (fireBurster(mCurrentFireItem.getLevel(), mCurrentFireItem.getType())) {
                mCurrentFireItem.remove(mCurrentFireItem.getFirstID());
            }
            int index = arrayList.indexOf(mCurrentFireItem);
            int newIndex;

            // if current stack is depleted
            if (mCurrentFireItem.getQuantity() == 0) {
                adapter.removeItem(index);

                // Check for a higher level stack of the same type
                newIndex = findNextHigherLevelItem(arrayList, mCurrentFireItem);

                // If no higher level stack of the same type, fallback to the previous behavior
                if (newIndex == -1) {
                    newIndex = (index < 1) ? 0 : index - 1;
                }

                if (!arrayList.isEmpty()) {
                    mCurrentFireItem = arrayList.get(newIndex);
                    adapter.setSelectedPosition(newIndex);
                    mRecyclerView.scrollToPosition(newIndex);
                } else {
                    findViewById(R.id.fire_carousel_button_fire).setEnabled(false);
                }
            } else {
                adapter.notifyItemChanged(index);
            }
        });

        // Done button logic
        findViewById(R.id.fire_carousel_button_done).setOnClickListener(v -> hideFireCarousel());

        return true;
    }

    private int findItemByDescription(@NonNull List<InventoryListItem> arrayList, String description) {
        for (int i = 0; i < arrayList.size(); i++) {
            if (Objects.equals(arrayList.get(i).getPrettyDescription(), description)) {
                return i;
            }
        }
        return -1;
    }

    private int findNextHigherLevelItem(@NonNull List<InventoryListItem> arrayList, InventoryListItem currentItem) {
        for (int i = 0; i < arrayList.size(); i++) {
            InventoryListItem item = arrayList.get(i);
            if (item.getType() == currentItem.getType() && item.getLevel() > currentItem.getLevel()) {
                return i;
            }
        }
        return -1;
    }

    private void flashMap() {
        MapView mapView = mScannerView.getMap();
        mapView.getMapAsync(maplibreMap -> maplibreMap.getStyle(style -> {
            FillLayer layer = style.getLayerAs("flash-overlay-layer");
            assert layer != null;
            layer.setProperties(PropertyFactory.fillColor("rgba(255, 255, 255, 1.0)"));
            mapView.postDelayed(() -> layer.setProperties(PropertyFactory.fillColor("rgba(255, 255, 255, 0.0)")), 444);
        }));
    }

    private boolean fireBurster(int level, ItemBase.ItemType type) {
        ItemBase item = mGame.getInventory().findItem(mCurrentFireItem.getFirstID());
        // FIXME i don't think these null checks are quite right.
        if (item == null) {
            return false;
        }
        ItemBase.ItemType itemType = item.getItemType();
        if (itemType == null) {
            return false;
        }
        ScannerView scanner = (ScannerView) getSupportFragmentManager().findFragmentById(R.id.map);
        assert scanner != null;
        // todo: rate limiting etc per knobs
        if (itemType == ItemBase.ItemType.WeaponXMP || itemType == ItemBase.ItemType.WeaponUltraStrike) {

            int cost = mGame.getKnobs().getXMCostKnobs().getXmpFiringCostByLevel().get(item.getItemLevel() - 1);
            if (cost > mGame.getAgent().getEnergy()) {
                DialogInfo dialog = new DialogInfo(this);
                dialog.setMessage(getString(R.string.you_don_t_have_enough_xm)).setDismissDelay(1500).show();
                return false;
            }

            mGame.intFireWeapon((ItemWeapon) item, new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                    SlimgressApplication.postPlainCommsMessage("XMP failed: " + error);
                    return true;
                }

                ArrayList<Damage> damages = data.getParcelableArrayList("damages");
                if (damages == null || damages.isEmpty()) {
                    SlimgressApplication.postPlainCommsMessage(getString(R.string.missed_all_resonators));
                    return true;
                }

                int destroyedResos = 0;
                for (var damage : damages) {
                    scanner.displayDamage(damage.getDamageAmount(), damage.getTargetGuid(), damage.getTargetSlot(), damage.isCriticalHit());
                    if (damage.isTargetDestroyed()) {
                        ++destroyedResos;
                    }
                }

                if (destroyedResos == 1) {
                    SlimgressApplication.postPlainCommsMessage("Destroyed 1 resonator");
                } else if (destroyedResos > 1) {
                    SlimgressApplication.postPlainCommsMessage(String.format(Locale.getDefault(), "Destroyed %d resonators", destroyedResos));
                }

                // when i put the drawing code (currently below) in here, it doesn't draw.
                mGame.getAgent().subtractEnergy(cost);
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
        } else if (itemType == ItemBase.ItemType.PowerCube) {
            mGame.intUsePowerCube((ItemPowerCube) item, new Handler(msg -> {
                var data = msg.getData();
                String error = getErrorStringFromAPI(data);
                if (error != null && !error.isEmpty()) {
                    DialogInfo dialog = new DialogInfo(this);
                    dialog.setMessage(error).setDismissDelay(1500).show();
                } else {
                    var res = data.getString("result");
                    String message = "Gained %s XM from using a %s";
                    message = String.format(message, res, item.getUsefulName());
                    SlimgressApplication.postPlainCommsMessage(message);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    for (var id : Objects.requireNonNull(data.getStringArray("consumed"))) {
                        mGame.getInventory().removeItem(id);
                    }
                }
                return false;
            }));
        } else if (itemType == ItemBase.ItemType.FlipCard) {
            mSelectedFlipCardGuid = item.getEntityGuid();
            mSelectTargetText.setVisibility(View.VISIBLE);
            mConfirmButton.setVisibility(View.VISIBLE);
            mCancelButton.setVisibility(View.VISIBLE);
            hideFireCarousel();
            isPortalPickerVisible = true;
            return false;
        } else {
            throw new RuntimeException("Unhandled weapon type fired from carousel");
        }
        return true;
    }

    public void damagePlayer(int amount, String attackerGuid) {
        mScannerView.displayPlayerDamage(amount, attackerGuid);
    }

    public void refreshDisplay() {
        mScannerView.updateScreen(new Handler(Looper.getMainLooper()));
    }

    private void resetSelection() {
        mSelectTargetText.setVisibility(View.GONE);
        mSelectTargetText.setText(R.string.flipcard_select_target);
        mConfirmButton.setVisibility(View.GONE);
        mConfirmButton.setEnabled(false);
        mCancelButton.setVisibility(View.GONE);
        mSelectedPortalGuid = null;
        mSelectedFlipCardGuid = null;
        mScannerView.removeInfoCard();
        isPortalPickerVisible = false;
    }

    private void flipPortal(String portalGuid, String mSelectedFlipCardGuid) {
        GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(portalGuid);
        ItemFlipCard flipCard = (ItemFlipCard) mGame.getInventory().getItems().get(mSelectedFlipCardGuid);
        assert portal != null;
        assert flipCard != null;
        mGame.intFlipPortal(portal, flipCard, new Handler(msg -> {
            var data = msg.getData();
            String error = getErrorStringFromAPI(data);
            if (error != null && !error.isEmpty()) {
                DialogInfo dialog = new DialogInfo(this);
                dialog.setMessage(error).setDismissDelay(1500).show();
            } else {
                flashMap();
                mScannerView.removeInfoCard();
            }
            return false;
        }));
    }

    public boolean isSelectingTargetPortal() {
        return mSelectTargetText.getVisibility() == View.VISIBLE;
    }

    public void setTargetPortal(String entityGuid) {
        GameEntityPortal portal = (GameEntityPortal) mGame.getWorld().getGameEntities().get(entityGuid);
        assert portal != null;
        int dist = (int) mGame.getLocation().getLatLng().distanceTo(portal.getPortalLocation().getLatLng());
        mSelectTargetText.setText(dist < 41 ? R.string.flipcard_confirm_selection : R.string.flipcard_select_target);
        mSelectedPortalGuid = entityGuid;
        mConfirmButton.setEnabled(dist < 41);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data.hasExtra("selected_weapon")) {
            InventoryListItem selectedItem = (InventoryListItem) data.getSerializableExtra("selected_weapon");
            showFireCarousel(selectedItem);
        }
    }

    public void processDeletedEntityGuids(List<String> deletedEntityGuids) {
        runOnUiThread(() -> mScannerView.onReceiveDeletedEntityGuids(deletedEntityGuids));
    }

    public void setScanner(ScannerView scanner) {
        mScannerView = scanner;
    }

    public void forceSync() {
        mScannerView.forceSync();
    }

    private void hideFireCarousel() {
        findViewById(R.id.fire_carousel_layout).setVisibility(View.GONE);
        findViewById(R.id.fire_carousel_button_fire).setEnabled(false);
        isFireCarouselVisible = false;
    }
}

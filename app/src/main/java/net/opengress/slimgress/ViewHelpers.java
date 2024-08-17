package net.opengress.slimgress;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.opengress.slimgress.API.Interface.APGain;
import net.opengress.slimgress.API.Item.ItemBase;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewHelpers {
    @Nullable
    static Bitmap getBitmapFromAsset(String name, @NonNull AssetManager assetManager) {

        InputStream istr;
        Bitmap bitmap;
        try {
            istr = assetManager.open(name);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            return null;
        }

        return bitmap;
    }

    @NonNull
    public static Bitmap getTintedImage(String image, int color, AssetManager assetManager) {
        Bitmap bitmap = getBitmapFromAsset(image, assetManager);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        assert bitmap != null;
        Bitmap bitmapResult = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapResult);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmapResult;
    }

    public static int getLevelColor(int level) {
        return switch (level) {
            case 2 -> R.color.level_two;
            case 3 -> R.color.level_three;
            case 4 -> R.color.level_four;
            case 5 -> R.color.level_five;
            case 6 -> R.color.level_six;
            case 7 -> R.color.level_seven;
            case 8 -> R.color.level_eight;
            default -> R.color.level_one;
        };
    }

    public static int getRarityColor(@NonNull ItemBase.Rarity rarity) {
        return switch (rarity) {
            case Common -> R.color.rarity_common;
            case LessCommon -> R.color.rarity_less_common;
            case Rare -> R.color.rarity_rare;
            case VeryRare -> R.color.rarity_very_rare;
            case ExtraRare -> R.color.rarity_extra_rare;
            default -> R.color.rarity_very_common;
        };
    }


    public static String getRarityText(ItemBase.Rarity rarity) {
        return switch (rarity) {
            case Common -> "Common";
            case LessCommon -> "Less Common";
            case Rare -> "Rare";
            case VeryRare -> "Very Rare";
            case ExtraRare -> "Extra Rare";
            default -> "Very Common";
        };
    }

    public static int getImageForResoLevel(int level) {
        int drawable = R.drawable.r1;
        switch (level) {
            case 2 -> drawable = R.drawable.r2;
            case 3 -> drawable = R.drawable.r3;
            case 4 -> drawable = R.drawable.r4;
            case 5 -> drawable = R.drawable.r5;
            case 6 -> drawable = R.drawable.r6;
            case 7 -> drawable = R.drawable.r7;
            case 8 -> drawable = R.drawable.r8;
        }
        return drawable;
    }

    public static int getImageForCubeLevel(int level) {
        int drawable = R.drawable.c1;
        switch (level) {
            case 2 -> drawable = R.drawable.c2;
            case 3 -> drawable = R.drawable.c3;
            case 4 -> drawable = R.drawable.c4;
            case 5 -> drawable = R.drawable.c5;
            case 6 -> drawable = R.drawable.c6;
            case 7 -> drawable = R.drawable.c7;
            case 8 -> drawable = R.drawable.c8;
        }
        return drawable;
    }

    public static int getImageForXMPLevel(int level) {
        int drawable = R.drawable.x1;
        switch (level) {
            case 2 -> drawable = R.drawable.x2;
            case 3 -> drawable = R.drawable.x3;
            case 4 -> drawable = R.drawable.x4;
            case 5 -> drawable = R.drawable.x5;
            case 6 -> drawable = R.drawable.x6;
            case 7 -> drawable = R.drawable.x7;
            case 8 -> drawable = R.drawable.x8;
        }
        return drawable;
    }

    public static int getImageForUltrastrikeLevel(int level) {
        int drawable = R.drawable.u1;
        switch (level) {
            case 2 -> drawable = R.drawable.u2;
            case 3 -> drawable = R.drawable.u3;
            case 4 -> drawable = R.drawable.u4;
            case 5 -> drawable = R.drawable.u5;
            case 6 -> drawable = R.drawable.u6;
            case 7 -> drawable = R.drawable.u7;
            case 8 -> drawable = R.drawable.u8;
        }
        return drawable;
    }

    /**
     * @noinspection deprecation
     */
    public static int getColorFromResources(Resources r, int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return r.getColor(id, null);
        } else {
            return r.getColor(id);
        }
    }

    @NonNull
    public static String getPrettyDistanceString(int dist) {
        // TODO: imperial units?
        double distKM = (dist < 1000000) ? (Math.ceil((double) dist / 100) / 10) : (Math.ceil((double) dist / 1000));
        DecimalFormat df = new DecimalFormat("#.#");
        String distKMPretty = df.format(distKM);
        return (dist < 1000 ? dist + "m" : distKMPretty + "km");
    }

    public static String getAPGainTriggerReason(APGain.Trigger trigger) {
        String what;
        switch (trigger) {
            case CapturedPortal -> what = "capturing a portal";
            case CreatedField -> what = "creating a control field";
            case CreatedLink -> what = "creating a link";
            case DeployedMod -> what = "deploying a portal mod";
            case DeployedResonator -> what = "deploying a resonator";
            case DestroyedField -> what = "destroying a control field";
            case DestroyedLink -> what = "destroying a link";
            case DestroyedResonator -> what = "destroying a resonator";
            case FullyDeployedPortal -> what = "fully deploying a portal";
            case HackingEnemyPortal -> what = "hacking an enemy portal";
            case InvitedPlayerJoined -> what = "a player you invited joining the game";
            case RechargeResonator -> what = "recharging a resonator";
            case RedeemedAP -> what = "redeeming a passcode";
            case RemoteRechargeResonator -> what = "recharging a remote resonator";
            case UpgradeResonator -> what = "upgrading someone else's resonator";
            default -> what = "doing something";
        }
        return what;
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    static String getPrettyItemName(@NonNull ItemBase item, Resources resources) {
        String html;
        // rarity will maybe eventually expressed by colour, not text. that's why html
        switch (item.getItemRarity()) {
            case VeryCommon, Common, LessCommon, Rare, VeryRare, ExtraRare -> {
                String hexColor = String.format("#%06X", (0xFFFFFF & resources.getColor(getRarityColor(item.getItemRarity()))));
                html = String.format("<span style='color: %s'>%s</span>", hexColor, item.getDisplayName());
            }
            default -> {
                if (item.getItemLevel() == 0) {
                    html = item.getDisplayName();
                } else {
                    String hexColor = String.format("#%06X", (0xFFFFFF & resources.getColor(getLevelColor(item.getItemLevel()))));
                    html = String.format("<span style='color: %s'>L%d</span> %s", hexColor, item.getItemLevel(), item.getDisplayName());
                }
            }
        }

        return html;
    }

    public static void putItemInMap(@NonNull HashMap<String, Integer> items, String name) {
        if (!items.containsKey(name)) {
            items.put(name, 1);
        } else {
            items.put(name, Objects.requireNonNull(items.get(name)) + 1);
        }
    }

    public static @NonNull File saveScreenshot(File cacheDir, Bitmap combinedBitmap) {
        // Save the screenshot to a file
        File screenshotFile = new File(cacheDir, "screenshot.png");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(screenshotFile))) {
                combinedBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return screenshotFile;
    }
}

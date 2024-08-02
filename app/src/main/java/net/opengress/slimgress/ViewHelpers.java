package net.opengress.slimgress;

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

import net.opengress.slimgress.API.Item.ItemBase;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;

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
        int levelColour = R.drawable.r1;
        switch (level) {
            case 2:
                levelColour = R.drawable.r2;
                break;
            case 3:
                levelColour = R.drawable.r3;
                break;
            case 4:
                levelColour = R.drawable.r4;
                break;
            case 5:
                levelColour = R.drawable.r5;
                break;
            case 6:
                levelColour = R.drawable.r6;
                break;
            case 7:
                levelColour = R.drawable.r7;
                break;
            case 8:
                levelColour = R.drawable.r8;
                break;
        }
        return levelColour;
    }

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
}

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
        int levelColour = R.color.level_one;
        switch (level) {
            case 2:
                levelColour = R.color.level_two;
                break;
            case 3:
                levelColour = R.color.level_three;
                break;
            case 4:
                levelColour = R.color.level_four;
                break;
            case 5:
                levelColour = R.color.level_five;
                break;
            case 6:
                levelColour = R.color.level_six;
                break;
            case 7:
                levelColour = R.color.level_seven;
                break;
            case 8:
                levelColour = R.color.level_eight;
                break;
        }
        return levelColour;
    }

    public static int getRarityColor(@NonNull ItemBase.Rarity rarity) {
        int rarityColor = R.color.rarity_very_common;
        switch (rarity) {
            case Common:
                rarityColor = R.color.rarity_common;
                break;
            case LessCommon:
                rarityColor = R.color.rarity_less_common;
                break;
            case Rare:
                rarityColor = R.color.rarity_rare;
                break;
            case VeryRare:
                rarityColor = R.color.rarity_very_rare;
                break;
            case ExtraRare:
                rarityColor = R.color.rarity_extra_rare;
                break;

        }
        return rarityColor;
    }


    public static String getRarityText(ItemBase.Rarity rarity) {
        String text = "Very Common";
        switch (rarity) {
            case Common:
                text = "Common";
                break;
            case LessCommon:
                text = "Less Common";
                break;
            case Rare:
                text = "Rare";
                break;
            case VeryRare:
                text = "Very Rare";
                break;
            case ExtraRare:
                text = "Extra Rare";
                break;

        }
        return text;
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

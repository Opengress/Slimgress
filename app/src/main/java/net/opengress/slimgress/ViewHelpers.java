package net.opengress.slimgress;

import static org.maplibre.android.utils.ColorUtils.colorToRgbaString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import net.opengress.slimgress.activity.ActivityMain;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Interface.APGain;
import net.opengress.slimgress.api.Item.ItemBase;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewHelpers {
    private static final HashMap<Integer, String> mColourStrings = new HashMap<>();

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

    public static int getLevelColour(int level) {
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

    public static int getRarityColour(@NonNull ItemBase.Rarity rarity) {
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

    // all these getImageForXYZ things could just go through one method to rule them all

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
     * Gets a bearing indicated in degrees from one of the 8 reso slot numbers.
     *
     * @param slot The slot number from 0 to 7 inclusive
     * @return The bearing in degrees
     */
    public static int getBearingFromSlot(int slot) {
        return switch (slot) {
            case 0 -> 90;
            case 1 -> 45;
            case 2 -> 0;
            case 3 -> 315;
            case 4 -> 270;
            case 5 -> 225;
            case 6 -> 180;
            case 7 -> 135;
            default -> throw new IllegalStateException("Unexpected value: " + slot);
        };
    }

    /**
     * Returns a color integer associated with a particular resource ID. If the
     * resource holds a complex ColorStateList, then the default color
     * from the set is returned.
     *
     * @param r Android application Resources object from getResources().
     *
     * @param id The desired resource identifier, as generated by the aapt
     *           tool. This integer encodes the package, type, and resource
     *           entry. The value 0 is an invalid identifier.
     *
     * @return A single color value in the form 0xAARRGGBB.
     */
    public static int getColourFromResources(Resources r, int id) {
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            result = r.getColor(id, null);
        } else {
            result = r.getColor(id);
        }
        return result;
    }

    public static String getRgbaStringFromColour(int colour) {
        if (mColourStrings.containsKey(colour)) {
            return mColourStrings.get(colour);
        }
        String rgbaString = colorToRgbaString(colour);
        mColourStrings.put(colour, rgbaString);
        return rgbaString;
    }

    @NonNull
    public static String getPrettyDistanceString(double dist) {
        // TODO: imperial units?
        double distKM = (dist < 1000000) ? (Math.ceil(dist / 100) / 10) : (Math.ceil(dist / 1000));
        DecimalFormat df = new DecimalFormat("#.#");
        String distKMPretty = df.format(distKM);
        return (dist < 1000 ? Math.round(dist) + "m" : distKMPretty + "km");
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
            default -> what = "doing something cool";
        }
        return what;
    }

    public static String getString(int resId) {
        return ContextCompat.getString(SlimgressApplication.getInstance(), resId);
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    public static String getPrettyItemName(@NonNull ItemBase item, Resources resources) {
        String html;
        // rarity will maybe eventually expressed by colour, not text. that's why html
        switch (item.getItemRarity()) {
            case VeryCommon, Common, LessCommon, Rare, VeryRare, ExtraRare -> {
                String hexColor = String.format("#%06X", (0xFFFFFF & resources.getColor(getRarityColour(item.getItemRarity()))));
                html = String.format("<span style='color: %s'>%s</span>", hexColor, item.getDisplayName());
            }
            default -> {
                if (item.getItemLevel() == 0) {
                    html = item.getDisplayName();
                } else {
                    String hexColor = String.format("#%06X", (0xFFFFFF & resources.getColor(getLevelColour(item.getItemLevel()))));
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

    public static ActivityMain getMainActivity() {
        return SlimgressApplication.getInstance().getMainActivity();
    }

    public static Spinner setUpSpinner(String[] what, View where, int resource) {
        Spinner sp = where.findViewById(resource);
        ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(where.getContext(),
                android.R.layout.simple_spinner_item, what);
        levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(levelAdapter);
        return sp;
    }

    public static Bitmap getBitmapFromDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        assert drawable != null;
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static String formatNumberToKLocalized(long number) {
        NumberFormat formatter = NumberFormat.getInstance(Locale.getDefault());
        if (number >= 1_000_000) {
            return String.format(Locale.getDefault(), "%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format(Locale.getDefault(), "%.1fK", number / 1_000.0);
        } else {
            return formatter.format(number);
        }
    }

    public static void updateInfoText(int dist, GameEntityPortal portal, TextView container) {
        String distanceText = getPrettyDistanceString(dist);

        StringBuilder modText = new StringBuilder();
        for (GameEntityPortal.LinkedMod mod : portal.getPortalMods()) {
            if (mod == null) {
                modText.append("\nMOD:");
            } else {
                modText.append("\nMOD: ").append(mod.rarity.name()).append(" ").append(mod.displayName);
            }
        }

        StringBuilder modEffectsText = new StringBuilder("\n");
        int mitigation = portal.getPortalMitigation();
        if (mitigation > 0) {
            modEffectsText.append("\nShielding: ").append(mitigation);
        }
        int hacks = portal.getHacksUntilBurnout();
        modEffectsText.append("\nHacks: ").append(hacks);
        int cooldownSecs = portal.getCooldownSecs();
        modEffectsText.append("\nCooldown: ").append(cooldownSecs).append(" seconds");
        int forceAmplification = portal.getForceAmplification();
        if (forceAmplification > 0) {
            modEffectsText.append("\nForce Amplification: ").append(forceAmplification).append("x");
        }
        int attackFrequency = portal.getAttackFrequency();
        if (attackFrequency > 0) {
            modEffectsText.append("\nAttack Frequency: ").append(attackFrequency).append("%");
        }
        int hitBonus = portal.getHitBonus();
        if (hitBonus > 0) {
            modEffectsText.append("\nHit bonus: ").append(hitBonus).append("%");
        }
        int linkRangeMultiplier = portal.getLinkRangeMultiplier();
        if (linkRangeMultiplier != 1) {
            modEffectsText.append("\nLink Range Multiplier: ").append(linkRangeMultiplier);
        }
        int outgoingLinksBonus = portal.getOutgoingLinksBonus();
        if (outgoingLinksBonus > 0) {
            modEffectsText.append("\nOutgoing Links Bonus: ").append(outgoingLinksBonus);
        }
        float linkDefenseBoost = portal.getLinkDefenseBoost();
        if (linkDefenseBoost > 0) {
            modEffectsText.append("\nLink Defense Boost: ").append(linkDefenseBoost).append("x");
        }

        StringBuilder portalInfoText = new StringBuilder();
        portalInfoText.append("LVL: L").append(portal.getPortalLevel())
                .append("\nRNG: ").append(getPrettyDistanceString(portal.getPortalLinkRange()))
                .append("\nENR: ").append(formatNumberToKLocalized(portal.getPortalEnergy()))
                .append(" / ").append(formatNumberToKLocalized(portal.getPortalMaxEnergy()))
                .append(modText)
                .append(modEffectsText)
//                + "LNK: 0 in, 0 out (unimplemented)"

                .append("\n\nDST: ").append(distanceText)
        ;
        container.setText(portalInfoText);
    }
}

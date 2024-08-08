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

package net.opengress.slimgress.API.Plext;

import static net.opengress.slimgress.ViewHelpers.getAPGainTriggerReason;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.Log;

import net.opengress.slimgress.API.Common.EntityBase;
import net.opengress.slimgress.API.Interface.APGain;
import net.opengress.slimgress.API.Interface.PlayerDamage;
import net.opengress.slimgress.SlimgressApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class PlextBase extends EntityBase {
    public enum PlextType {
        None,
        PlayerGenerated,
        SystemBroadcast,
        SystemNarrowcast
    }

    private final PlextType mPlextType;
    private final String mText;
    private final List<Markup> mMarkups;

    public static PlextBase createByJSON(JSONArray json) throws JSONException {
        if (json.length() != 3) {
            Log.e("PlextBase", "invalid array size");
            return null;
        }

        JSONObject item = json.getJSONObject(2);
        JSONObject plext = item.getJSONObject("plext");

        PlextBase newPlext = null;

        String plextType = plext.getString("plextType");
        switch (plextType) {
            case "NONE" -> newPlext = new PlextNone(json);
            case "PLAYER_GENERATED" -> newPlext = new PlextPlayerGenerated(json);
            case "SYSTEM_BROADCAST" -> newPlext = new PlextSystemBroadcast(json);
            case "SYSTEM_NARROWCAST" -> newPlext = new PlextSystemNarrowcast(json);
            default -> Log.w("PlextBase", "unknown plext type: " + plextType);
        }

        return newPlext;
    }

    @SuppressLint("DefaultLocale")
    public static PlextBase createByPlayerDamage(PlayerDamage dam) {
        String message = String.format("You've been hit and lost %d XM!", dam.getAmount());
        return new PlextBase(PlextType.SystemNarrowcast, message);
    }

    @SuppressLint("DefaultLocale")
    public static PlextBase createByAPGain(APGain gain) {
        String message = String.format("Gained %d AP for %s.", gain.getAmount(), getAPGainTriggerReason(gain.getTrigger()));
        return new PlextBase(PlextType.SystemNarrowcast, message);
    }

    public static PlextBase createByScores(long enl, long res) {
        return new PlextBase(PlextType.None, enl, res);
    }

    // for scores
    protected PlextBase(PlextType type, long enl, long res) {
        super(UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()));
        mPlextType = type;
        var score = new MarkupScore(enl, res);
        mText = score.getPlain();
        mMarkups = new LinkedList<>();
        mMarkups.add(score);
    }

    // for plain text (probably Narrowcasts)
    protected PlextBase(PlextType type, String message) {
        super(UUID.randomUUID().toString(), String.valueOf(System.currentTimeMillis()));
        mPlextType = type;
        mText = message;
        mMarkups = new LinkedList<>();
        mMarkups.add(new MarkupText(message));
    }

    protected PlextBase(PlextType type, JSONArray json) throws JSONException {
        super(json);
        mPlextType = type;

        JSONObject item = json.getJSONObject(2);

        JSONObject plext = item.getJSONObject("plext");
        JSONArray markup = plext.getJSONArray("markup");

        mText = plext.getString("text");

        mMarkups = new LinkedList<>();
        for (int i = 0; i < markup.length(); i++) {
            JSONArray markupItem = markup.getJSONArray(i);

            Markup newMarkup = Markup.createByJSON(markupItem);
            if (newMarkup != null) {
                mMarkups.add(newMarkup);
            }
        }
    }

    public PlextType getPlextType() {
        return mPlextType;
    }

    public String getText() {
        return mText;
    }

    public SpannableStringBuilder getFormattedText() {
        var teams = SlimgressApplication.getInstance().getGame().getKnobs().getTeamKnobs().getTeams();
        int textColour = 0xFFFFFFFF;
        switch (mPlextType) {
            case PlayerGenerated -> textColour = 0xFFCFE5E5;
            case SystemBroadcast -> textColour = 0xFF00BAB5;
            case SystemNarrowcast -> textColour = 0xFFD5AB4C;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder();
        int start = 0;
        for (var mark : mMarkups) {
            String text;
            if (mark.getType() == Markup.MarkupType.Portal) {
                text = ((MarkupPortal) mark).getName();
            } else if (mark.getType() == Markup.MarkupType.Score) {
                // undesirable, and can't be fixed atm due to dynamic team names not being considered
                text = "";
            } else {
                text = mark.getPlain();
            }
            int end = start + text.length();
            spannable.append(text);
            // TODO check intel map and ios client for ideas about these
            switch (mark.getType()) {
                case Secure ->
                    // ios: #F55F55
                        spannable.setSpan(new ForegroundColorSpan(Color.RED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                // "user generated messages"
                // omg maybe not sender - "automatically generated messages"
                case Sender, Player, ATPlayer -> {
                    // ATPlayer might need its own special colour - #FCD452 ?
                    spannable.setSpan(new ForegroundColorSpan(0xff000000 + ((MarkupSender) mark).getTeam().getColour()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new URLSpan("ogagent:" + ((MarkupSender) mark).getGUID()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // maybe not ATPlayer?
                }
                case Portal -> {
                    // maybe don't use team colour? #008780
                    spannable.setSpan(new ForegroundColorSpan(0xff000000 + ((MarkupPortal) mark).getTeam().getColour()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new URLSpan("ogportal:" + ((MarkupPortal) mark).getGUID()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    String text2 = String.format(" (%s)", ((MarkupPortal) mark).getAddress());
                    end += text2.length();
                    spannable.append(text2);
                    // formatting?
                    start += text2.length();
                }
                case Score -> {
                    // FIXME team names
                    String texte = String.format(Locale.getDefault(), "Enlightened: %d", ((MarkupScore) mark).getAliensScore());
                    String textr = String.format(Locale.getDefault(), "Resistance: %d", ((MarkupScore) mark).getAliensScore());
                    spannable.append(texte);
                    spannable.append(" - ");
                    spannable.append(textr);
                    end -= text.length();
                    end += texte.length();
                    spannable.setSpan(new ForegroundColorSpan(0xff000000 + Objects.requireNonNull(teams.get("alien")).getColour()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start += texte.length() + 3; // +3 because of the joiner
                    end += textr.length() + 3;
                    spannable.setSpan(new ForegroundColorSpan(0xff000000 + Objects.requireNonNull(teams.get("human")).getColour()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                default ->
                        spannable.setSpan(new ForegroundColorSpan(textColour), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            start = end;
        }
//        Log.d("PlextBase", spannable.toString());
        return spannable;
    }

    public List<Markup> getMarkups() {
        return mMarkups;
    }
}

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

package net.opengress.slimgress.api.Plext;

import static net.opengress.slimgress.ViewHelpers.getAPGainTriggerReason;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import net.opengress.slimgress.ClickablePlextItemSpan;
import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.Common.EntityBase;
import net.opengress.slimgress.api.Interface.APGain;
import net.opengress.slimgress.api.Interface.PlayerDamage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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

    static class SpanInfo {
        private final Object what;
        private final int start;
        private final int end;
        private final int flags;

        public SpanInfo(Object what, int start, int end, int flags) {
            this.what = what;
            this.start = start;
            this.end = end;
            this.flags = flags;
        }
    }


    private SpannableString mSpannableString;

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

    public static PlextBase createByPlainText(PlextType type, String message) {
        return new PlextBase(type, message);
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

    public SpannableString getFormattedText(boolean isFactionTab) {
        if (mSpannableString != null) {
            return mSpannableString;
        }
        var teams = SlimgressApplication.getInstance().getGame().getKnobs().getTeamKnobs().getTeams();
        int textColour = 0xFFFFFFFF;
        switch (mPlextType) {
            case PlayerGenerated -> textColour = 0xFFCFE5E5;
            case SystemBroadcast -> textColour = 0xFF00BAB5;
            case SystemNarrowcast -> textColour = 0xFFD5AB4C;
        }


        int start = 0;
        StringBuilder sb = new StringBuilder();
        ArrayList<SpanInfo> spans = new ArrayList<>();
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
            sb.append(text);
            switch (mark.getType()) {
                case Secure -> {
                    // ios: #F55F55
                    if (isFactionTab) {
                        sb.delete(0, text.length());
                        end -= text.length();
                    } else {
                        spans.add(new SpanInfo(new ForegroundColorSpan(Color.RED), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                    }
                }
                // "user generated messages"
                // omg maybe not sender - "automatically generated messages"
                case Sender -> {
                    spans.add(new SpanInfo(new ClickablePlextItemSpan(((MarkupSender) mark).getGUID(), 0xff000000 + ((MarkupSender) mark).getTeam().getColour(), null), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                }
                case Player -> {
                    spans.add(new SpanInfo(new ClickablePlextItemSpan(((MarkupPlayer) mark).getGUID(), 0xff000000 + ((MarkupPlayer) mark).getTeam().getColour(), null), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                }
                case ATPlayer -> {
                    // ATPlayer might need its own special colour - #FCD452 ?
                    int colour = Objects.equals(SlimgressApplication.getInstance().getGame().getAgent().getEntityGuid(), ((MarkupATPlayer) mark).getGUID()) ? 0xFCD452 : ((MarkupATPlayer) mark).getTeam().getColour();
                    spans.add(new SpanInfo(new ClickablePlextItemSpan(((MarkupATPlayer) mark).getGUID(), 0xff000000 + colour, null), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                }
                case Portal -> {
                    // maybe don't use team colour? #008780
                    spans.add(new SpanInfo(new ClickablePlextItemSpan(((MarkupPortal) mark).getGUID(), 0xff000000 + ((MarkupPortal) mark).getTeam().getColour(), null), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                    String text2 = String.format(" (%s)", ((MarkupPortal) mark).getAddress());
                    int oldEnd = end;
                    end += text2.length();
                    sb.append(text2);
                    if (oldEnd - end != 0) {
                        spans.add(new SpanInfo(new ForegroundColorSpan(textColour), oldEnd, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                    }
                    // formatting?
                    start += text2.length();
                }
                case Score -> {
                    // FIXME team names
                    String texte = String.format(Locale.getDefault(), "Enlightened: %d", ((MarkupScore) mark).getAliensScore());
                    String textr = String.format(Locale.getDefault(), "Resistance: %d", ((MarkupScore) mark).getResistanceScore());
                    sb.append(texte);
                    sb.append(" - ");
                    sb.append(textr);
                    end -= text.length();
                    end += texte.length();
                    spans.add(new SpanInfo(new ForegroundColorSpan(0xff000000 + Objects.requireNonNull(teams.get("alien")).getColour()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                    start += texte.length() + 3; // +3 because of the joiner
                    end += textr.length() + 3;
                    spans.add(new SpanInfo(new ForegroundColorSpan(0xff000000 + Objects.requireNonNull(teams.get("human")).getColour()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
                }
                default ->
                        spans.add(new SpanInfo(new ForegroundColorSpan(textColour), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE));
            }
            start = end;
        }

        mSpannableString = new SpannableString(sb);
        for (var span : spans) {
            mSpannableString.setSpan(span.what, span.start, span.end, span.flags);
        }
        return mSpannableString;
    }

    public List<Markup> getMarkups() {
        return mMarkups;
    }

    public boolean atMentionsPlayer() {
        for (Markup markup : mMarkups) {
            if (markup.getType() == Markup.MarkupType.ATPlayer) {
                if (Objects.equals(((MarkupATPlayer) markup).getGUID(), SlimgressApplication.getInstance().getGame().getAgent().getEntityGuid())) {
                    return true;
                }
            }
        }
        return false;
    }
}

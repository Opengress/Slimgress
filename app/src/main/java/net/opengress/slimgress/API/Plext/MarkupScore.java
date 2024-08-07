package net.opengress.slimgress.API.Plext;

import org.json.JSONException;
import org.json.JSONObject;

// FIXME team names - shoud be a hashmap or something
public class MarkupScore extends Markup {
    private final long mAliensScore;
    private final long mResistanceScore;

    public MarkupScore(JSONObject json) throws JSONException {
        super(MarkupType.Score, json);
        mAliensScore = json.getLong("ALIENS");
        mResistanceScore = json.getLong("RESISTANCE");
    }

    public MarkupScore(long aliensScore, long resistanceScore) {
        super(aliensScore, resistanceScore);
        mAliensScore = aliensScore;
        mResistanceScore = resistanceScore;
    }

    public long getAliensScore() {
        return mAliensScore;
    }

    public long getResistanceScore() {
        return mResistanceScore;
    }
}

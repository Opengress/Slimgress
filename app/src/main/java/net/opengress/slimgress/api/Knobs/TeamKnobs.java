package net.opengress.slimgress.api.Knobs;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TeamKnobs extends Knobs{

    public static class TeamType {
        private final String mID;
        private final String mName;
        private final int mColour;
//        private final String mIcon;
        private final boolean mPlayable;

        public TeamType(JSONObject json) throws JSONException {
            mID = json.getString("id");
            mName = json.getString("name");
            mColour = json.getInt("colour");
//            mIcon = json.getString("icon");
            mPlayable = json.getBoolean("playable_human");
        }

        public String getId() {
            return mID;
        }

        public String getName() {
            return mName;
        }

        public int getColour() {
            return mColour;
        }

        public boolean isPlayable() {
            return mPlayable;
        }

    }

    private final Map<String, TeamType> mTeamsMap;

    public TeamKnobs(JSONObject json) throws JSONException
    {
        super(json);

        mTeamsMap = new HashMap<>();
        Iterator<?> it = json.keys();
        while (it.hasNext()) {
            String key = (String)it.next();
            TeamType t = new TeamType(json.getJSONObject(key));
            mTeamsMap.put(key, t);
        }
    }

    public TeamType fromString(String name) {
        return mTeamsMap.get(name);
    }

    public Map<String, TeamType> getTeams() {
        return mTeamsMap;
    }
}

package net.opengress.slimgress.api.Knobs;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PlayerLevelKnobs extends Knobs {

    public class PlayerLevel {
        private final int mApRequired;
        private int mSilverRequired = 0;
        private int mGoldRequired = 0;
        private int mPlatinumRequired = 0;
        private int mOnyxRequired = 0;

        public PlayerLevel(JSONObject json) throws JSONException {
                mApRequired = json.getInt("apRequired");
                JSONObject achievementsRequired = json.optJSONObject("achievementsRequired");
                if (achievementsRequired != null) {
                    mSilverRequired = achievementsRequired.optInt("SILVER", 0);
                    mGoldRequired = achievementsRequired.optInt("GOLD", 0);
                    mPlatinumRequired = achievementsRequired.optInt("PLATINUM", 0);
                    mOnyxRequired = achievementsRequired.optInt("ONYX", 0);
                }
        }

        public int getApRequired() {
            return mApRequired;
        }

        public int getSilverRequired() {
            return mSilverRequired;
        }

        public int getGoldRequired() {
            return mGoldRequired;
        }

        public int getPlatinumRequired() {
            return mPlatinumRequired;
        }

        public int getOnyxRequired() {
            return mOnyxRequired;
        }
    }

    private final Map<String, PlayerLevel> mPlayerLevelsMap;
    private final Map<String, Integer> mXmCapacityByLevel;
    private final Map<String, Integer> mBoostedPowerCubeCapacityByLevel;

    public PlayerLevelKnobs(JSONObject json) throws JSONException
    {
        super(json);

        mPlayerLevelsMap = new HashMap<>();
        JSONObject levelUpRequirementsMap = json.getJSONObject("levelUpRequirements");
        Iterator<?> it = levelUpRequirementsMap.keys();
        while (it.hasNext()) {
            String key = (String)it.next();
            PlayerLevel thisLevel = new PlayerLevel(levelUpRequirementsMap.getJSONObject(key));
            mPlayerLevelsMap.put(key, thisLevel);
        }

        mXmCapacityByLevel = new HashMap<>();
        JSONObject xmCapacityMap = json.getJSONObject("xmCapacityByLevel");
        Iterator<?> xmCapacityIterator = xmCapacityMap.keys();
        while (xmCapacityIterator.hasNext()) {
            String key = (String)xmCapacityIterator.next();
            mXmCapacityByLevel.put(key, xmCapacityMap.getInt(key));
        }

        mBoostedPowerCubeCapacityByLevel = new HashMap<>();
        JSONObject boostedPowerCubeLevelMap = json.getJSONObject("boostedPowerCubeCapacityByLevel");
        Iterator<?> boostedCubeLevelMapIterator = xmCapacityMap.keys();
        while (boostedCubeLevelMapIterator.hasNext()) {
            String key = (String)boostedCubeLevelMapIterator.next();
            mBoostedPowerCubeCapacityByLevel.put(key, boostedPowerCubeLevelMap.getInt(key));
        }

    }

    public PlayerLevel getLevelUpRequirement(int which)
    {
        String key = String.valueOf(which);
        if (!mPlayerLevelsMap.containsKey(key))
            Log.e("PlayerLevelKnobs", "key not found in LevelUpRequirements hash map: " + key);

        return mPlayerLevelsMap.get(key);
    }

    public PlayerLevel getLevelUpRequirement(String key) {
        if (!mPlayerLevelsMap.containsKey(key)) {
            Log.e("PlayerLevelKnobs", "key not found in LevelUpRequirements hash map: " + key);
        }

        return mPlayerLevelsMap.get(key);
    }

    public Integer getXmCapacityForLevel(Integer key)
    {
        if (!mXmCapacityByLevel.containsKey(String.valueOf(key)))
            Log.e("PlayerLevelKnobs", "key not found in XmCapacity hash map: " + key);

        return mXmCapacityByLevel.get(String.valueOf(key));
    }

    public Integer getBoostedPowerCubeCapacityByLevel(Integer key)
    {
        if (!mBoostedPowerCubeCapacityByLevel.containsKey(String.valueOf(key)))
            Log.e("PlayerLevelKnobs", "key not found in PowerCubeCapacity hash map: " + key);

        return mBoostedPowerCubeCapacityByLevel.get(String.valueOf(key));
    }

    public Map<String, PlayerLevel> getPlayerLevelsMap() {
        return mPlayerLevelsMap;
    }
}

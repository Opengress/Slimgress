package net.opengress.slimgress.api;

import android.os.Handler;

import net.opengress.slimgress.SlimgressApplication;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class BulkPlayerStorage {
    private final Map<String, String> mStorageMap;

    public enum MissionStatus {
        ENDED_BY_NAGGING,
        SUCCESS,
        UNKNOWN
    }

    public BulkPlayerStorage(JSONObject storage) throws JSONException {
        mStorageMap = new HashMap<>();
        Iterator<String> it = storage.keys();
        while (it.hasNext()) {
            String key = it.next();
            mStorageMap.put(key, storage.getString(key));
        }
    }

    public boolean getBool(String name, boolean def) {
        String value = mStorageMap.get(name);
        if (value != null) {
            return Boolean.parseBoolean(value.split(":delim:")[0]);
        }
        return def;
    }

    public boolean getBool(String name) {
        return getBool(name, false);
    }

    public String getString(String name, String def) {
        String value = mStorageMap.get(name);
        if (value != null) {
            return value.split(":delim:")[0];
        }
        return def;
    }

    public String getString(String name) {
        return getString(name, null);
    }

    public MissionStatus getMissionStatus(String name, MissionStatus def) {
        String value = mStorageMap.get(name);
        if (value != null) {
            String status = value.split(":delim:")[0];
            return switch (status) {
                case "ENDED_BY_NAGGING" -> MissionStatus.ENDED_BY_NAGGING;
                case "SUCCESS" -> MissionStatus.SUCCESS;
                default -> MissionStatus.UNKNOWN;
            };
        }
        return MissionStatus.UNKNOWN;
    }

    public MissionStatus getMissionStatus(String name) {
        return getMissionStatus(name, MissionStatus.UNKNOWN);
    }

    public long getTimestamp(String name, int def) {
        String value = mStorageMap.get(name);
        if (value != null) {
            return Long.parseLong(value.split(":delim:")[1]);
        }
        return def;
    }

    public long getTimestamp(String name) {
        // maybe this should be now?
        return getTimestamp(name, 0);
    }

    public void putBool(String name, boolean value) {
        mStorageMap.put(name, value + ":delim:" + System.currentTimeMillis() + ":delim:true");
    }

    public void putString(String name, String value) {
        mStorageMap.put(name, value + ":delim:" + System.currentTimeMillis() + ":delim:true");
    }

    public void putMissionStatus(String name, MissionStatus status) {
        mStorageMap.put(name, status.name() + ":delim:" + System.currentTimeMillis() + ":delim:true");
    }

    public void delete(String name) {
        mStorageMap.remove(name);
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : mStorageMap.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json;
    }

    public void apply() {
        commit();
    }

    public void commit() {
        SlimgressApplication.getInstance().getGame().intPutBulkPlayerStorage(new Handler());
    }

}

package net.opengress.slimgress.api.Plext;

import org.json.JSONArray;
import org.json.JSONException;

public class PlextNone extends PlextBase {
    public PlextNone(JSONArray json) throws JSONException {
        super(PlextType.None, json);
    }
}
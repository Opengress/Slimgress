package net.opengress.slimgress.api.Plext;

import org.json.JSONArray;
import org.json.JSONException;

public class PlextSystemNarrowcast extends PlextBase {
    public PlextSystemNarrowcast(JSONArray json) throws JSONException {
        super(PlextType.SystemNarrowcast, json);
    }
}
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

package net.opengress.slimgress.api.Common;

import org.json.JSONArray;
import org.json.JSONException;

public abstract class EntityBase
{
    private final String mEntityGuid;
    private final String mEntityTimestamp;

    public EntityBase(String guid, String timestamp) {
        mEntityGuid = guid;
        mEntityTimestamp = timestamp;
    }

    public EntityBase(JSONArray json) throws JSONException
    {
        if (json.length() != 3)
            throw new JSONException("invalid array size");

        mEntityGuid = json.getString(0);
        mEntityTimestamp = json.getString(1);
    }

    public String getEntityGuid()
    {
        return mEntityGuid;
    }

    public String getEntityTimestamp()
    {
        return mEntityTimestamp;
    }
}

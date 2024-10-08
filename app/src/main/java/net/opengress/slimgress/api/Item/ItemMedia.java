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

package net.opengress.slimgress.api.Item;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ItemMedia extends ItemBase
{
    private final String mMediaImageUrl;
    private final String mMediaUrl;
    private final String mMediaDescription;
    private final boolean mMediaHasBeenViewed;
    private final String mMediaReleaseDate;

    public ItemMedia(JSONArray json) throws JSONException
    {
        super(ItemType.Media, json);

        JSONObject item = json.getJSONObject(2);
        JSONObject imageByUrl = item.getJSONObject("imageByUrl");
        JSONObject storyItem = item.getJSONObject("storyItem");

        mMediaImageUrl = imageByUrl.getString("imageUrl");
        mMediaUrl = storyItem.getString("primaryUrl");
        mMediaDescription = storyItem.getString("shortDescription");
        mMediaHasBeenViewed = storyItem.getBoolean("hasBeenViewed");
        mMediaReleaseDate = storyItem.getString("releaseDate");
    }

    public static String getNameStatic()
    {
        return "MEDIA";
    }

    public String getName()
    {
        return getNameStatic();
    }

    public String getMediaImageUrl()
    {
        return mMediaImageUrl;
    }

    public String getMediaUrl()
    {
        return mMediaUrl;
    }

    public String getMediaDescription()
    {
        return mMediaDescription;
    }

    public boolean getMediaHasBeenViewed()
    {
        return mMediaHasBeenViewed;
    }

    public String getMediaReleaseDate()
    {
        return mMediaReleaseDate;
    }

    @Override
    public String getUsefulName() {
        return getDisplayName() + ": " + getMediaDescription();
    }
}

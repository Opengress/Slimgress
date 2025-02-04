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

package net.opengress.slimgress.api.Game;

import androidx.annotation.NonNull;

import net.opengress.slimgress.api.Common.Location;

public class XMParticle
{
    private final String mGuid;
    private final String mEnergyTimestamp;
    private final long mCellId;
    private final int mAmount;
    private final Location mCellLocation;

    public XMParticle(@NonNull String guid, String timestamp)
    {
        // note that guids should be 32 bytes long plus suffix
        mGuid = guid;
        mEnergyTimestamp = timestamp;

        mCellId = Long.parseUnsignedLong(guid.substring(0, 16), 16);
        mCellLocation = new Location(mCellId);

        // NOTE: API may differ from *rime because our globs can be HUGE
        String amountStr = guid.substring(guid.length() - 16, guid.length() - 2);
        mAmount = Integer.parseInt(amountStr, 16);

    }

    public String getGuid()
    {
        return mGuid;
    }

    public String getEnergyTimestamp()
    {
        return mEnergyTimestamp;
    }

    public long getCellId()
    {
        return mCellId;
    }

    public int getAmount()
    {
        return mAmount;
    }

    public Location getCellLocation()
    {
        return mCellLocation;
    }
}

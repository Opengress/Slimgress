/**********************************************************************

 Slimgress: Ingress API for Android
 Copyright (C) 2013 Norman Link <norman.link@gmx.net>

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

package com.norman0406.slimgress.API.Game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.norman0406.slimgress.API.GameEntity.GameEntityBase;
import com.norman0406.slimgress.API.Interface.GameBasket;

public class World
{
    private final Map<String, GameEntityBase> mGameEntities;
    private final Map<String, XMParticle> mXMParticles;

    public World()
    {
        mGameEntities = new HashMap<>();
        mXMParticles = new HashMap<>();
    }

    public void clear()
    {
        mGameEntities.clear();
        mXMParticles.clear();
    }

    public void processGameBasket(GameBasket basket)
    {
        System.err.println("entities size:");
        System.err.println(basket.getGameEntities().size());
        // only add non-existing game entities
        List<GameEntityBase> entities = basket.getGameEntities();
        for (GameEntityBase entity : entities) {
            System.err.println(entity.getEntityGuid());
            if (!mGameEntities.containsKey(entity.getEntityGuid()))
                mGameEntities.put(entity.getEntityGuid(), entity);
        }

        // only add non-existing xm particles
        List<XMParticle> xmParticles = basket.getEnergyGlobGuids();
        for (XMParticle particle : xmParticles) {
            if (!mXMParticles.containsKey(particle.getGuid()))
                mXMParticles.put(particle.getGuid(), particle);
        }

        // remove deleted entities
        List<String> deletedEntityGuids = basket.getDeletedEntityGuids();
        for (String guid : deletedEntityGuids) {
            mGameEntities.remove(guid);
            mXMParticles.remove(guid);  // necessary?
        }
    }

    public final Map<String, GameEntityBase> getGameEntities()
    {
        return mGameEntities;
    }

    public final List<GameEntityBase> getGameEntitiesList()
    {
        return new ArrayList<>(mGameEntities.values());
    }

    public final Map<String, XMParticle> getXMParticles()
    {
        return mXMParticles;
    }

    public final List<XMParticle> getXMParticlesList()
    {
        return new ArrayList<>(mXMParticles.values());
    }
}

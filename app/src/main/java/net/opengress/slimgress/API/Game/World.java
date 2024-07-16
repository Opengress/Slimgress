/*

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

package net.opengress.slimgress.API.Game;

import com.github.msteinbeck.sig4j.signal.Signal1;
import com.github.msteinbeck.sig4j.slot.Slot1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.opengress.slimgress.API.GameEntity.GameEntityBase;
import net.opengress.slimgress.API.Interface.GameBasket;

public class World
{
    private final Map<String, GameEntityBase> mGameEntities;
    private final Map<Long, XMParticle> mXMParticles;
    private final Signal1<List<String>> mSignalDeletedEntities = new Signal1<>();

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
        // only add non-existing game entities
        List<GameEntityBase> entities = basket.getGameEntities();
        for (GameEntityBase entity : entities) {
            if (!mGameEntities.containsKey(entity.getEntityGuid()))
                mGameEntities.put(entity.getEntityGuid(), entity);
        }

        // only add non-existing xm particles
        List<XMParticle> xmParticles = basket.getEnergyGlobGuids();
        for (XMParticle particle : xmParticles) {
            if (!mXMParticles.containsKey(particle.getCellId()))
                mXMParticles.put(particle.getCellId(), particle);
        }

        // remove deleted entities
        List<String> deletedEntityGuids = basket.getDeletedEntityGuids();
        mSignalDeletedEntities.emit(deletedEntityGuids);
        for (String guid : deletedEntityGuids) {
            mGameEntities.remove(guid);
            mXMParticles.remove(Long.parseLong(guid.substring(0, 16), 16));
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

    public final Map<Long, XMParticle> getXMParticles()
    {
        return mXMParticles;
    }

    public final List<XMParticle> getXMParticlesList()
    {
        return new ArrayList<>(mXMParticles.values());
    }

    public void connectSignalDeletedEntities(Slot1<List<String>> handler) {
        mSignalDeletedEntities.connect(handler);
    }

}

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

import net.opengress.slimgress.SlimgressApplication;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;
import net.opengress.slimgress.api.GameEntity.GameEntityPortal;
import net.opengress.slimgress.api.Interface.GameBasket;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class World {
    private final Map<String, GameEntityBase> mGameEntities;
    private final Map<Long, XMParticle> mXMParticles;

    public World() {
        mGameEntities = new HashMap<>();
        mXMParticles = new HashMap<>();
    }

    public void clear() {
        mGameEntities.clear();
        mXMParticles.clear();
    }

    public void deleteEntityByGuid(@NonNull String guid) {
        if (guid.endsWith(".6")) {
            mXMParticles.remove(Long.parseLong(guid.substring(0, 16), 16));
        } else {
            mGameEntities.remove(guid);
        }
    }

    public void deleteEntitiesByGuid(@NonNull List<String> deletedEntityGuids) {
        SlimgressApplication.getInstance().getDeletedEntityGuidsViewModel().addGuids(deletedEntityGuids);
        for (String guid : deletedEntityGuids) {
            deleteEntityByGuid(guid);
        }
    }

    public void processGameBasket(@NonNull GameBasket basket) {

        // remove deleted entities
        List<String> deletedEntityGuids = basket.getDeletedEntityGuids();
        deleteEntitiesByGuid(deletedEntityGuids);

        // only add non-existing game entities ... should this be a map?
        List<GameEntityBase> entities = basket.getGameEntities();
        ArrayList<GameEntityBase> out = new ArrayList<>();
        for (GameEntityBase entity : entities) {
            if (entity.getGameEntityType() == GameEntityBase.GameEntityType.Portal && mGameEntities.containsKey(entity.getEntityGuid())) {
                Objects.requireNonNull((GameEntityPortal) mGameEntities.get(entity.getEntityGuid())).updateFrom(entity);
                out.add(mGameEntities.get(entity.getEntityGuid()));
                continue;
            }
            out.add(entity);
            mGameEntities.put(entity.getEntityGuid(), entity);
        }
        SlimgressApplication.getInstance().getUpdatedEntitiesViewModel().addEntities(out);

        // only add non-existing xm particles
        List<XMParticle> xmParticles = basket.getEnergyGlobGuids();
        for (XMParticle particle : xmParticles) {
            if (!mXMParticles.containsKey(particle.getCellId())) {
                mXMParticles.put(particle.getCellId(), particle);
            }
        }
        SlimgressApplication.getInstance().getUpdatedEntitiesViewModel().addParticles(xmParticles);
    }

    public final Map<String, GameEntityBase> getGameEntities() {
        return mGameEntities;
    }

    @NonNull
    @Contract(" -> new")
    public final List<GameEntityBase> getGameEntitiesList() {
        return new ArrayList<>(mGameEntities.values());
    }

    public final Map<Long, XMParticle> getXMParticles() {
        return mXMParticles;
    }

    @NonNull
    @Contract(" -> new")
    public final List<XMParticle> getXMParticlesList() {
        return new ArrayList<>(mXMParticles.values());
    }

}

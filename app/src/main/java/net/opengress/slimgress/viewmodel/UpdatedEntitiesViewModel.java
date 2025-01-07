package net.opengress.slimgress.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.api.Game.XMParticle;
import net.opengress.slimgress.api.GameEntity.GameEntityBase;

import java.util.List;

public class UpdatedEntitiesViewModel extends ViewModel {
    private final MutableLiveData<List<GameEntityBase>> mGameEntities = new MutableLiveData<>();
    private final MutableLiveData<List<XMParticle>> mParticles = new MutableLiveData<>();

    public LiveData<List<GameEntityBase>> getEntities() {
        return mGameEntities;
    }

    public void addEntities(List<GameEntityBase> entities) {
        mGameEntities.postValue(entities);
    }

    public LiveData<List<XMParticle>> getParticles() {
        return mParticles;
    }

    public void addParticles(List<XMParticle> particles) {
        mParticles.postValue(particles);
    }
}
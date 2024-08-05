package net.opengress.slimgress.API.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.API.Interface.PlayerDamage;

import java.util.List;

public class PlayerDamagesViewModel extends ViewModel {
    private final MutableLiveData<List<PlayerDamage>> mPlayerDamages = new MutableLiveData<>();

    public LiveData<List<PlayerDamage>> getPlayerDamages() {
        return mPlayerDamages;
    }

    public void setPlayerDamages(List<PlayerDamage> damages) {
        mPlayerDamages.setValue(damages);
    }

    public void postPlayerDamages(List<PlayerDamage> damages) {
        mPlayerDamages.postValue(damages);
    }
}
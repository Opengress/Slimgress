package net.opengress.slimgress.API.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.API.Interface.APGain;

import java.util.List;

public class APGainsViewModel extends ViewModel {
    private final MutableLiveData<List<APGain>> mAPGains = new MutableLiveData<>();

    public LiveData<List<APGain>> getAPGains() {
        return mAPGains;
    }

    public void setAPGains(List<APGain> gains) {
        mAPGains.setValue(gains);
    }

    public void postAPGains(List<APGain> gains) {
        mAPGains.postValue(gains);
    }
}
package net.opengress.slimgress.api.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LevelUpViewModel extends ViewModel {
    private final MutableLiveData<Integer> mLevelUpMsgId = new MutableLiveData<>();

    public LiveData<Integer> getLevelUpMsgId() {
        return mLevelUpMsgId;
    }

    public void setLevelUpMsgId(Integer lvl) {
        mLevelUpMsgId.setValue(lvl);
    }

    public void postLevelUpMsgId(Integer lvl) {
        mLevelUpMsgId.postValue(lvl);
    }
}
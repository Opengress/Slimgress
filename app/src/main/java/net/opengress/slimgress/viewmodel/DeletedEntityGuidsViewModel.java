package net.opengress.slimgress.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class DeletedEntityGuidsViewModel extends ViewModel {
    private final MutableLiveData<List<String>> mDeletedEntityGuids = new MutableLiveData<>();

    public LiveData<List<String>> getGuids() {
        return mDeletedEntityGuids;
    }

    public void addGuids(List<String> ids) {
        mDeletedEntityGuids.postValue(ids);
    }
}
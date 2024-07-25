package net.opengress.slimgress.API.Common;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class DeletedEntitiesGuidsModel extends ViewModel {
    private final MutableLiveData<List<String>> mDeletedEntityGuids = new MutableLiveData<>();

    public LiveData<List<String>> getDeletedEntityGuids() {
        return mDeletedEntityGuids;
    }

    public void setDeletedEntityGuids(List<String> guids) {
        mDeletedEntityGuids.setValue(guids);
    }

    public void postDeletedEntityGuids(List<String> guids) {
        mDeletedEntityGuids.postValue(guids);
    }
}

package net.opengress.slimgress.API.Common;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LocationViewModel extends ViewModel {
    private final MutableLiveData<Location> mLocationData = new MutableLiveData<>();

    public LiveData<Location> getLocationData() {
        return mLocationData;
    }

    public void setLocationData(Location location) {
        mLocationData.setValue(location);
    }
}
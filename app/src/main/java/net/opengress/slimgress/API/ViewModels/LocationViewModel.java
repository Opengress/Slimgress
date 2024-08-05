package net.opengress.slimgress.API.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.API.Common.Location;

public class LocationViewModel extends ViewModel {
    private final MutableLiveData<Location> mLocationData = new MutableLiveData<>();

    public LiveData<Location> getLocationData() {
        return mLocationData;
    }

    public void setLocationData(Location location) {
        mLocationData.setValue(location);
    }
}
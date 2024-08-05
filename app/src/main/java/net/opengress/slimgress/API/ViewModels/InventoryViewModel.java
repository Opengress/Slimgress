package net.opengress.slimgress.API.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.API.Game.Inventory;

public class InventoryViewModel extends ViewModel {
    private final MutableLiveData<Inventory> mInventory = new MutableLiveData<>();

    public LiveData<Inventory> getInventory() {
        return mInventory;
    }

    public void setInventory(Inventory inventory) {
        mInventory.setValue(inventory);
    }

    public void postInventory(Inventory inventory) {
        mInventory.postValue(inventory);
    }
}

package net.opengress.slimgress.API.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.API.Player.Agent;

// I know this one is named funny...
public class PlayerDataViewModel extends ViewModel {
    private final MutableLiveData<Agent> mAgent = new MutableLiveData<>();

    public LiveData<Agent> getAgent() {
        return mAgent;
    }

    public void setAgent(Agent agent) {
        mAgent.setValue(agent);
    }

    public void postAgent(Agent agent) {
        mAgent.postValue(agent);
    }
}
package net.opengress.slimgress.API.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.API.Plext.PlextBase;

import java.util.ArrayList;
import java.util.List;

public class CommsViewModel extends ViewModel {
    private final MutableLiveData<List<PlextBase>> mAllMessages = new MutableLiveData<>();
    private final MutableLiveData<List<PlextBase>> mFactionMessages = new MutableLiveData<>();
    private final MutableLiveData<List<PlextBase>> mAlertMessages = new MutableLiveData<>();
    private final MutableLiveData<List<PlextBase>> mInfoMessages = new MutableLiveData<>();

    public LiveData<List<PlextBase>> getAllMessages() {
        return mAllMessages;
    }

    public LiveData<List<PlextBase>> getFactionMessages() {
        return mFactionMessages;
    }

    public LiveData<List<PlextBase>> getAlertMessages() {
        return mAlertMessages;
    }

    public LiveData<List<PlextBase>> getInfoMessages() {
        return mInfoMessages;
    }

    public void setMessages(List<PlextBase> newMessages, String category) {
        switch (category) {
            case "ALL" -> mAllMessages.setValue(newMessages);
            case "FACTION" -> mFactionMessages.setValue(newMessages);
            case "ALERT" -> mAlertMessages.setValue(newMessages);
            case "INFO" -> mInfoMessages.setValue(newMessages);
        }
    }

    public void addMessage(PlextBase message, String category) {
        // NB updates are posted and may not appear instantly
        List<PlextBase> currentMessages;
        switch (category) {
            case "FACTION" -> {
                currentMessages = mFactionMessages.getValue() != null ? mFactionMessages.getValue() : new ArrayList<>();
                currentMessages.add(message);
                mFactionMessages.postValue(currentMessages);
            }
            case "ALERT" -> {
                currentMessages = mAlertMessages.getValue() != null ? mAlertMessages.getValue() : new ArrayList<>();
                currentMessages.add(message);
                mAlertMessages.postValue(currentMessages);
            }
            case "INFO" -> {
                currentMessages = mInfoMessages.getValue() != null ? mInfoMessages.getValue() : new ArrayList<>();
                currentMessages.add(message);
                mInfoMessages.postValue(currentMessages);
            }
        }
        // Update ALL category
        List<PlextBase> allCurrentMessages = mAllMessages.getValue() != null ? mAllMessages.getValue() : new ArrayList<>();
        allCurrentMessages.add(message);
        mAllMessages.postValue(allCurrentMessages);
    }
}
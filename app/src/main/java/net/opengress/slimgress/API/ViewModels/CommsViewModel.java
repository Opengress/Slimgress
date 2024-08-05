package net.opengress.slimgress.API.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;

public class CommsViewModel extends ViewModel {
    private final MutableLiveData<List<SimpleEntry<Long, String>>> mAllMessages = new MutableLiveData<>();
    private final MutableLiveData<List<SimpleEntry<Long, String>>> mFactionMessages = new MutableLiveData<>();
    private final MutableLiveData<List<SimpleEntry<Long, String>>> mAlertMessages = new MutableLiveData<>();
    private final MutableLiveData<List<SimpleEntry<Long, String>>> mInfoMessages = new MutableLiveData<>();

    public LiveData<List<SimpleEntry<Long, String>>> getAllMessages() {
        return mAllMessages;
    }

    public LiveData<List<SimpleEntry<Long, String>>> getFactionMessages() {
        return mFactionMessages;
    }

    public LiveData<List<SimpleEntry<Long, String>>> getAlertMessages() {
        return mAlertMessages;
    }

    public LiveData<List<SimpleEntry<Long, String>>> getInfoMessages() {
        return mInfoMessages;
    }

    public void setMessages(List<SimpleEntry<Long, String>> newMessages, String category) {
        switch (category) {
            case "ALL":
                mAllMessages.setValue(newMessages);
                break;
            case "FACTION":
                mFactionMessages.setValue(newMessages);
                break;
            case "ALERT":
                mAlertMessages.setValue(newMessages);
                break;
            case "INFO":
                mInfoMessages.setValue(newMessages);
                break;
        }
    }

    public void addMessage(SimpleEntry<Long, String> message, String category) {
        List<SimpleEntry<Long, String>> currentMessages;
        switch (category) {
            case "FACTION":
                currentMessages = mFactionMessages.getValue() != null ? mFactionMessages.getValue() : new ArrayList<>();
                currentMessages.add(message);
                mFactionMessages.setValue(currentMessages);
                break;
            case "ALERT":
                currentMessages = mAlertMessages.getValue() != null ? mAlertMessages.getValue() : new ArrayList<>();
                currentMessages.add(message);
                mAlertMessages.setValue(currentMessages);
                break;
            case "INFO":
                currentMessages = mInfoMessages.getValue() != null ? mInfoMessages.getValue() : new ArrayList<>();
                currentMessages.add(message);
                mInfoMessages.setValue(currentMessages);
                break;
        }
        // Update ALL category
        List<SimpleEntry<Long, String>> allCurrentMessages = mAllMessages.getValue() != null ? mAllMessages.getValue() : new ArrayList<>();
        allCurrentMessages.add(message);
        mAllMessages.setValue(allCurrentMessages);
    }
}

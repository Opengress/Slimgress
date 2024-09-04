package net.opengress.slimgress.api.ViewModels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.api.Plext.PlextBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommsViewModel extends ViewModel {
    private final MutableLiveData<List<PlextBase>> mPlexts = new MutableLiveData<>();
    private final Set<String> allGuids = new HashSet<>();

    public LiveData<List<PlextBase>> getMessages() {
        return mPlexts;
    }

    public void setMessages(List<PlextBase> newMessages) {
        allGuids.clear();
        for (PlextBase message : newMessages) {
            allGuids.add(message.getEntityGuid());
        }
        mPlexts.setValue(newMessages);
    }

    public void postMessages(List<PlextBase> newMessages) {
        allGuids.clear();
        for (PlextBase message : newMessages) {
            allGuids.add(message.getEntityGuid());
        }
        mPlexts.postValue(newMessages);
    }

    public synchronized void addMessage(PlextBase message) {
        if (allGuids.contains(message.getEntityGuid())) {
            return;
        }

        List<PlextBase> currentMessages = getMessageList();
        currentMessages.add(message);
        allGuids.add(message.getEntityGuid());
        sortMessagesByTimestamp(currentMessages);
        mPlexts.postValue(currentMessages);
    }

    public synchronized void addMessages(List<PlextBase> messages) {
        List<PlextBase> currentMessages = getMessageList();
        boolean newMessagesAdded = false;

        for (PlextBase message : messages) {
            if (!allGuids.contains(message.getEntityGuid())) {
                currentMessages.add(message);
                allGuids.add(message.getEntityGuid());
                newMessagesAdded = true;
            }
        }

        if (newMessagesAdded) {
            sortMessagesByTimestamp(currentMessages);
            mPlexts.postValue(currentMessages);
        }
    }

    private void sortMessagesByTimestamp(List<PlextBase> messages) {
        Collections.sort(messages, (o1, o2) -> {
            long timestamp1 = Long.parseLong(o1.getEntityTimestamp());
            long timestamp2 = Long.parseLong(o2.getEntityTimestamp());
            return Long.compare(timestamp1, timestamp2);
        });
    }

    private List<PlextBase> getMessageList() {
        return mPlexts.getValue() != null ? new ArrayList<>(mPlexts.getValue()) : new ArrayList<>();
    }
}


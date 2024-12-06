package net.opengress.slimgress.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import net.opengress.slimgress.api.Plext.PlextBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class CommsViewModel extends ViewModel {
    private final MutableLiveData<List<PlextBase>> mPlexts = new MutableLiveData<>(new ArrayList<>());
    private final Set<String> mMessageGuids = new HashSet<>();  // deduplication
    private final Object mLock = new Object();
    // Cached version of the sorted list
    private List<PlextBase> cachedSortedMessages;

    /**
     * Gets the current list of messages, already sorted and deduplicated.
     */
    public LiveData<List<PlextBase>> getMessages() {
        synchronized (mLock) {
            if (cachedSortedMessages == null) {
                cachedSortedMessages = sortMessagesByTimestamp(mPlexts.getValue());
            }
            return mPlexts;
        }
    }

    public void setMessages(List<PlextBase> newMessages) {
        mMessageGuids.clear();
        for (PlextBase message : newMessages) {
            mMessageGuids.add(message.getEntityGuid());
        }
        mPlexts.setValue(newMessages);
    }

    public void postMessages(List<PlextBase> newMessages) {
        mMessageGuids.clear();
        for (PlextBase message : newMessages) {
            mMessageGuids.add(message.getEntityGuid());
        }
        mPlexts.postValue(newMessages);
    }

    public void addMessage(PlextBase message) {
        if (mMessageGuids.contains(message.getEntityGuid())) {
            return;
        }

        List<PlextBase> currentMessages = getMessageList();
        currentMessages.add(message);
        mMessageGuids.add(message.getEntityGuid());
        sortMessagesByTimestamp(currentMessages);
        mPlexts.postValue(currentMessages);
    }

    /**
     * Adds a batch of messages to the list, efficiently handling deduplication and sorting.
     */
    public void addMessages(List<PlextBase> newMessages) {
        if (newMessages == null || newMessages.isEmpty()) {
            return;
        }

        synchronized (mLock) {
            List<PlextBase> currentMessages = new ArrayList<>(mPlexts.getValue());
            boolean updated = false;

            for (PlextBase newMessage : newMessages) {
                // Deduplication: Add only if message GUID is new
                if (!mMessageGuids.contains(newMessage.getEntityGuid())) {
                    currentMessages.add(newMessage);
                    mMessageGuids.add(newMessage.getEntityGuid());
                    updated = true;
                }
            }

            if (updated) {
                // Sort only if new messages were added, to avoid unnecessary sorting
                cachedSortedMessages = sortMessagesByTimestamp(currentMessages);
                mPlexts.postValue(cachedSortedMessages);
            }
        }
    }

    private List<PlextBase> sortMessagesByTimestamp(List<PlextBase> messages) {
        if (messages == null || messages.size() <= 1) {
            return messages;  // Already sorted
        }
        Collections.sort(messages, (o1, o2) -> {
            long timestamp1 = Long.parseLong(o1.getEntityTimestamp());
            long timestamp2 = Long.parseLong(o2.getEntityTimestamp());
            return Long.compare(timestamp1, timestamp2);
        });
        return messages;
    }

    private List<PlextBase> getMessageList() {
        return getMessages().getValue();
    }

    /**
     * Removes all messages, clearing both the displayed list and the deduplication set.
     */
    public void clearMessages() {
        synchronized (mLock) {
            mPlexts.postValue(new ArrayList<>());  // Notify observers immediately
            mMessageGuids.clear();  // Clear deduplication set
            cachedSortedMessages = null;  // Invalidate cache
        }
    }

    /**
     * Deletes messages older than a certain timestamp. This is useful for removing stale data.
     */
    public void deleteMessagesOlderThan(long timestamp) {
        synchronized (mLock) {
            List<PlextBase> currentMessages = new ArrayList<>(mPlexts.getValue());
            boolean updated = false;

            Iterator<PlextBase> iterator = currentMessages.iterator();
            while (iterator.hasNext()) {
                PlextBase message = iterator.next();
                if (Long.parseLong(message.getEntityTimestamp()) < timestamp) {
                    iterator.remove();  // Remove message if older than timestamp
                    updated = true;
                }
            }

            if (updated) {
                mMessageGuids.clear();  // Rebuild the deduplication set
                for (PlextBase message : currentMessages) {
                    mMessageGuids.add(message.getEntityGuid());
                }

                cachedSortedMessages = sortMessagesByTimestamp(currentMessages);  // Update sorted cache
                mPlexts.postValue(cachedSortedMessages);
            }
        }
    }
}



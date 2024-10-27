package net.opengress.slimgress.positioning;

public interface BearingProvider {
    void startBearingUpdates();

    void stopBearingUpdates();

    void setBearingCallback(BearingCallback callback);
}

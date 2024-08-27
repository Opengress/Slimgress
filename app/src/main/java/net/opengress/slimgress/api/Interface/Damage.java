package net.opengress.slimgress.api.Interface;

import android.os.Parcel;
import android.os.Parcelable;

public class Damage implements Parcelable {
    private final String responsibleGuid;
    private final String targetGuid;
    private final int targetSlot;
    private final String resonatorId;
    private final boolean criticalHit;
    private final int damageAmount;
    private final boolean targetDestroyed;

    public Damage(Parcel in) {
        responsibleGuid = in.readString();
        targetGuid = in.readString();
        targetSlot = in.readInt();
        resonatorId = in.readString();
        criticalHit = in.readByte() != 0;
        damageAmount = in.readInt();
        targetDestroyed = in.readByte() != 0;
    }

    public static final Creator<Damage> CREATOR = new Creator<>() {
        @Override
        public Damage createFromParcel(Parcel in) {
            return new Damage(in);
        }

        @Override
        public Damage[] newArray(int size) {
            return new Damage[size];
        }
    };

    public Damage(String responsibleGuid, String targetGuid, int targetSlot, String resonatorId, boolean criticalHit, int damageAmount, boolean targetDestroyed) {
        this.responsibleGuid = responsibleGuid;
        this.targetGuid = targetGuid;
        this.targetSlot = targetSlot;
        this.resonatorId = resonatorId;
        this.criticalHit = criticalHit;
        this.damageAmount = damageAmount;
        this.targetDestroyed = targetDestroyed;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(responsibleGuid);
        dest.writeString(targetGuid);
        dest.writeInt(targetSlot);
        dest.writeString(resonatorId);
        dest.writeByte((byte) (criticalHit ? 1 : 0));
        dest.writeInt(damageAmount);
        dest.writeByte((byte) (targetDestroyed ? 1 : 0));
    }

    public boolean isCriticalHit() {
        return criticalHit;
    }

    public int getDamageAmount() {
        return damageAmount;
    }

    public String getResonatorId() {
        return resonatorId;
    }

    public String getResponsibleGuid() {
        return responsibleGuid;
    }

    public boolean isTargetDestroyed() {
        return targetDestroyed;
    }

    public String getTargetGuid() {
        return targetGuid;
    }

    public int getTargetSlot() {
        return targetSlot;
    }
}

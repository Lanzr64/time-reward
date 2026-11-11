package net.lanzr.time_reward.save;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

public class LZSavedData
extends SavedData {
    @Nullable
    public static LZSavedData INSTANCE;
    public static final int CONTAINER_SIZE = 360;
    public static final int SLOTS_PER_LEVEL = 10;
    public static final int DAYS_PER_LEVEL = 30;
    public static final String SAVE_DATA_NAME = "timeReward-SavedData";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_NAME_RECORD = "Records";
    public static boolean SET_DONE;
    public final SimpleContainer rewardBox = new SimpleContainer(360){

        public void setChanged() {
            super.setChanged();
            if (INSTANCE != null) {
                INSTANCE.setDirty();
            }
        }
    };

    public LZSavedData() {
        SET_DONE = false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public LZSavedData(CompoundTag nbt) {
        this();
        if (nbt.contains(TAG_ITEMS, 9)) {
            SimpleContainer simpleContainer = this.rewardBox;
            synchronized (simpleContainer) {
                this.rewardBox.fromTag(nbt.getList(TAG_ITEMS, 10));
            }
        }
        if (nbt.contains(TAG_NAME_RECORD)) {
            SET_DONE = nbt.getBoolean(TAG_NAME_RECORD);
        }
    }

    public static void setDone(boolean flag) {
        SET_DONE = flag;
        if (INSTANCE != null) {
            INSTANCE.setDirty();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public CompoundTag save(CompoundTag nbt) {
        SimpleContainer simpleContainer = this.rewardBox;
        synchronized (simpleContainer) {
            nbt.put(TAG_ITEMS, (Tag)this.rewardBox.createTag());
        }
        nbt.putBoolean(TAG_NAME_RECORD, SET_DONE);
        return nbt;
    }

    public static void setInstance(LZSavedData instance) {
        INSTANCE = instance;
    }

    public static SimpleContainer getRewardBox() {
        assert (INSTANCE != null);
        return LZSavedData.INSTANCE.rewardBox;
    }

    static {
        SET_DONE = false;
    }
}

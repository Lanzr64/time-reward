package net.lanzr.time_reward.save;

import net.lanzr.time_reward.api.PlayerRecords;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.awt.desktop.PrintFilesEvent;
import java.awt.geom.FlatteningPathIterator;

public class LZSavedData extends SavedData {
    @Nullable
    public static LZSavedData INSTANCE;
    public static final int CONTAINER_SIZE = 27;
    public static final String SAVE_DATA_NAME = "timeReward-SavedData";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_NAME_RECORD = "Records";
    public static boolean SET_DONE = false;

    public final SimpleContainer rewardBox = new SimpleContainer(CONTAINER_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            if(INSTANCE != null) {
                INSTANCE.setDirty();
            }
        }
    };

    public LZSavedData() {
        super();
    }

    public LZSavedData(CompoundTag nbt) {
        this();
        if(nbt.contains(TAG_ITEMS, Tag.TAG_LIST)) {
            synchronized (this.rewardBox) {
                this.rewardBox.fromTag(nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
            }
        }
        if (nbt.contains(TAG_NAME_RECORD)) {
            SET_DONE =nbt.getBoolean(TAG_NAME_RECORD);
        }
    }
    public static void setDone(boolean flag) {
        SET_DONE = flag;
        if (INSTANCE != null) {
            INSTANCE.setDirty();
        }
    }
    @Override
    public CompoundTag save(CompoundTag nbt) {
        synchronized (this.rewardBox) {
            nbt.put(TAG_ITEMS, this.rewardBox.createTag());
        }
        nbt.putBoolean(TAG_NAME_RECORD, SET_DONE);

//        synchronized (this.records) {
//            nbt.put(TAG_NAME_RECORD, this.records.getTags());
////            ListTag listTag = this.records.getTags();
////            if (listTag != null) {
////                nbt.put(TAG_NAME_RECORD, listTag);
////            }
//        }

        return nbt;
    }
    public static void setInstance(LZSavedData instance) {
        INSTANCE = instance;
    }

    public static SimpleContainer getRewardBox() {
        assert INSTANCE != null;
        return INSTANCE.rewardBox;
    }
}

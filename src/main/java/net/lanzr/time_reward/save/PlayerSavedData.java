package net.lanzr.time_reward.save;

import net.lanzr.time_reward.api.PlayerRecords;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

public class PlayerSavedData extends SavedData {
    @Nullable
    public static PlayerSavedData INSTANCE;
    public static final int CONTAINER_SIZE = 27;
    public static final String SAVE_DATA_NAME_PREFIX = "time-reward/";
    private static final String TAG_ITEMS = "Items";

    private static final String TAG_NAME_RECORD = "Records";

    public final PlayerRecords records = new PlayerRecords() {
        public void setChanged() {
            INSTANCE.setDirty();
        }
    };

    public final SimpleContainer rewardBox = new SimpleContainer(CONTAINER_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            if(INSTANCE != null) {
                INSTANCE.setDirty();
            }
        }
    };

    public PlayerSavedData() {
        super();
    }

    public PlayerSavedData(CompoundTag nbt) {
        this();
        if(nbt.contains(TAG_ITEMS, Tag.TAG_LIST)) {
            synchronized (this.rewardBox) {
                this.rewardBox.fromTag(nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
            }
        }
        synchronized (this.records) {
            if (nbt.contains(TAG_NAME_RECORD, Tag.TAG_LIST)) {
                this.records.load(nbt.getList(TAG_NAME_RECORD, Tag.TAG_COMPOUND));
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        synchronized (this.rewardBox) {
            nbt.put(TAG_ITEMS, this.rewardBox.createTag());
        }
        synchronized (this.records) {
            nbt.put(TAG_NAME_RECORD, this.records.getTags());
//            ListTag listTag = this.records.getTags();
//            if (listTag != null) {
//                nbt.put(TAG_NAME_RECORD, listTag);
//            }
        }
        return nbt;
    }
    public static void setInstance(PlayerSavedData instance) {
        INSTANCE = instance;
    }

    public static PlayerRecords getRecords() {
        assert INSTANCE != null;
        return INSTANCE.records;
    }

    public static SimpleContainer getRewardBox() {
        assert INSTANCE != null;
        return INSTANCE.rewardBox;
    }
}

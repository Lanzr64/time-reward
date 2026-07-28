package net.lanzr.time_reward.save;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;

public class LZSavedData extends SavedData {
    @Nullable
    public static LZSavedData INSTANCE;
    /** 奖励库存总容量。 */
    public static final int CONTAINER_SIZE = 360;
    /** 每个等级对应的槽位数。 */
    public static final int SLOTS_PER_LEVEL = 10;
    /** 每级所需天数。 */
    public static final int DAYS_PER_LEVEL = 30;
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


    public LZSavedData(CompoundTag nbt, HolderLookup.Provider provider) {
        this();
        if(nbt.contains(TAG_ITEMS, Tag.TAG_LIST)) {
            synchronized (this.rewardBox) {
                this.rewardBox.fromTag(nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND), provider);
            }
        }
        if (nbt.contains(TAG_NAME_RECORD)) {
            SET_DONE =nbt.getBoolean(TAG_NAME_RECORD);
        }
    }

    public static final Factory<LZSavedData> FACTORY = new Factory<>(
            LZSavedData::new, // constructor
            (tag, provider) -> { // deserializer
                // 反序列化逻辑
                return new LZSavedData(tag, provider);
            }
    );

    public static void setDone(boolean flag) {
        SET_DONE = flag;
        if (INSTANCE != null) {
            INSTANCE.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt,HolderLookup.Provider pRegistries) {
        synchronized (this.rewardBox) {
            nbt.put(TAG_ITEMS, this.rewardBox.createTag(pRegistries));
        }
        nbt.putBoolean(TAG_NAME_RECORD, SET_DONE);
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

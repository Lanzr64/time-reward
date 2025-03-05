package net.lanzr.time_reward.api;

import net.lanzr.time_reward.TimeReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class RewardTag {
    private CompoundTag mTag;
    private ServerPlayer mPlayer;
    public String GET_REWARD_ALIAS ="got_reward";
    public RewardTag(ServerPlayer player) {
        CompoundTag pTag = player.getPersistentData();
        mTag  = pTag.getCompound(TimeReward.MODID);
        mPlayer = player;
        if(!pTag.contains(TimeReward.MODID)) {
            pTag.put(TimeReward.MODID,mTag);
        }
        if(!mTag.contains(GET_REWARD_ALIAS)) {
            mTag.putInt(GET_REWARD_ALIAS,-1);
        }
    }
    public int getLevel() {
        return mTag.getInt(GET_REWARD_ALIAS);
    }
    public void setLevel(int rewardLevel) {
        mTag.putInt(GET_REWARD_ALIAS, rewardLevel);
    }
}

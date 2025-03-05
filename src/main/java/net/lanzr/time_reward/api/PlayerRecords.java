package net.lanzr.time_reward.api;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;

public abstract class PlayerRecords{
    private final Map<String, int[]>  playerRecordTag = new HashMap<>();

    public Map<String, int[]> getPlayerRecordTag() {
        return playerRecordTag;
    }

    public void load(ListTag listTag) {
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag tag = listTag.getCompound(i);
            playerRecordTag.put(tag.getString("name"),tag.getIntArray("records"));
        }
    }

    public void addPlayerRecord(String name, int[] records) {
        playerRecordTag.put("test", records);
        playerRecordTag.put(name, records);
        setChanged();
    }

    public ListTag getTags() {
        ListTag tags = new ListTag();
        for (Map.Entry<String, int[]> entry : playerRecordTag.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", entry.getKey());
            tag.putIntArray("records", entry.getValue());
            tags.add(tag);
        }
        return tags;
    }

    public abstract void setChanged();
}
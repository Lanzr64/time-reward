package net.lanzr.time_reward.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.util.Lazy;

public class KeybindHandler {
    private static final Lazy<KeyMapping> BACKPACK_KEY = Lazy.of(() -> new KeyMapping("key.time_reward.open_backpack", (IKeyConflictContext)KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, 66, "key.categories.time_reward"));

    public static KeyMapping getOpenBackpackKey() {
        return (KeyMapping)BACKPACK_KEY.get();
    }
}

package net.lanzr.time_reward.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

public class KeybindHandler {
    private static final Lazy<KeyMapping> BACKPACK_KEY = Lazy.of(() -> new KeyMapping(
            "key.time_reward.open_backpack",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.time_reward"
    ));

    public static KeyMapping getOpenBackpackKey() {
        return BACKPACK_KEY.get();
    }
}

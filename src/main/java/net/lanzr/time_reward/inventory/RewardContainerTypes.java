package net.lanzr.time_reward.inventory;

import net.lanzr.time_reward.TimeReward;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RewardContainerTypes {
    public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, TimeReward.MODID);
//    public static final RegistryObject<MenuType<RewardMenu_depreacted>> REWARD_MENU = CONTAINERS.register("reward_menu", () -> new MenuType<>(RewardMenu_depreacted::createTSTContainer));
}

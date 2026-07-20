package net.lanzr.time_reward.init;

import net.lanzr.time_reward.TimeReward;
import net.lanzr.time_reward.inventory.BackpackContainer;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 本模组的所有 MenuType 注册入口。
 *
 * <p>使用 NeoForge 的 {@link DeferredRegister} 机制将菜单类型注册到
 * {@link Registries#MENU} 注册表中。</p>
 */
public final class ModMenuTypes {
    private ModMenuTypes() {}

    /** {@link DeferredRegister} 实例，绑定 MENU 注册表 */
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TimeReward.MODID);

    /**
     * 背包容器菜单类型。
     *
     * <p>使用 {@code IMenuTypeExtension.create(BackpackContainer::new)} 构建，
     * 工厂方法接收容器ID、玩家背包和网络数据包，返回对应的 {@link BackpackContainer} 实例。</p>
     */
    public static final Supplier<MenuType<BackpackContainer>> BACKPACK =
            MENUS.register("backpack", () -> IMenuTypeExtension.create(BackpackContainer::new));

    /**
     * 获取已注册的 {@code MenuType<BackpackContainer>} 实例。
     *
     * @return 背包容器的 MenuType
     */
    public static MenuType<BackpackContainer> get() {
        return BACKPACK.get();
    }
}

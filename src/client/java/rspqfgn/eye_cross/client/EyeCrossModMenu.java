package rspqfgn.eye_cross.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.gui.screens.Screen;

/**
 * Mod Menu 集成：在模组菜单中为 eye-cross 提供配置界面入口。
 * 界面由 Cloth Config（AutoConfig）构建，保存即写回 config/eye-cross.json5。
 *
 * <p>仅在装了 Mod Menu 时被其加载（entrypoint "modmenu"）；未安装则本类不会被实例化，
 * 不产生任何空闲开销（fabric.mod.json 以 "suggests" 声明 modmenu，非强制依赖）。
 */
public final class EyeCrossModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfigClient.getConfigScreen(
                EyeCrossConfig.EyeCrossConfigData.class, (Screen) parent).get();
    }
}
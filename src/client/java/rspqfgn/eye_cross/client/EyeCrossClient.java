package rspqfgn.eye_cross.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * eye-cross：纯客户端模组。通过记录末影之眼的飞行轨迹，
 * 拟合两条以上的一次函数并在 XZ 平面求交，从而定位末地要塞。
 */
public class EyeCrossClient implements ClientModInitializer {
    public static final String MOD_ID = "eye-cross";

    @Override
    public void onInitializeClient() {
        EyeTracker.register();
        EyeCrossHud.register();
        EyeCrossWorldRenderer.register();
        EyeCrossCommands.register();
    }
}

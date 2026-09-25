package rspqfgn.eye_cross.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * HUD：在屏幕指定位置（左上/右上/左下/右下，/eyecross hudpos 切换）显示一条半透明小条；
 * 距离按玩家当前位置逐帧实时计算。可通过 /eyecross hud 开关。
 */
public final class EyeCrossHud {
    private EyeCrossHud() {
    }

    private static final int BACKDROP = 0x90000000;
    private static final int PADDING = 2;

    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath("eye-cross", "hud");
        HudElementRegistry.addLast(id, EyeCrossHud::extract);
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (!EyeCrossConfig.hudEnabled || !EyeCrossState.hudVisible) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        MutableComponent text;
        int textColor;
        EyeCrossState.Solution s = EyeCrossState.solution;
        double distance;
        if (s != null) {
            // 距离按玩家当前位置实时计算，走动时同步刷新
            distance = Math.hypot(s.x() - client.player.getX(), s.z() - client.player.getZ());
            text = EyeCrossText.tr("eyecross.hud.solution",
                    EyeCrossText.f1(s.x()), EyeCrossText.f1(s.z()),
                    EyeCrossText.f0(distance), EyeCrossText.f1(s.rmsError()));
            textColor = 0xFF55FFFF;
        } else if (EyeCrossState.parallelWarning) {
            text = EyeCrossText.tr("eyecross.hud.parallel");
            textColor = 0xFFFF5555;
        } else if (EyeCrossState.estimate != null) {
            StrongholdSolver.SingleThrowEstimate est = EyeCrossState.estimate;
            distance = Math.hypot(est.x() - client.player.getX(), est.z() - client.player.getZ());
            text = EyeCrossText.tr("eyecross.hud.estimate",
                    est.ringIndex(), EyeCrossText.f1(est.x()), EyeCrossText.f1(est.z()),
                    EyeCrossText.f0(distance), EyeCrossText.f0(est.errorRadius()));
            textColor = 0xFFFFAA00;
        } else {
            return;
        }

        Font font = client.font;
        int width = font.width(text);
        int x;
        int y;
        switch (EyeCrossState.hudPosition) {
            case TOP_RIGHT:
                x = g.guiWidth() - width - 6;
                y = 6;
                break;
            case BOTTOM_LEFT:
                x = 6;
                y = g.guiHeight() - font.lineHeight - 6;
                break;
            case BOTTOM_RIGHT:
                x = g.guiWidth() - width - 6;
                y = g.guiHeight() - font.lineHeight - 6;
                break;
            default:
                x = 6;
                y = 6;
                break;
        }
        g.fill(x - PADDING, y - PADDING, x + width + PADDING, y + font.lineHeight + PADDING, BACKDROP);
        g.text(font, text, x, y, textColor, true);
    }
}

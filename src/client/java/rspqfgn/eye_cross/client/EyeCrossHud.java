package rspqfgn.eye_cross.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * HUD：仅在已求出结果（或存在近平行警告）时，于屏幕左上角显示一条半透明小条。
 * 可通过 /eyecross hud 开关。
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
        if (!EyeCrossState.hudVisible) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        MutableComponent text;
        int textColor;
        EyeCrossState.Solution s = EyeCrossState.solution;
        if (s != null) {
            text = EyeCrossText.tr("eyecross.hud.solution",
                    EyeCrossText.f1(s.x()), EyeCrossText.f1(s.z()),
                    EyeCrossText.f0(s.distanceFromPlayer()), EyeCrossText.f1(s.rmsError()));
            textColor = 0xFF55FFFF;
        } else if (EyeCrossState.parallelWarning) {
            text = EyeCrossText.tr("eyecross.hud.parallel");
            textColor = 0xFFFF5555;
        } else {
            return;
        }

        Font font = client.font;
        int width = font.width(text);
        int x = 6;
        int y = 6;
        g.fill(x - PADDING, y - PADDING, x + width + PADDING, y + font.lineHeight + PADDING, BACKDROP);
        g.text(font, text, x, y, textColor, true);
    }
}

package rspqfgn.eye_cross.client;

import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * 文案工具：数字格式化与「点击传送」组件。全部文案走翻译 key，由语言文件提供。
 */
public final class EyeCrossText {
    private EyeCrossText() {
    }

    public static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public static String f3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    public static String f0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 生成形如原版 /locate 的「点击传送」片段：点击即执行 /tp @s X ~ Z（保持当前高度）。
     */
    public static MutableComponent teleport(double x, double z) {
        String command = String.format(Locale.ROOT, "/tp @s %s ~ %s", f1(x), f1(z));
        MutableComponent hint = tr("eyecross.chat.teleport").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(
                        tr("eyecross.chat.teleport_hover", Component.literal(command)))));
        return hint;
    }
}

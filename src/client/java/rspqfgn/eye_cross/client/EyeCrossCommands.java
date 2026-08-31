package rspqfgn.eye_cross.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * /eyecross 客户端命令：help / status / reset / hud。
 */
public final class EyeCrossCommands {
    private EyeCrossCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
                ClientCommands.literal("eyecross")
                        .then(ClientCommands.literal("help").executes(ctx -> {
                            sendHelp(ctx.getSource());
                            return 1;
                        }))
                        .then(ClientCommands.literal("status").executes(ctx -> {
                            reportStatus(ctx.getSource());
                            return 1;
                        }))
                        .then(ClientCommands.literal("reset").executes(ctx -> {
                            EyeCrossState.reset();
                            feedback(ctx.getSource(),
                                    EyeCrossText.tr("eyecross.chat.reset").withStyle(ChatFormatting.GREEN));
                            return 1;
                        }))
                        .then(ClientCommands.literal("hud").executes(ctx -> {
                            EyeCrossState.hudVisible = !EyeCrossState.hudVisible;
                            feedback(ctx.getSource(), EyeCrossText.tr(EyeCrossState.hudVisible
                                    ? "eyecross.chat.hud_enabled"
                                    : "eyecross.chat.hud_disabled").withStyle(ChatFormatting.GREEN));
                            return 1;
                        }))));
    }

    private static void sendHelp(FabricClientCommandSource source) {
        feedback(source, EyeCrossText.tr("eyecross.help.header").withStyle(ChatFormatting.AQUA));
        feedback(source, EyeCrossText.tr("eyecross.help.step1").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.step2").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.step3").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.more", EyeCrossState.MAX_LINES)
                .withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.usage").withStyle(ChatFormatting.GRAY));
    }

    private static void reportStatus(FabricClientCommandSource source) {
        int n = EyeCrossState.LINES.size();
        if (n == 0) {
            feedback(source, EyeCrossText.tr("eyecross.chat.no_trails").withStyle(ChatFormatting.GRAY));
            return;
        }
        feedback(source, EyeCrossText.tr("eyecross.chat.trail_list", n).withStyle(ChatFormatting.GREEN));
        for (int i = 0; i < n; i++) {
            EyeCrossState.FitLine l = EyeCrossState.LINES.get(i);
            feedback(source, EyeCrossText.tr("eyecross.chat.trail_entry",
                    i + 1, EyeCrossText.f1(l.px()), EyeCrossText.f1(l.pz()),
                    EyeCrossText.f3(l.dx()), EyeCrossText.f3(l.dz())).withStyle(ChatFormatting.GRAY));
        }
        EyeCrossState.Solution s = EyeCrossState.solution;
        if (s != null) {
            feedback(source, EyeCrossText.tr("eyecross.chat.solution",
                    EyeCrossText.f1(s.x()), EyeCrossText.f1(s.z()), EyeCrossText.f0(s.distanceFromPlayer()),
                    s.lineCount(), EyeCrossText.f1(s.rmsError())).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" "))
                    .append(EyeCrossText.teleport(s.x(), s.z())));
        } else if (EyeCrossState.parallelWarning) {
            feedback(source, EyeCrossText.tr("eyecross.chat.parallel").withStyle(ChatFormatting.RED));
        } else {
            feedback(source, EyeCrossText.tr("eyecross.chat.need_two").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void feedback(FabricClientCommandSource source, MutableComponent text) {
        source.sendFeedback(text);
    }
}

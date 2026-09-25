package rspqfgn.eye_cross.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import rspqfgn.eye_cross.client.xaero.XaeroSync;

/**
 * /eyecross 客户端命令：help / status / rings / reset / hud / hudpos。
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
                        .then(ClientCommands.literal("rings").executes(ctx -> {
                            reportRings(ctx.getSource());
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
                        }))
                        .then(ClientCommands.literal("hudpos")
                                .then(ClientCommands.literal("topleft").executes(ctx -> setHudPos(ctx.getSource(),
                                        EyeCrossState.HudPosition.TOP_LEFT)))
                                .then(ClientCommands.literal("topright").executes(ctx -> setHudPos(ctx.getSource(),
                                        EyeCrossState.HudPosition.TOP_RIGHT)))
                                .then(ClientCommands.literal("bottomleft").executes(ctx -> setHudPos(ctx.getSource(),
                                        EyeCrossState.HudPosition.BOTTOM_LEFT)))
                                .then(ClientCommands.literal("bottomright").executes(ctx -> setHudPos(ctx.getSource(),
                                        EyeCrossState.HudPosition.BOTTOM_RIGHT))))
                        .then(ClientCommands.literal("portal").executes(ctx -> {
                            markPortalWaypoint(ctx.getSource());
                            return 1;
                        }))
                        .then(ClientCommands.literal("config")
                                .then(ClientCommands.literal("reload").executes(ctx -> {
                                    EyeCrossConfig.reload();
                                    feedback(ctx.getSource(),
                                            EyeCrossText.tr("eyecross.chat.config_reloaded").withStyle(ChatFormatting.GREEN));
                                    return 1;
                                }))
                                .then(ClientCommands.literal("path").executes(ctx -> {
                                    feedback(ctx.getSource(), EyeCrossText.tr("eyecross.chat.config_path",
                                            Component.literal(EyeCrossConfig.getConfigPath().toString()))
                                            .withStyle(ChatFormatting.GRAY));
                                    return 1;
                                })))));
    }

    private static void sendHelp(FabricClientCommandSource source) {
        feedback(source, EyeCrossText.tr("eyecross.help.header").withStyle(ChatFormatting.AQUA));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_help").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_status").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_rings").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_reset").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_hud").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_hudpos").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_portal").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.cmd_config").withStyle(ChatFormatting.GRAY));
        feedback(source, EyeCrossText.tr("eyecross.help.footer").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * /eyecross portal：把 Xaero 小地图上的 Stronghold(EC) 路径点移到末地传送门中心。
     * 尚未检测到完整传送门时提示；Xaero 缺失/版本不足时由 XaeroSync 静默降级并给出说明。
     */
    private static void markPortalWaypoint(FabricClientCommandSource source) {
        EyeCrossState.CompletePortal portal = EyeCrossState.completePortal;
        if (portal == null) {
            feedback(source, EyeCrossText.tr("eyecross.chat.portal_not_detected").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!EyeCrossConfig.xaeroWaypoints) {
            feedback(source, EyeCrossText.tr("eyecross.chat.waypoint_disabled").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (EyeCrossState.dimension == null) {
            feedback(source, EyeCrossText.tr("eyecross.chat.portal_dimension_missing").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!XaeroSync.isAvailable()) {
            feedback(source, EyeCrossText.tr("eyecross.chat.portal_xaero_unavailable").withStyle(ChatFormatting.YELLOW));
            return;
        }
        XaeroSync.pushPortal(portal.centerX(), portal.centerY(), portal.centerZ(), EyeCrossState.dimension);
        feedback(source, EyeCrossText.tr("eyecross.chat.portal_waypoint_set",
                EyeCrossText.f1(portal.centerX()), EyeCrossText.f1(portal.centerZ())).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static int setHudPos(FabricClientCommandSource source, EyeCrossState.HudPosition pos) {
        EyeCrossState.hudPosition = pos;
        feedback(source, EyeCrossText.tr("eyecross.chat.hudpos",
                EyeCrossText.tr("eyecross.hudpos." + pos.key())).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static void reportRings(FabricClientCommandSource source) {
        feedback(source, EyeCrossText.tr("eyecross.chat.rings_header").withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < StrongholdRings.RINGS.size(); i++) {
            StrongholdRings.Ring r = StrongholdRings.RINGS.get(i);
            feedback(source, EyeCrossText.tr("eyecross.chat.ring_entry",
                    i + 1, r.count(), EyeCrossText.f0(r.minDist()), EyeCrossText.f0(r.maxDist()))
                    .withStyle(ChatFormatting.GRAY));
        }
        feedback(source, EyeCrossText.tr("eyecross.chat.ring_total", StrongholdRings.TOTAL)
                .withStyle(ChatFormatting.GRAY));
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
        } else if (EyeCrossState.estimate != null) {
            StrongholdSolver.SingleThrowEstimate est = EyeCrossState.estimate;
            feedback(source, EyeCrossText
                    .tr("eyecross.chat.single_estimate",
                            est.ringIndex(), est.ringCount(), EyeCrossText.f1(est.x()),
                            EyeCrossText.f1(est.z()), EyeCrossText.f0(est.distanceFromPlayer()),
                            EyeCrossText.f0(est.errorRadius()))
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" "))
                    .append(EyeCrossText.teleport(est.x(), est.z())));
        } else {
            feedback(source, EyeCrossText.tr("eyecross.chat.need_two").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void feedback(FabricClientCommandSource source, MutableComponent text) {
        source.sendFeedback(text);
    }
}

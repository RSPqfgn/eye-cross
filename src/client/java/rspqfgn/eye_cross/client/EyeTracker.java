package rspqfgn.eye_cross.client;

import java.util.HashSet;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EyeOfEnder;

/**
 * 每个客户端 tick 巡检世界里的末影之眼实体，记录其飞行轨迹；
 * 实体消失（破碎/掉落/飞出渲染距离）时结束采样并拟合直线、更新解。
 */
public final class EyeTracker {
    private EyeTracker() {
    }

    private static final int MIN_SAMPLES = 5;
    private static final double MIN_PATH_LENGTH = 10.0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EyeTracker::onEndClientTick);
    }

    private static void onEndClientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            // 不在世界里（主菜单/断线）：丢弃未完成的采样，保留历史
            EyeCrossState.clearTransient();
            return;
        }
        if (!level.dimension().equals(EyeCrossState.dimension)) {
            EyeCrossState.onDimensionChanged(level.dimension());
        }

        HashSet<Integer> present = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof EyeOfEnder) {
                present.add(entity.getId());
                EyeCrossState.ACTIVE_TRAILS
                        .computeIfAbsent(entity.getId(), id -> new EyeCrossState.Trail())
                        .addSample(entity.getX(), entity.getY(), entity.getZ());
            }
        }

        for (Integer id : new HashSet<>(EyeCrossState.ACTIVE_TRAILS.keySet())) {
            if (!present.contains(id)) {
                EyeCrossState.Trail trail = EyeCrossState.ACTIVE_TRAILS.remove(id);
                finalizeTrail(client, trail);
            }
        }
    }

    private static void finalizeTrail(Minecraft client, EyeCrossState.Trail trail) {
        if (trail == null || client.player == null) {
            return;
        }
        if (trail.size() < MIN_SAMPLES || trail.pathLengthXZ() < MIN_PATH_LENGTH) {
            message(client, EyeCrossText.tr("eyecross.chat.trail_too_short").withStyle(ChatFormatting.GRAY));
            return;
        }
        EyeCrossState.FitLine line = StrongholdSolver.fitTrail(trail.samples());
        if (line == null) {
            message(client, EyeCrossText.tr("eyecross.chat.trail_degenerate").withStyle(ChatFormatting.RED));
            return;
        }
        EyeCrossState.addLine(line);
        message(client, EyeCrossText.tr("eyecross.chat.line_recorded",
                EyeCrossState.LINES.size()).withStyle(ChatFormatting.GREEN));

        if (EyeCrossState.LINES.size() >= 2) {
            StrongholdSolver.SolvedPoint s = StrongholdSolver.intersect(
                    EyeCrossState.LINES, client.player.getX(), client.player.getZ());
            if (s == null) {
                EyeCrossState.solution = null;
                EyeCrossState.parallelWarning = true;
                message(client, EyeCrossText.tr("eyecross.chat.parallel").withStyle(ChatFormatting.RED));
                return;
            }
            EyeCrossState.parallelWarning = false;
            EyeCrossState.solution = new EyeCrossState.Solution(s.x(), s.z(), s.rmsError(), s.maxError(),
                    s.distanceFromPlayer(), EyeCrossState.LINES.size());

            MutableComponent msg = EyeCrossText
                    .tr("eyecross.chat.solution",
                            EyeCrossText.f1(s.x()), EyeCrossText.f1(s.z()), EyeCrossText.f0(s.distanceFromPlayer()),
                            EyeCrossState.LINES.size(), EyeCrossText.f1(s.rmsError()))
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" "))
                    .append(EyeCrossText.teleport(s.x(), s.z()));
            message(client, msg);
            if (s.maxError() > 5.0) {
                message(client, EyeCrossText.tr("eyecross.chat.high_residual").withStyle(ChatFormatting.YELLOW));
            }
        } else {
            message(client, EyeCrossText.tr("eyecross.chat.need_more").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void message(Minecraft client, MutableComponent text) {
        if (client.player != null) {
            client.player.sendSystemMessage(text);
        }
    }
}

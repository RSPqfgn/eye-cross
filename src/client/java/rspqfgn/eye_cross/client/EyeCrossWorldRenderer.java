package rspqfgn.eye_cross.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 世界内渲染：交点处一根信标式光柱标记 + 出发方向箭头。
 * 走原版 gizmo 通道，全部 setAlwaysOnTop 穿墙可见。
 */
public final class EyeCrossWorldRenderer {
    private EyeCrossWorldRenderer() {
    }

    private static final int BEAM_COLOR = 0xFFFFAA00;
    private static final int BEAM_FILL = 0x50FFAA00;
    private static final int CORE_COLOR = 0xFFFFD24D;
    private static final int TEXT_COLOR = 0xFFFFAA00;
    private static final double BEAM_HALF_HEIGHT = 256.0;
    private static final double BEAM_HALF_WIDTH = 0.35;
    private static final double ARROW_MAX_DISTANCE = 24.0;

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            EyeCrossState.Solution s = EyeCrossState.solution;
            if (s == null) {
                return;
            }
            // 打开原版每帧 gizmo 收集作用域，期间 Gizmos.xxx 的图形会被本帧渲染
            try (var ignored = context.levelRenderer().collectPerFrameGizmos()) {
                Vec3 cam = context.levelState().cameraRenderState.pos;
                double bottom = cam.y - BEAM_HALF_HEIGHT;
                double top = cam.y + BEAM_HALF_HEIGHT;

                // 半透明填充光柱 + 高亮中心线，远处也能一眼看到
                AABB beam = new AABB(s.x() - BEAM_HALF_WIDTH, bottom, s.z() - BEAM_HALF_WIDTH,
                        s.x() + BEAM_HALF_WIDTH, top, s.z() + BEAM_HALF_WIDTH);
                Gizmos.cuboid(beam, GizmoStyle.strokeAndFill(BEAM_COLOR, 2.0F, BEAM_FILL)).setAlwaysOnTop();
                Gizmos.line(new Vec3(s.x(), bottom, s.z()), new Vec3(s.x(), top, s.z()), CORE_COLOR, 3.0F)
                        .setAlwaysOnTop();
                Gizmos.billboardText(String.format("X=%s Z=%s", EyeCrossText.f0(s.x()), EyeCrossText.f0(s.z())),
                        new Vec3(s.x(), top, s.z()), TextGizmo.Style.forColorAndCentered(TEXT_COLOR))
                        .setAlwaysOnTop();

                // 目标超出视距时，从玩家眼睛处画一支指向要塞的箭头
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    Vec3 eye = client.player.getEyePosition();
                    Vec3 toTarget = new Vec3(s.x() - eye.x, 0, s.z() - eye.z);
                    double distance = toTarget.length();
                    if (distance > ARROW_MAX_DISTANCE) {
                        Vec3 dir = toTarget.normalize();
                        Gizmos.arrow(eye.add(dir.scale(2.0)), eye.add(dir.scale(12.0)), BEAM_COLOR, 2.0F)
                                .setAlwaysOnTop();
                    }
                }
            }
        });
    }
}

package rspqfgn.eye_cross.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 世界内渲染：精确交点处一根信标式光柱标记；
 * 只有单次投掷估测时，用水平圆环把「大致范围」（误差带半径）标出来。
 * 两种情况都从玩家眼前画一支指向目标的箭头。走原版 gizmo 通道，全部 setAlwaysOnTop 穿墙可见。
 */
public final class EyeCrossWorldRenderer {
    private EyeCrossWorldRenderer() {
    }

    private static final int BEAM_COLOR = 0xFFFFAA00;
    private static final int BEAM_FILL = 0x50FFAA00;
    private static final int CORE_COLOR = 0xFFFFD24D;
    private static final int TEXT_COLOR = 0xFFFFAA00;
    private static final int RANGE_COLOR = 0xFFFFAA00;
    private static final double BEAM_HALF_HEIGHT = 256.0;
    private static final double BEAM_HALF_WIDTH = 0.35;
    private static final double ARROW_MAX_DISTANCE = 24.0;
    /** 范围圆环的折线段数（水平圆用折线逼近）。 */
    private static final int RANGE_SEGMENTS = 24;
    /** 估测范围过小时的最小显示半径（格），避免缩成一个点。 */
    private static final double RANGE_MIN_RADIUS = 8.0;

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            EyeCrossState.Solution s = EyeCrossState.solution;
            StrongholdSolver.SingleThrowEstimate est = EyeCrossState.estimate;
            if (s == null && est == null) {
                return;
            }
            // 打开原版每帧 gizmo 收集作用域，期间 Gizmos.xxx 的图形会被本帧渲染
            try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                Vec3 cam = context.levelState().cameraRenderState.pos;
                Minecraft client = Minecraft.getInstance();
                if (s != null) {
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
                    drawArrow(client, s.x(), s.z());
                } else {
                    // 单次投掷估测：水平圆环标出大致范围（半径 = 误差带）
                    double radius = Math.max(est.errorRadius(), RANGE_MIN_RADIUS);
                    double y = cam.y;
                    drawRangeCircle(est.x(), est.z(), radius, y);
                    Gizmos.point(new Vec3(est.x(), y, est.z()), RANGE_COLOR, 3.0F).setAlwaysOnTop();
                    Gizmos.billboardText(String.format("X≈%s Z≈%s ±%s",
                            EyeCrossText.f0(est.x()), EyeCrossText.f0(est.z()), EyeCrossText.f0(est.errorRadius())),
                            new Vec3(est.x(), y + 1.0, est.z()), TextGizmo.Style.forColorAndCentered(TEXT_COLOR))
                            .setAlwaysOnTop();
                    drawArrow(client, est.x(), est.z());
                }
            }
        });
    }

    /** 目标超出视距时，从玩家眼睛处画一支指向目标的箭头。 */
    private static void drawArrow(Minecraft client, double targetX, double targetZ) {
        if (client.player == null) {
            return;
        }
        Vec3 eye = client.player.getEyePosition();
        Vec3 toTarget = new Vec3(targetX - eye.x, 0, targetZ - eye.z);
        double distance = toTarget.length();
        if (distance > ARROW_MAX_DISTANCE) {
            Vec3 dir = toTarget.normalize();
            Gizmos.arrow(eye.add(dir.scale(2.0)), eye.add(dir.scale(12.0)), BEAM_COLOR, 2.0F)
                    .setAlwaysOnTop();
        }
    }

    /** 水平（XZ 平面）圆环：以 (cx, cz) 为圆心、radius 为半径，画在高度 y。 */
    private static void drawRangeCircle(double cx, double cz, double radius, double y) {
        for (int i = 0; i < RANGE_SEGMENTS; i++) {
            double a0 = 2 * Math.PI * i / RANGE_SEGMENTS;
            double a1 = 2 * Math.PI * (i + 1) / RANGE_SEGMENTS;
            Gizmos.line(new Vec3(cx + radius * Math.cos(a0), y, cz + radius * Math.sin(a0)),
                    new Vec3(cx + radius * Math.cos(a1), y, cz + radius * Math.sin(a1)),
                    RANGE_COLOR, 2.0F)
                    .setAlwaysOnTop();
        }
    }
}
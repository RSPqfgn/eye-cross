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

    private static final int BEAM_FILL = 0x50FFAA00;
    private static final int CORE_COLOR = 0xFFFFD24D;
    private static final int TEXT_COLOR = 0xFFFFAA00;
    private static final int RANGE_COLOR = 0xFFFFAA00;
    private static final double BEAM_HALF_HEIGHT = 256.0;
    private static final double ARROW_MAX_DISTANCE = 24.0;
    /** 范围圆环的折线段数（水平圆用折线逼近）。 */
    private static final int RANGE_SEGMENTS = 24;
    /** 估测范围过小时的最小显示半径（格），避免缩成一个点。 */
    private static final double RANGE_MIN_RADIUS = 8.0;

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            // 单个 gizmo 收集作用域里画全部内容：末地传送门方框 + 传送门中心光柱 + 光束/估测圈
            try (var ignored = context.levelRenderer().collectPerFrameGizmos()) {
                Vec3 cam = context.levelState().cameraRenderState.pos;
                drawPortalBlocks();
                drawPortalCenterBeam(cam.y);

                EyeCrossState.Solution s = EyeCrossState.solution;
                StrongholdSolver.SingleThrowEstimate est = EyeCrossState.estimate;
                if (s == null && est == null) {
                    return;
                }
                Minecraft client = Minecraft.getInstance();
                if (s != null) {
                    // 垂直方向硬截断在 [beamMinY, beamMaxY]，避免光柱画进世界外
                    double bottom = Math.max(cam.y - BEAM_HALF_HEIGHT, EyeCrossConfig.beamMinY);
                    double top = Math.min(cam.y + BEAM_HALF_HEIGHT, EyeCrossConfig.beamMaxY);

                    // 半透明填充光柱 + 高亮中心线，远处也能一眼看到
                    double halfWidth = EyeCrossConfig.beamHalfWidth;
                    AABB beam = new AABB(s.x() - halfWidth, bottom, s.z() - halfWidth,
                            s.x() + halfWidth, top, s.z() + halfWidth);
                    Gizmos.cuboid(beam,
                            GizmoStyle.strokeAndFill(EyeCrossConfig.solutionBeamColor, 2.0F, BEAM_FILL)).setAlwaysOnTop();
                    Gizmos.line(new Vec3(s.x(), bottom, s.z()), new Vec3(s.x(), top, s.z()), CORE_COLOR, 3.0F)
                            .setAlwaysOnTop();
                    Gizmos.billboardText(String.format("X=%s Z=%s", EyeCrossText.f0(s.x()), EyeCrossText.f0(s.z())),
                            new Vec3(s.x(), top, s.z()), TextGizmo.Style.forColorAndCentered(TEXT_COLOR))
                            .setAlwaysOnTop();
                    drawArrow(client, s.x(), s.z());
                } else if (EyeCrossConfig.estimateRing) {
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
        if (client.player == null || !EyeCrossConfig.directionArrow) {
            return;
        }
        Vec3 eye = client.player.getEyePosition();
        Vec3 toTarget = new Vec3(targetX - eye.x, 0, targetZ - eye.z);
        double distance = toTarget.length();
        if (distance > ARROW_MAX_DISTANCE) {
            Vec3 dir = toTarget.normalize();
            Gizmos.arrow(eye.add(dir.scale(2.0)), eye.add(dir.scale(12.0)), EyeCrossConfig.solutionBeamColor, 2.0F)
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

    // ---- 末地传送门方框标记 ----

    private static final int PORTAL_FILL = 0x40808080;
    /** 方框比整方块略放大一点，保证边缘可见且不与方块表面完全重叠。 */
    private static final double PORTAL_BOX_PADDING = 0.06;

    // ---- 传送门中心光柱 ----

    private static final int PORTAL_BEAM_FILL = 0x40AA00FF;
    private static final int PORTAL_BEAM_CORE = 0xFFB266FF;
    private static final int PORTAL_TEXT_COLOR = 0xFFB266FF;
    /** 光柱半高（格），与要塞光束一致，够高到远处可见。 */
    private static final double PORTAL_BEAM_HALF_HEIGHT = 256.0;

    /**
     * 逐块绘制传送门相关方块的可透视方框（每个方块一格）。
     * 由调用方保证已在 collectPerFrameGizmos 作用域内；传送门总开关关闭或没有扫描结果时什么都不画。
     */
    private static void drawPortalBlocks() {
        if (!EyeCrossConfig.portalEnabled) {
            return;
        }
        java.util.List<EyeCrossState.PortalBlock> blocks = EyeCrossState.portalBlocks;
        if (blocks.isEmpty()) {
            return;
        }
        for (EyeCrossState.PortalBlock b : blocks) {
            int color = switch (b.kind()) {
                case 1 -> EyeCrossConfig.portalFrameHasEyeColor;
                case 2 -> EyeCrossConfig.portalBlockColor;
                default -> EyeCrossConfig.portalFrameNoEyeColor;
            };
            AABB box = new AABB(
                    b.x() - PORTAL_BOX_PADDING, b.y() - PORTAL_BOX_PADDING, b.z() - PORTAL_BOX_PADDING,
                    b.x() + 1.0 + PORTAL_BOX_PADDING, b.y() + 1.0 + PORTAL_BOX_PADDING,
                    b.z() + 1.0 + PORTAL_BOX_PADDING);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, 2.0F, PORTAL_FILL)).setAlwaysOnTop();
        }
    }

    /**
     * 检测到完整末地传送门时，在中心画一根紫色光柱 + 中心亮线 + 坐标文字。
     * 垂直方向与要塞光柱一样硬截断在 [beamMinY, beamMaxY]；宽度共用 beamHalfWidth。
     */
    private static void drawPortalCenterBeam(double camY) {
        if (!EyeCrossConfig.portalEnabled) {
            return;
        }
        EyeCrossState.CompletePortal portal = EyeCrossState.completePortal;
        if (portal == null) {
            return;
        }
        double bottom = Math.max(camY - PORTAL_BEAM_HALF_HEIGHT, EyeCrossConfig.beamMinY);
        double top = Math.min(camY + PORTAL_BEAM_HALF_HEIGHT, EyeCrossConfig.beamMaxY);
        double cx = portal.centerX();
        double cz = portal.centerZ();
        double halfWidth = EyeCrossConfig.beamHalfWidth;
        // 半透明填充光柱 + 高亮中心线，远处也能一眼看到
        AABB beam = new AABB(cx - halfWidth, bottom, cz - halfWidth,
                cx + halfWidth, top, cz + halfWidth);
        Gizmos.cuboid(beam, GizmoStyle.strokeAndFill(EyeCrossConfig.portalBeamColor, 2.0F, PORTAL_BEAM_FILL))
                .setAlwaysOnTop();
        Gizmos.line(new Vec3(cx, bottom, cz), new Vec3(cx, top, cz), PORTAL_BEAM_CORE, 3.0F)
                .setAlwaysOnTop();
        Gizmos.billboardText(String.format("X=%s Z=%s", EyeCrossText.f0(cx), EyeCrossText.f0(cz)),
                new Vec3(cx, top, cz), TextGizmo.Style.forColorAndCentered(PORTAL_TEXT_COLOR))
                .setAlwaysOnTop();
    }
}
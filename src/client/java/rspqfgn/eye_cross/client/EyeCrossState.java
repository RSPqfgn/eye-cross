package rspqfgn.eye_cross.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 模组全部运行时状态：进行中的轨迹采样、已采纳的直线、最新解，以及末地传送门扫描结果。
 */
public final class EyeCrossState {
    private EyeCrossState() {
    }

    /**
     * 一条已采纳的轨迹直线。XZ 平面上的点向式：P = (px, pz) + t·(dx, dz)，dx/dz 为单位向量。
     */
    public record FitLine(double px, double pz, double dx, double dz, double firstY) {
    }

    /**
     * 多条直线的最优交点。
     */
    public record Solution(double x, double z, double rmsError, double maxError, double distanceFromPlayer,
            int lineCount) {
    }

    /**
     * 扫描到的末地传送门相关方块，用于世界内方框标记。
     * kind：0 = 末地传送门框架方块（未放眼）；1 = 框架方块（已放眼）；2 = 末地传送门方块。
     */
    public record PortalBlock(double x, double y, double z, int kind) {
    }

    /**
     * 检测到的完整末地传送门：12 个框架围成 5×5 环，中央 3×3 为末地传送门区域。
     */
    public record CompletePortal(double centerX, double centerY, double centerZ, boolean activated, int eyesFilled) {
    }

    /**
     * 一次仍在飞行中的投掷采样，样本为 {x, y, z}。
     */
    public static final class Trail {
        private final List<double[]> samples = new ArrayList<>();

        public void addSample(double x, double y, double z) {
            double[] last = samples.isEmpty() ? null : samples.get(samples.size() - 1);
            if (last != null && last[0] == x && last[1] == y && last[2] == z) {
                return;
            }
            samples.add(new double[] { x, y, z });
        }

        public List<double[]> samples() {
            return samples;
        }

        public double pathLengthXZ() {
            double len = 0;
            for (int i = 1; i < samples.size(); i++) {
                len += Math.hypot(samples.get(i)[0] - samples.get(i - 1)[0],
                        samples.get(i)[2] - samples.get(i - 1)[2]);
            }
            return len;
        }

        public int size() {
            return samples.size();
        }
    }

    public static final int MAX_LINES = 12;

    /** HUD 在屏幕上的显示位置；/eyecross hudpos 设置。 */
    public enum HudPosition {
        TOP_LEFT("topleft"),
        TOP_RIGHT("topright"),
        BOTTOM_LEFT("bottomleft"),
        BOTTOM_RIGHT("bottomright");

        private final String key;

        HudPosition(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** HUD 是否显示；/eyecross hud 切换。 */
    public static boolean hudVisible = true;
    /** HUD 屏幕位置；/eyecross hudpos 切换。 */
    public static HudPosition hudPosition = HudPosition.TOP_LEFT;

    public static final Map<Integer, Trail> ACTIVE_TRAILS = new HashMap<>();
    public static final List<FitLine> LINES = new ArrayList<>();
    /** 最新解；直线数不足 2 或求解失败时为 null。 */
    public static Solution solution;
    /** 只扔了一颗眼时的环带估测；求出精确解前供 HUD / status 显示。 */
    public static StrongholdSolver.SingleThrowEstimate estimate;
    /** 最近一次求解是否因直线近似平行而失败。 */
    public static boolean parallelWarning;
    /** 记录轨迹时所在维度；切换维度时清空全部数据。 */
    public static ResourceKey<Level> dimension;

    /** 最近一次扫描到的末地传送门相关方块（世界内方框标记用），每次扫描整体替换。 */
    public static List<PortalBlock> portalBlocks = new ArrayList<>();
    /** 检测到的完整末地传送门；尚未检测到时为 null。 */
    public static CompletePortal completePortal;
    /** 完整末地传送门的聊天提示是否已经发过（避免每 10 tick 重复刷屏）。 */
    public static boolean portalAnnounced;

    public static void addLine(FitLine line) {
        LINES.add(line);
        while (LINES.size() > MAX_LINES) {
            LINES.remove(0);
        }
    }

    public static void clearTransient() {
        ACTIVE_TRAILS.clear();
    }

    public static void reset() {
        clearTransient();
        LINES.clear();
        solution = null;
        estimate = null;
        parallelWarning = false;
        portalBlocks.clear();
        completePortal = null;
        portalAnnounced = false;
        // 用户确认（t12）：模组重置/维度切换**不再**清空 Xaero 地图上的 eye-cross 路标——
        // 路标由精确解产生后保持在地图上，玩家可自行在 Xaero 里管理/删除。
        // （早期版本曾调用 XaeroSync.clearAll(dimension) 同步清理，已按用户要求移除。）
    }

    public static void onDimensionChanged(ResourceKey<Level> key) {
        dimension = key;
        reset();
    }
}
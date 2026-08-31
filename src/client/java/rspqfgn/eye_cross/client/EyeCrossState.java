package rspqfgn.eye_cross.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 模组全部运行时状态：进行中的轨迹采样、已采纳的直线、最新解。
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

    /** HUD 是否显示；/eyecross hud 切换。 */
    public static boolean hudVisible = true;

    public static final Map<Integer, Trail> ACTIVE_TRAILS = new HashMap<>();
    public static final List<FitLine> LINES = new ArrayList<>();
    /** 最新解；直线数不足 2 或求解失败时为 null。 */
    public static Solution solution;
    /** 最近一次求解是否因直线近似平行而失败。 */
    public static boolean parallelWarning;
    /** 记录轨迹时所在维度；切换维度时清空全部数据。 */
    public static ResourceKey<Level> dimension;

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
        parallelWarning = false;
    }

    public static void onDimensionChanged(ResourceKey<Level> key) {
        dimension = key;
        reset();
    }
}

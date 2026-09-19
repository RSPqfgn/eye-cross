package rspqfgn.eye_cross.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯数学求解：把一次投掷的轨迹点拟合成直线（总体最小二乘 / PCA），
 * 再对多条直线求最小二乘最优交点。
 */
public final class StrongholdSolver {
    private StrongholdSolver() {
    }

    /**
     * 两条直线夹角小于该值时认为近似平行，联立方程病态，不求解。
     */
    private static final double PARALLEL_SIN = Math.sin(Math.toRadians(2.0));

    public record SolvedPoint(double x, double z, double rmsError, double maxError, double distanceFromPlayer) {
    }

    /**
     * 对一次投掷的采样点做总体最小二乘直线拟合。
     *
     * @param pts 每项为 {x, y, z}
     * @return 直线（点 + 单位方向），退化时返回 null
     */
    public static EyeCrossState.FitLine fitTrail(List<double[]> pts) {
        int n = pts.size();
        if (n < 2) {
            return null;
        }
        double cx = 0, cz = 0;
        for (double[] p : pts) {
            cx += p[0];
            cz += p[2];
        }
        cx /= n;
        cz /= n;
        double sxx = 0, sxz = 0, szz = 0;
        for (double[] p : pts) {
            double dx = p[0] - cx, dz = p[2] - cz;
            sxx += dx * dx;
            sxz += dx * dz;
            szz += dz * dz;
        }
        // 协方差矩阵 [sxx sxz; sxz szz] 的最大特征值对应的特征向量即主方向
        double mean = (sxx + szz) / 2.0;
        double radius = Math.sqrt(Math.max(0, mean * mean - (sxx * szz - sxz * sxz)));
        double lambda = mean + radius;
        double dx, dz;
        if (Math.abs(sxz) > 1e-9) {
            dx = sxz;
            dz = lambda - sxx;
        } else if (sxx >= szz) {
            dx = 1;
            dz = 0;
        } else {
            dx = 0;
            dz = 1;
        }
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) {
            return null;
        }
        return new EyeCrossState.FitLine(cx, cz, dx / len, dz / len, pts.get(0)[1]);
    }

    /**
     * 最小二乘求所有直线的最优交点：min Σ ‖(I - d dᵀ)(q - p)‖²。
     *
     * @return 最优交点；直线近似平行（方程病态）时返回 null
     */
    public static SolvedPoint intersect(List<EyeCrossState.FitLine> lines, double playerX, double playerZ) {
        double a00 = 0, a01 = 0, a11 = 0, b0 = 0, b1 = 0;
        for (EyeCrossState.FitLine l : lines) {
            double nx = 1 - l.dx() * l.dx();
            double nzz = 1 - l.dz() * l.dz();
            double nxz = -l.dx() * l.dz();
            a00 += nx;
            a01 += nxz;
            a11 += nzz;
            b0 += nx * l.px() + nxz * l.pz();
            b1 += nxz * l.px() + nzz * l.pz();
        }
        double det = a00 * a11 - a01 * a01;
        if (Math.abs(det) < PARALLEL_SIN * PARALLEL_SIN) {
            return null;
        }
        double qx = (b0 * a11 - b1 * a01) / det;
        double qz = (a00 * b1 - a01 * b0) / det;

        List<Double> residuals = new ArrayList<>(lines.size());
        double sumSq = 0, max = 0;
        for (EyeCrossState.FitLine l : lines) {
            double vx = qx - l.px(), vz = qz - l.pz();
            double along = vx * l.dx() + vz * l.dz();
            double dist = Math.hypot(vx - along * l.dx(), vz - along * l.dz());
            residuals.add(dist);
            sumSq += dist * dist;
            max = Math.max(max, dist);
        }
        double rms = Math.sqrt(sumSq / lines.size());
        return new SolvedPoint(qx, qz, rms, max, Math.hypot(qx - playerX, qz - playerZ));
    }

    /**
     * 两条轨迹方向的夹角正弦，用于判断是否近似平行。
     */
    public static double sinAngleBetween(EyeCrossState.FitLine a, EyeCrossState.FitLine b) {
        return Math.abs(a.dx() * b.dz() - a.dz() * b.dx());
    }

    /**
     * 仅投掷一颗末影之眼时，基于要塞环带分布得到的估测结果。
     *
     * @param ringIndex         环号（1..8）
     * @param ringCount         该环上的要塞总数
     * @param x / z             估测坐标（投掷方向上、环带距离区间的中点）
     * @param radiusFromOrigin  估测点到世界原点的距离
     * @param minDistance / maxDistance 沿投掷方向，这个环带覆盖的距离区间（格）
     * @param distanceFromPlayer 估测点与当前玩家的直线距离
     * @param errorRadius       综合误差半径（环带纵向半宽 + 投掷方向角度噪声的横向偏移）
     */
    public record SingleThrowEstimate(double x, double z, int ringIndex, int ringCount,
            double radiusFromOrigin, double minDistance, double maxDistance,
            double distanceFromPlayer, double errorRadius) {
    }

    /** 单次投掷方向噪声的经验半角（度）：末影之眼每次投掷的朝向有随机偏差。 */
    private static final double EYE_ANGLE_ERROR_DEG = 8.0;

    /** 判定「交点坐标属于某个环带」的容差（格）：入口区块西北角在环内，主体可能略越界。 */
    private static final double RING_TOLERANCE = 64.0;

    /**
     * 基于要塞环带分布的单次投掷估测：只扔一颗末影之眼就能给出要塞的粗略位置。
     *
     * <p>原理：末影之眼飞向「距离玩家最近的要塞」，而要塞必然位于某个环带内，
     * 因此把投掷射线与 8 个环带逐一求交，取射线最先穿过的那个环带——
     * 该环带沿射线覆盖的距离区间就是要塞可能出现的位置，区间中点即估测点。
     *
     * <p>要塞可能位于区间内任意一点（径向位置由世界种子决定，客户端不可知），
     * 误差带 = 区间半宽（纵向）与投掷角度噪声引起的横向偏移的合成。
     *
     * @param samples 一次投掷的有序轨迹采样 {x, y, z}，首个样本即投掷点
     * @param playerX / playerZ 当前玩家坐标（仅用于报告直线距离）
     * @return 估测结果；射线前方不经过任何环带（例如远离原点朝外投掷）时返回 null
     */
    public static SingleThrowEstimate estimateSingleThrow(List<double[]> samples,
            double playerX, double playerZ) {
        if (samples.size() < 2) {
            return null;
        }
        double[] first = samples.get(0);
        double[] last = samples.get(samples.size() - 1);
        // PCA 拟合的方向符号不定，这里统一为「投掷点 → 轨迹末端」，即朝向要塞的方向
        double dx = last[0] - first[0];
        double dz = last[2] - first[2];
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) {
            return null;
        }
        dx /= len;
        dz /= len;
        double ox = first[0];
        double oz = first[2];

        // 取「沿射线最先到达的环带」：最可能就是要塞所在环（也是眼睛指向的最近要塞所在环）
        int best = -1;
        double bestT0 = Double.POSITIVE_INFINITY;
        double bestT1 = 0;
        for (int i = 0; i < StrongholdRings.RINGS.size(); i++) {
            StrongholdRings.Ring ring = StrongholdRings.RINGS.get(i);
            double[] interval = forwardAnnulus(ox, oz, dx, dz, ring.minDist(), ring.maxDist());
            if (interval == null) {
                continue;
            }
            if (interval[0] < bestT0) {
                bestT0 = interval[0];
                bestT1 = interval[1];
                best = i;
            }
        }
        if (best < 0) {
            return null;
        }
        StrongholdRings.Ring ring = StrongholdRings.RINGS.get(best);
        double tMid = (bestT0 + bestT1) / 2.0;
        double ex = ox + tMid * dx;
        double ez = oz + tMid * dz;
        double halfLongitudinal = (bestT1 - bestT0) / 2.0;
        double halfLateral = tMid * Math.tan(Math.toRadians(EYE_ANGLE_ERROR_DEG));
        return new SingleThrowEstimate(ex, ez, best + 1, ring.count(), Math.hypot(ex, ez),
                bestT0, bestT1, Math.hypot(ex - playerX, ez - playerZ),
                Math.hypot(halfLongitudinal, halfLateral));
    }

    /**
     * 射线 O + t·d（d 为单位向量，t ≥ 0）上满足 minR ≤ |O + t·d| ≤ maxR 的 t 区间。
     * 环带在射线前方可能被分成两段（穿过环带中间的“洞”时），此时取更近的一段。
     *
     * @return [tA, tB]（tA ≥ 0）；射线前方不经过该环带时返回 null
     */
    private static double[] forwardAnnulus(double ox, double oz, double dx, double dz,
            double minR, double maxR) {
        double b = ox * dx + oz * dz; // O·d
        double o2 = ox * ox + oz * oz; // |O|²
        double[] outer = circleInterval(b, o2, maxR); // |O + t·d| ≤ maxR 的 t 区间
        if (outer == null || outer[1] < 0) {
            return null; // 外圆不被碰到，或整段都在射线身后
        }
        double[] inner = circleInterval(b, o2, minR); // |O + t·d| ≤ minR 的 t 区间（环带中间的“洞”）
        double outStart = Math.max(0, outer[0]);
        double outEnd = outer[1];
        if (inner == null) {
            // 射线不穿过内圆：环带就是一个连续区间
            return new double[] { outStart, outEnd };
        }
        // 穿过内圆时环带分成两段：[进外圆 → 进内圆] 与 [出内圆 → 出外圆]，取更近的一段
        double a0 = outStart;
        double a1 = Math.min(outEnd, inner[0]);
        double b0 = Math.max(0, inner[1]);
        double b1 = outEnd;
        boolean hasA = a1 >= a0;
        boolean hasB = b1 >= b0;
        if (hasA && hasB) {
            return a0 <= b0 ? new double[] { a0, a1 } : new double[] { b0, b1 };
        }
        if (hasA) {
            return new double[] { a0, a1 };
        }
        if (hasB) {
            return new double[] { b0, b1 };
        }
        return null;
    }

    /**
     * 圆 |O + t·d| = r 与射线的交点：|O + t·d| ≤ r 的 t 区间 [tL, tR]。
     *
     * @return 无交点时返回 null
     */
    private static double[] circleInterval(double b, double o2, double r) {
        double disc = r * r - o2 + b * b;
        if (disc < 0) {
            return null;
        }
        double s = Math.sqrt(disc);
        return new double[] { -b - s, -b + s };
    }

    /**
     * 判断坐标距原点的半径属于哪个要塞环带。
     *
     * @return 环带索引（0..7，环号 = 索引 + 1）；不在任何环带内（含容差）时返回 -1
     */
    public static int ringOfRadius(double radius) {
        for (int i = 0; i < StrongholdRings.RINGS.size(); i++) {
            StrongholdRings.Ring ring = StrongholdRings.RINGS.get(i);
            if (radius >= ring.minDist() - RING_TOLERANCE && radius <= ring.maxDist() + RING_TOLERANCE) {
                return i;
            }
        }
        return -1;
    }
}

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
}

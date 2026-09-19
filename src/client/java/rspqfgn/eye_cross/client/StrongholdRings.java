package rspqfgn.eye_cross.client;

import java.util.List;

/**
 * Java 版末地要塞的环带分布数据。
 *
 * <p>每个世界固定生成 128 个要塞，分布在以世界原点（X=0，Z=0，而非出生点）为圆心的
 * 8 个圆环上；每个环上的要塞大致等角度分散，距离则在环带的（内半径，外半径）区间内随机。
 * 要塞从入口的螺旋楼梯开始生成，入口楼梯所在区块的西北角一定位于环带内，主体可能略越界。
 * 末影之眼总是飞向「距离玩家最近的要塞」，所以一次投掷的射线必定指向某个环带。
 * 数据与 Minecraft Wiki（Java 版 1.16.2+）一致。
 */
public final class StrongholdRings {
    private StrongholdRings() {
    }

    /** 一个要塞环带：要塞数量、距原点最小 / 最大距离（格）。 */
    public record Ring(int count, double minDist, double maxDist) {
    }

    /** 第 1 环到第 8 环（列表索引 0..7，环号 = 索引 + 1）。 */
    public static final List<Ring> RINGS = List.of(
            new Ring(3, 1239, 2857),
            new Ring(6, 4310, 5930),
            new Ring(10, 7382, 9002),
            new Ring(15, 10454, 12074),
            new Ring(21, 13526, 15146),
            new Ring(28, 16598, 18218),
            new Ring(36, 19670, 21290),
            new Ring(9, 22742, 24362));

    /** 全部 8 个环上的要塞总数：3+6+10+15+21+28+36+9 = 128。 */
    public static final int TOTAL = 128;
}
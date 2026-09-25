package rspqfgn.eye_cross.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 末地传送门扫描器：在已有精确要塞解、且要塞进入玩家区块渲染范围时，
 * 每 10 tick 扫描场景中已加载区块里的末地传送门框架方块 / 末地传送门方块，
 * 把结果存进 {@link EyeCrossState#portalBlocks} 供世界内方框标记；
 * 当 12 个框架围成完整的 5×5 末地传送门环时，在聊天框给出中心坐标、是否已激活、已放几颗眼。
 */
public final class EyeCrossPortalScanner {
    private EyeCrossPortalScanner() {
    }

    /** 扫描半径上限（格），防止渲染距离特别大时一次性扫太多区块。 */
    private static final double SCAN_RADIUS_CAP = 256.0;

    private static int tickCounter;

    /** 判断一个方块状态是否是传送门相关方块（框架或已激活的传送门方块）。 */
    private static final Predicate<BlockState> PORTAL_STATE = state ->
            state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_PORTAL);

    /**
     * 完整传送门的 12 个框架槽位，相对传送门西/北角（5×5 环的左上角）：
     * <pre>
     *  111     第 0 行：x=1..3
     * 10001    第 1 行：x=0 与 x=4
     * 10001    第 2 行：x=0 与 x=4
     * 10001    第 3 行：x=0 与 x=4
     *  111     第 4 行：x=1..3
     * </pre>
     * 中央 3×3（x=1..3, z=1..3）是末地传送门方块 / 传送门激活区。
     */
    private static final int[][] FRAME_SLOTS = {
            { 1, 0 }, { 2, 0 }, { 3, 0 },
            { 0, 1 }, { 4, 1 },
            { 0, 2 }, { 4, 2 },
            { 0, 3 }, { 4, 3 },
            { 1, 4 }, { 2, 4 }, { 3, 4 },
    };

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EyeCrossPortalScanner::onEndClientTick);
    }

    private static void onEndClientTick(Minecraft client) {
        if (!EyeCrossConfig.portalEnabled) {
            EyeCrossState.portalBlocks.clear();
            return;
        }
        int interval = Math.max(EyeCrossConfig.scanInterval, 1);
        if (++tickCounter % interval != 0) {
            return;
        }
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            EyeCrossState.portalBlocks.clear();
            return;
        }
        EyeCrossState.Solution s = EyeCrossState.solution;
        if (s == null) {
            EyeCrossState.portalBlocks.clear();
            return;
        }
        // 只有要塞解进入玩家区块渲染范围才开始扫描
        int renderBlocks = Math.max(client.options.getEffectiveRenderDistance(), 2) * 16;
        double dist = Math.hypot(s.x() - client.player.getX(), s.z() - client.player.getZ());
        if (dist > renderBlocks) {
            EyeCrossState.portalBlocks.clear();
            return;
        }
        scan(level, s, renderBlocks);
        detectAndAnnounce();
    }

    /** 以要塞解为中心、向四周扫已加载区块里的传送门方块。 */
    private static void scan(ClientLevel level, EyeCrossState.Solution s, int renderBlocks) {
        double radius = Math.min(SCAN_RADIUS_CAP, renderBlocks);
        int minX = (int) Math.floor(s.x() - radius) >> 4;
        int maxX = (int) Math.floor(s.x() + radius) >> 4;
        int minZ = (int) Math.floor(s.z() - radius) >> 4;
        int maxZ = (int) Math.floor(s.z() + radius) >> 4;

        List<EyeCrossState.PortalBlock> found = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                // 只扫已加载区块；isLoaded 对未加载区块返回 false
                BlockPos origin = new BlockPos(cx << 4, 0, cz << 4);
                if (!level.isLoaded(origin)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                int sectionOriginY = chunk.getMinY();
                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length; si++) {
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    // palette 级快速剔除：不含传送门方块的 section 整段跳过
                    if (!section.getStates().maybeHas(PORTAL_STATE)) {
                        continue;
                    }
                    int baseY = sectionOriginY + si * 16;
                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState bs = section.getBlockState(lx, ly, lz);
                                if (bs.is(Blocks.END_PORTAL_FRAME)) {
                                    boolean hasEye = bs.getValue(EndPortalFrameBlock.HAS_EYE);
                                    found.add(new EyeCrossState.PortalBlock(
                                            (double) (cx << 4) + lx, baseY + ly, (double) (cz << 4) + lz,
                                            hasEye ? 1 : 0));
                                } else if (bs.is(Blocks.END_PORTAL)) {
                                    found.add(new EyeCrossState.PortalBlock(
                                            (double) (cx << 4) + lx, baseY + ly, (double) (cz << 4) + lz, 2));
                                }
                            }
                        }
                    }
                }
            }
        }
        EyeCrossState.portalBlocks = found;
    }

    /** 从扫描集合中查找完整的 5×5 末地传送门环，找到后（仅一次）聊天提示。 */
    private static void detectAndAnnounce() {
        List<EyeCrossState.PortalBlock> blocks = EyeCrossState.portalBlocks;
        // 收集所有框架（kind 0/1），按 (x,z,y) 精确匹配槽位
        for (EyeCrossState.PortalBlock anchor : blocks) {
            if (anchor.kind() == 2) {
                continue;
            }
            // 尝试把 anchor 当作每个槽位上的框架，反推环的西北角
            for (int[] slot : FRAME_SLOTS) {
                double ox = anchor.x() - slot[0];
                double oz = anchor.z() - slot[1];
                double oy = anchor.y();
                EyeCrossState.CompletePortal portal = matchPortal(blocks, ox, oz, oy);
                if (portal != null) {
                    EyeCrossState.completePortal = portal;
                    if (!EyeCrossState.portalAnnounced) {
                        EyeCrossState.portalAnnounced = true;
                        announce(Minecraft.getInstance(), portal);
                    }
                    return;
                }
            }
        }
    }

    /** 校验以 (ox, oy, oz) 为西北角的 5×5 环是否完整，返回传送门信息；不完整返回 null。 */
    private static EyeCrossState.CompletePortal matchPortal(List<EyeCrossState.PortalBlock> blocks,
            double ox, double oz, double oy) {
        int eyes = 0;
        for (int[] slot : FRAME_SLOTS) {
            double fx = ox + slot[0];
            double fz = oz + slot[1];
            boolean frameFound = false;
            for (EyeCrossState.PortalBlock b : blocks) {
                if (b.kind() != 2 && b.y() == oy && b.x() == fx && b.z() == fz) {
                    frameFound = true;
                    if (b.kind() == 1) {
                        eyes++;
                    }
                    break;
                }
            }
            if (!frameFound) {
                return null;
            }
        }
        // 中央 3×3 内任意末地传送门方块 → 已激活
        boolean activated = false;
        for (EyeCrossState.PortalBlock b : blocks) {
            if (b.kind() == 2 && b.y() == oy
                    && b.x() > ox && b.x() < ox + 4
                    && b.z() > oz && b.z() < oz + 4) {
                activated = true;
                break;
            }
        }
        return new EyeCrossState.CompletePortal(ox + 2.0, oy + 0.5, oz + 2.0, activated, eyes);
    }

    private static void announce(Minecraft client, EyeCrossState.CompletePortal portal) {
        if (client.player == null) {
            return;
        }
        MutableComponent msg = EyeCrossText
                .tr("eyecross.chat.portal_found",
                        EyeCrossText.f0(portal.centerX()), EyeCrossText.f0(portal.centerZ()),
                        EyeCrossText.tr(portal.activated() ? "eyecross.chat.portal_active"
                                : "eyecross.chat.portal_inactive"),
                        portal.eyesFilled())
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(" "))
                .append(EyeCrossText.teleport(portal.centerX(), portal.centerZ()))
                .append(Component.literal(" "))
                .append(EyeCrossText.portalWaypoint());
        client.player.sendSystemMessage(msg);
    }
}
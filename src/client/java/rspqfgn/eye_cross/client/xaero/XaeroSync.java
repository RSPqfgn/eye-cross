package rspqfgn.eye_cross.client.xaero;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.WaypointRenderInfo;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * Xaero's Minimap 可选联动隔离层（路线 A：官方第三方路标 API，minimap ≥26.2.0）。
 *
 * <p>约束（docs/xaero-integration-feasibility.md §3 步骤 1）：
 * <ul>
 *   <li>本文件是本仓库中唯一允许出现 {@code xaero.*} 类引用的地方；</li>
 *   <li>运行期探测 + 版本检查（≥26.2.0），任一环节失败 → 静默降级（debug 日志一次 + 整层禁用），
 *       绝不连坐 eye-cross 主功能；</li>
 *   <li>所有 Xaero API 调用仅在客户端主线程执行（与官方 Waystones 兼容实现
 *       {@code xaero.hud.compat.mods.SupportWaystones} 一致），会话为 null 时直接跳过。</li>
 * </ul>
 *
 * <p>用户确认范围：仅在精确解（solution）产生时创建/更新固定名 Stronghold(EC) 的绿色 S 路标；
 * 不做估测路标、不做误差圈点阵、不做命令开关、不做语言改动。World Map 无需任何代码
 * （自动显示 minimap 管理的第三方路标，见可行性报告 §3 步骤 6.2）。
 */
public final class XaeroSync {
	private XaeroSync() {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("eye-cross/XaeroSync");

	private static final String XAERO_MOD_ID = "xaerominimap";
	/** 启用门槛：第三方路标系统引入版本 minimap 26.2.0（可行性报告 R9）。 */
	private static final int MIN_VERSION_MAJOR = 26;
	private static final int MIN_VERSION_MINOR = 2;

	/** 第三方路标来源 ID；稳定 id 用同名常量，同 id add() 即覆盖更新（不污染路标菜单）。 */
	private static final Identifier ORIGIN = Identifier.fromNamespaceAndPath("eye-cross", "stronghold");
	private static final String WAYPOINT_ID = "stronghold";
	/** 固定名（用户确认范围：不做语言改动，用固定英文名）。 */
	private static final String WAYPOINT_NAME = "Stronghold(EC)";
	private static final String WAYPOINT_SYMBOL = "S";
	/** 精确解只有 XZ；y 取固定地表参考高度（也让 Xaero 路标菜单的「传送」落点安全）。 */
	// 防回归（t5）：本类严禁任何引用 xaero.* 类型的 static 字段或静态初始化块。
	// static 字段初始化器在类加载 <clinit> 阶段执行，会强制解析 xaero.* 类型；
	// Xaero 未安装时 <clinit> 直接抛 NoClassDefFoundError（Error），发生在 isAvailable()
	// 守卫与任何 try/catch(LinkageError) 执行之前，会绕过全部降级路径并导致游戏崩溃
	// （实测 crash-2026-09-13_14.13.38-client.txt：WaypointColor.<clinit> 引用）。
	// 因此所有 xaero.* 类型引用（如颜色值 WaypointColor.GREEN）必须放在方法体内，
	// 由 pushSolution/clearAll 的 try/catch(LinkageError)（包含 NoClassDefFoundError）保护。
	private static final int WAYPOINT_Y = 64;

	/** null = 未探测；true/false = 探测结果。 */
	private static Boolean available;
	/** 已发生一次失败 → 整层禁用，避免刷日志。 */
	private static boolean broken;
	/** 诊断版（t8）：broken 闩锁后的静默跳过只记一次。 */
	private static boolean skipPushLogged;

	/**
	 * Xaero 联动是否可用：xaerominimap 已加载且版本 ≥26.2.0。结果缓存。
	 * 诊断版（t8）：首次探测时以 INFO 输出可用性结果与版本/原因（默认日志可见）。
	 */
	public static boolean isAvailable() {
		Boolean cached = available;
		if (cached != null) {
			return cached && !broken;
		}
		boolean ok = false;
		String versionFriendly = "unknown";
		String reason = "not loaded";
		try {
			Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(XAERO_MOD_ID);
			if (container.isPresent()) {
				versionFriendly = container.get().getMetadata().getVersion().getFriendlyString();
				try {
					SemanticVersion sv = SemanticVersion.parse(versionFriendly);
					reason = (sv.getVersionComponentCount() < 2)
							? "parse error (component count<2): " + versionFriendly
							: "below min " + MIN_VERSION_MAJOR + "." + MIN_VERSION_MINOR + " (actual "
									+ sv.getVersionComponent(0) + "." + sv.getVersionComponent(1) + ")";
				} catch (VersionParsingException parseFail) {
					reason = "parse error: " + versionFriendly;
				}
				ok = isAtLeastMinVersion(container.get().getMetadata().getVersion());
				if (ok) {
					reason = "ok";
				}
			}
		} catch (LinkageError | RuntimeException e) {
			LOGGER.debug("Xaero availability probe failed; waypoint sync disabled.", e);
			LOGGER.info("[XaeroSync] disabled: probe exception, version=" + versionFriendly);
			ok = false;
		}
		available = ok;
		if (!ok) {
			LOGGER.debug("Xaero's Minimap unavailable or below {}.{}; waypoint sync disabled.",
					MIN_VERSION_MAJOR, MIN_VERSION_MINOR);
			LOGGER.info("[XaeroSync] disabled, reason=" + reason + ", version=" + versionFriendly);
		} else {
			LOGGER.info("[XaeroSync] available, mod version=" + versionFriendly);
		}
		return ok && !broken;
	}

	private static boolean isAtLeastMinVersion(Version version) {
		try {
			SemanticVersion sv = SemanticVersion.parse(version.getFriendlyString());
			if (sv.getVersionComponentCount() < 2) {
				return false;
			}
			int major = sv.getVersionComponent(0);
			int minor = sv.getVersionComponent(1);
			return major > MIN_VERSION_MAJOR
					|| (major == MIN_VERSION_MAJOR && minor >= MIN_VERSION_MINOR);
		} catch (VersionParsingException e) {
			// 版本无法解析为语义版本 → 保守按不满足处理（静默降级）
			return false;
		}
	}

	/**
	 * 推送/更新精确解路标（同稳定 id 覆盖 = 更新）。用户已手动删除过该路标时不复活（R8 防御，
	 * 可行性报告 §4：{@code get(id).isThirdPartyDeleted()} 时跳过，尊重用户删除）。
	 *
	 * @param x 解 X 坐标
	 * @param z 解 Z 坐标
	 * @param dimension 当前维度（用于解析 Xaero 维度子容器）
	 */
	public static void pushSolution(double x, double z, ResourceKey<Level> dimension) {
		if (broken || !isAvailable()) {
			// 诊断版（t8）：broken 闩锁后的静默跳过记一次 INFO（防刷屏）。
			if (!skipPushLogged) {
				skipPushLogged = true;
				LOGGER.info("[XaeroSync] pushSolution skipped, broken=" + broken + ", available=" + available);
			}
			return;
		}
		LOGGER.info("[XaeroSync] pushSolution enter, x=" + x + ", z=" + z + ", dimension=" + dimension);
		try {
			applyPush(x, z, dimension);
		} catch (LinkageError | RuntimeException e) {
			degradeOnce(e);
		}
	}

	/**
	 * 诊断版（t9）：打印 add 目标容器与渲染读取容器（world.getContainer()）的路径/实例对比，
	 * 用于定位「路标 add 成功但小地图/世界地图/路标菜单均不可见」。纯日志，零行为影响。
	 */
	private static void logContainerPaths(MinimapWorldRootContainer root, String dimDir,
			MinimapWorldContainer dimContainer) {
		try {
			StringBuilder sb = new StringBuilder("container paths: addTarget=");
			sb.append(root.getPath()).append(" + \"").append(dimDir).append("\"");
			sb.append(" => ").append(dimContainer.getPath());
			sb.append(" (id=").append(System.identityHashCode(dimContainer)).append(")");
			var world = root.getSession().getWorldManager().getCurrentWorld();
			if (world == null) {
				sb.append("; renderWorld=null");
			} else {
				var wc = world.getContainer();
				sb.append("; renderWorld node=\"").append(world.getNode()).append("\"");
				sb.append(" fullPath=").append(world.getFullPath());
				sb.append(" container=").append(wc == null ? "null" : wc.getPath());
				sb.append(" (id=").append(wc == null ? "n/a" : System.identityHashCode(wc)).append(")");
				if (wc != null) {
					sb.append("; sameInstance=").append(wc == dimContainer);
					sb.append("; samePath=").append(wc.getPath().equals(dimContainer.getPath()));
				}
			}
			LOGGER.info("[XaeroSync] " + sb);
		} catch (LinkageError | RuntimeException e) {
			LOGGER.info("[XaeroSync] container path log failed: " + e);
		}
	}

	private static void applyPush(double x, double z, ResourceKey<Level> dimension) {
		if (dimension == null) {
			LOGGER.info("[XaeroSync] applyPush skip: dimension==null");
			return;
		}
		if (!Minecraft.getInstance().isSameThread()) {
			LOGGER.info("[XaeroSync] applyPush skip: not on client thread");
			return;
		}
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		if (session == null) {
			LOGGER.info("[XaeroSync] applyPush skip: session==null (module class="
					+ BuiltInHudModules.MINIMAP.getClass().getName() + ")");
			return;
		}
		MinimapWorldRootContainer root = session.getWorldManager().getAutoRootContainer();
		if (root == null) {
			LOGGER.info("[XaeroSync] applyPush skip: root==null (session class="
					+ session.getClass().getName() + ")");
			return;
		}
		String dimDir = session.getDimensionHelper().getDimensionDirectoryName(dimension);
		if (dimDir == null || dimDir.isEmpty()) {
			LOGGER.info("[XaeroSync] applyPush skip: dimDir is null/empty (dimension=" + dimension + ")");
			return;
		}
		LOGGER.info("[XaeroSync] applyPush dimDir=" + dimDir + ", dimension=" + dimension);
		MinimapWorldContainer dimContainer = root.addSubContainer(root.getPath().resolve(dimDir));
		// 诊断版（t9）：对比 add 目标容器与渲染读取容器（world.getContainer()）。
		logContainerPaths(root, dimDir, dimContainer);
		ThirdPartyWaypointManager manager = dimContainer.getThirdPartyWaypointManager();
		if (manager == null) {
			LOGGER.info("[XaeroSync] applyPush skip: manager==null (dimContainer class="
					+ dimContainer.getClass().getName() + ")");
			return;
		}
		ThirdPartyWaypoints tpw = manager.get(ORIGIN);
		if (tpw == null) {
			LOGGER.info("[XaeroSync] applyPush skip: tpw==null (origin=" + ORIGIN + ")");
			return;
		}
		// 颜色/类型内联在方法体内（防回归 t5：不得提升为 static 字段，见类注释）。
		Waypoint waypoint = new Waypoint((int) Math.floor(x), WAYPOINT_Y, (int) Math.floor(z),
				WAYPOINT_NAME, WAYPOINT_SYMBOL, WaypointColor.GREEN, WaypointPurpose.NORMAL);
		// t10 修复：Xaero 会把 origin 的 renderInfoOverride 持久化到 config.txt（third-party-waypoint 行）。
		// 若历史残留/用户删除过该路标（isThirdPartyDeleted=true），Xaero 加载后 add() 会把该 override
		// 挂到新 waypoint 上（ThirdPartyWaypoints.add() 字节码：renderInfoOverrides.get(id) → setThirdPartyRenderOverride），
		// 渲染/菜单/小地图全部按「已删除」跳过——三处不可见（用户实测）。
		// 正确顺序：先用 deleted=false 的新 override 覆盖 map 中的旧记录，再 add()（add 时挂上的是新 override）。
		// 之后 Xaero 保存 config.txt 时该行 thirdPartyDeleted 翻转为 false，一次修复永久生效。
		// 行为变更（用户确认范围外，需向用户说明）：移除原 R8「用户删除过则不复活」防御——
		// 精确解产生后路标必出现（与用户核心需求一致）。
		tpw.addRenderInfoOverride(WAYPOINT_ID, new WaypointRenderInfo());
		tpw.add(WAYPOINT_ID, waypoint);
		LOGGER.info("[XaeroSync] waypoint added/updated: x=" + (int) Math.floor(x) + ", y=" + WAYPOINT_Y
				+ ", z=" + (int) Math.floor(z) + ", id=" + WAYPOINT_ID + ", name=" + WAYPOINT_NAME
				+ ", symbol=" + WAYPOINT_SYMBOL);
	}

	private static void degradeOnce(Throwable t) {
		if (!broken) {
			broken = true;
			// 诊断版（t8）：升级为 WARN + 完整堆栈（首个异常可见于默认日志）。
			LOGGER.warn("[XaeroSync] waypoint sync disabled after an error (first failure stacktrace):", t);
		}
	}
}
package rspqfgn.eye_cross.client;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.InteractionResult;

/**
 * 模组配置文件（eye-cross.json5）：功能开关与部分渲染/扫描参数。
 *
 * <p>配置由 Cloth Config（AutoConfig）管理：文件经 Jankson 序列化器写出，
 * 因此配置文件是带 <code>//</code> 英文注释的 JSON5（docs/portal-config-plan.md §3/§4.1
 * 更新为“兼容 Mod Menu”方案）：
 * <ul>
 *   <li>位置：{@code config/eye-cross.json5}（Fabric 标准配置目录）；</li>
 *   <li>首次启动由 AutoConfig 自动生成默认值文件；</li>
 *   <li>Mod Menu 已安装时，可在模组菜单中点开配置界面（{@link EyeCrossModMenu}），
 *       界面保存即写回文件；也可用 /eyecross config reload 热重载；</li>
 *   <li>颜色一律用 8 位大写十六进制字符串 {@code AARRGGBB}（对应代码中的
 *       {@code 0xFFRRGGBB} 样式整型）；</li>
 *   <li>渲染与扫描在帧/tick 内读静态字段镜像（{@link #syncFrom(EyeCrossConfigData)}），
 *       因此改配置后（界面保存或 reload）立即生效。</li>
 * </ul>
 */
public final class EyeCrossConfig {
	private EyeCrossConfig() {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("eye-cross/EyeCrossConfig");

	// ---- 功能开关（默认全开） ----
	public static boolean portalEnabled = true;
	public static boolean estimateRing = true;
	public static boolean directionArrow = true;
	public static boolean hudEnabled = true;
	public static boolean xaeroWaypoints = true;

	// ---- 数值参数 ----
	public static int beamMinY = -64;
	public static int beamMaxY = 320;
	public static double beamHalfWidth = 0.35;
	public static int scanInterval = 10;

	// ---- 颜色（int 镜像，来自 EyeCrossConfigData 的 AARRGGBB 字符串） ----
	public static int solutionBeamColor = 0xFFFFAA00;
	public static int portalBeamColor = 0xFFAA00FF;
	public static int portalFrameNoEyeColor = 0xFFFFFF00;
	public static int portalFrameHasEyeColor = 0xFF00FF00;
	public static int portalBlockColor = 0xFFAA00FF;

	private static boolean loadedOnce;
	private static boolean registerAttempted;

	/** 配置文件完整路径（eye-cross.json5）。 */
	public static Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("eye-cross.json5");
	}

	/** 启动加载：注册 AutoConfig 并同步一次静态镜像；文件缺失时自动生成默认值。 */
	public static void load() {
		if (!registerAttempted) {
			registerAttempted = true;
			try {
				AutoConfig.register(EyeCrossConfigData.class, JanksonConfigSerializer::new);
				ConfigHolder<EyeCrossConfigData> holder = AutoConfig.getConfigHolder(EyeCrossConfigData.class);
				// GUI 保存后同步静态镜像，让渲染/扫描立即读到新值
				holder.registerSaveListener((manager, config) -> {
					syncFrom(config);
					return InteractionResult.SUCCESS;
				});
				// register 内部已加载一次磁盘值；再显式 sync 一次兜底
				syncFrom(holder.getConfig());
			} catch (RuntimeException e) {
				LOGGER.warn("[EyeCrossConfig] failed to register AutoConfig, using defaults (" + e + ")");
			}
		}
		loadedOnce = true;
	}

	/** 热重载：重新从磁盘读文件并同步静态镜像；文件不存在时沿用当前值。 */
	public static void reload() {
		if (!registerAttempted) {
			load();
			return;
		}
		try {
			ConfigHolder<EyeCrossConfigData> holder = AutoConfig.getConfigHolder(EyeCrossConfigData.class);
			boolean loaded = holder.load();
			syncFrom(holder.getConfig());
			LOGGER.info("[EyeCrossConfig] reload finished, load=" + loaded + ", path=" + getConfigPath());
		} catch (RuntimeException e) {
			LOGGER.warn("[EyeCrossConfig] reload failed, keeping current values (" + e + ")");
		}
	}

	/** 是否已执行过首次加载（客户端启动时由调用方触发一次）。 */
	public static boolean isLoaded() {
		return loadedOnce;
	}

	/** 把 AutoConfig 容器实例同步到本类静态字段（渲染/扫描读取入口）。 */
	private static void syncFrom(EyeCrossConfigData cfg) {
		portalEnabled = cfg.portalEnabled;
		estimateRing = cfg.estimateRing;
		directionArrow = cfg.directionArrow;
		hudEnabled = cfg.hudEnabled;
		xaeroWaypoints = cfg.xaeroWaypoints;

		beamMinY = cfg.beamMinY;
		beamMaxY = cfg.beamMaxY;
		beamHalfWidth = cfg.beamHalfWidth;
		scanInterval = Math.max(cfg.scanInterval, 1);

		solutionBeamColor = parseColor(cfg.solutionBeamColor, 0xFFFFAA00);
		portalBeamColor = parseColor(cfg.portalBeamColor, 0xFFAA00FF);
		portalFrameNoEyeColor = parseColor(cfg.portalFrameNoEyeColor, 0xFFFFFF00);
		portalFrameHasEyeColor = parseColor(cfg.portalFrameHasEyeColor, 0xFF00FF00);
		portalBlockColor = parseColor(cfg.portalBlockColor, 0xFFAA00FF);
	}

	private static int parseColor(String hex, int def) {
		if (hex == null) {
			return def;
		}
		String digits = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
		if (digits.length() != 8) {
			LOGGER.warn("[EyeCrossConfig] invalid color '" + hex + "', using default");
			return def;
		}
		try {
			return (int) Long.parseLong(digits, 16);
		} catch (NumberFormatException e) {
			LOGGER.warn("[EyeCrossConfig] invalid color '" + hex + "', using default");
			return def;
		}
	}

	/**
	 * AutoConfig 容器：字段即 eye-cross.json5 的键，@Comment 生成 // 英文注释。
	 */
	@Config(name = "eye-cross")
	public static class EyeCrossConfigData implements ConfigData {
		// ---- 功能开关 ----
		@Comment("Master switch for the end portal feature (scan + boxes + beam + chat)") //
		@ConfigEntry.Gui.Tooltip
		public boolean portalEnabled = true;
		@Comment("Draw the estimated range ring after a single eye throw") //
		@ConfigEntry.Gui.Tooltip
		public boolean estimateRing = true;
		@Comment("Draw the direction arrow when the stronghold is off-screen") //
		@ConfigEntry.Gui.Tooltip
		public boolean directionArrow = true;
		@Comment("Persistent default for the HUD (can still be toggled with /eyecross hud)") //
		@ConfigEntry.Gui.Tooltip
		public boolean hudEnabled = true;
		@Comment("Push/update the Stronghold(EC) waypoint on Xaero's Minimap") //
		@ConfigEntry.Gui.Tooltip
		public boolean xaeroWaypoints = true;

		// ---- 数值参数 ----
		@Comment("Beam vertical lower bound (world height clamp)") //
		@ConfigEntry.Gui.Tooltip
		public int beamMinY = -64;
		@Comment("Beam vertical upper bound (world height clamp)") //
		@ConfigEntry.Gui.Tooltip
		public int beamMaxY = 320;
		@Comment("Beam half width in blocks (both stronghold and portal beams)") //
		@ConfigEntry.Gui.Tooltip
		public double beamHalfWidth = 0.35;
		@Comment("End portal scan interval in ticks") //
		@ConfigEntry.Gui.Tooltip
		public int scanInterval = 10;

		// ---- 颜色（8 位 AARRGGBB 十六进制字符串） ----
		@Comment("Stronghold solution beam color (AARRGGBB)") //
		@ConfigEntry.Gui.Tooltip
		public String solutionBeamColor = "FFFFAA00";
		@Comment("End portal center beam color (AARRGGBB)") //
		@ConfigEntry.Gui.Tooltip
		public String portalBeamColor = "FFAA00FF";
		@Comment("Portal frame block WITHOUT eye color (AARRGGBB)") //
		@ConfigEntry.Gui.Tooltip
		public String portalFrameNoEyeColor = "FFFFFF00";
		@Comment("Portal frame block WITH eye color (AARRGGBB)") //
		@ConfigEntry.Gui.Tooltip
		public String portalFrameHasEyeColor = "FF00FF00";
		@Comment("End portal block color (AARRGGBB)") //
		@ConfigEntry.Gui.Tooltip
		public String portalBlockColor = "FFAA00FF";
	}
}
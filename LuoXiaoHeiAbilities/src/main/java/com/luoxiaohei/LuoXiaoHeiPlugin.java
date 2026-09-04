package com.luoxiaohei;

import com.luoxiaohei.abilities.*;
import com.luoxiaohei.commands.AbilityCommand;
import com.luoxiaohei.commands.LingQiCommand;
import com.luoxiaohei.config.ConfigManager;
import com.luoxiaohei.config.MessagesManager;
import com.luoxiaohei.cultivation.CultivationManager;
import com.luoxiaohei.data.PlayerDataManager;
import com.luoxiaohei.input.KeybindManager;
import com.luoxiaohei.listeners.*;
import com.luoxiaohei.ore.OreGenerator;
import com.luoxiaohei.ore.OreManager;
import com.luoxiaohei.spirititems.SpiritItemManager;
import com.luoxiaohei.ui.HUDManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 罗小黑战记风格特殊能力插件 v2.0
 * 金系 / 空间系 / 火系 / 雷系 / 木系
 * + 修炼系统(5阶) + 灵矿/灵块 + HUD + 按键绑定
 */
public final class LuoXiaoHeiPlugin extends JavaPlugin {

    private static LuoXiaoHeiPlugin instance;

    // 配置
    private ConfigManager configManager;
    private MessagesManager messagesManager;

    // 数据
    private PlayerDataManager playerDataManager;

    // 系统
    private CultivationManager cultivationManager;
    private HUDManager hudManager;
    private KeybindManager keybindManager;
    private SpiritItemManager spiritItemManager;

    // 矿
    private OreManager oreManager;
    private OreGenerator oreGenerator;

    // 能力
    private AbilityManager abilityManager;
    private MetalAbility metalAbility;
    private SpaceAbility spaceAbility;
    private FireAbility fireAbility;
    private ThunderAbility thunderAbility;
    private WoodAbility woodAbility;

    @Override
    public void onEnable() {
        instance = this;

        // 配置
        saveDefaultConfig();
        saveResource("action.yml", false);
        saveResource("messages.yml", false);
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        messagesManager = new MessagesManager(this);

        // 数据
        playerDataManager = new PlayerDataManager(this);
        playerDataManager.loadAllOnline();

        // 系统
        cultivationManager = new CultivationManager(this);
        hudManager = new HUDManager(this);
        keybindManager = new KeybindManager(this);
        spiritItemManager = new SpiritItemManager(this);

        // 矿 (v2.4.8+ 已废弃世界灵矿生成, 全部改为指令 /ability items 给予)
        oreManager = new OreManager(this);
        oreGenerator = new OreGenerator(this);
        // oreGenerator.register(); — 取消区块自动生成灵矿

        // 能力
        abilityManager = new AbilityManager(this);
        metalAbility = new MetalAbility(this);
        spaceAbility = new SpaceAbility(this);
        fireAbility = new FireAbility(this);
        thunderAbility = new ThunderAbility(this);
        woodAbility = new WoodAbility(this);
        abilityManager.registerAbility(AbilityType.METAL, metalAbility);
        abilityManager.registerAbility(AbilityType.SPACE, spaceAbility);
        abilityManager.registerAbility(AbilityType.FIRE, fireAbility);
        abilityManager.registerAbility(AbilityType.THUNDER, thunderAbility);
        abilityManager.registerAbility(AbilityType.WOOD, woodAbility);

        // 指令
        AbilityCommand abCmd = new AbilityCommand(this);
        getCommand("ability").setExecutor(abCmd);
        getCommand("ability").setTabCompleter(abCmd);
        getCommand("lingqi").setExecutor(new LingQiCommand(this));

        // 监听器
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AbilityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        Bukkit.getPluginManager().registerEvents(keybindManager, this);
        Bukkit.getPluginManager().registerEvents(spiritItemManager, this);
        Bukkit.getPluginManager().registerEvents(metalAbility, this);
        Bukkit.getPluginManager().registerEvents(spaceAbility, this);
        Bukkit.getPluginManager().registerEvents(fireAbility, this);
        Bukkit.getPluginManager().registerEvents(thunderAbility, this);
        Bukkit.getPluginManager().registerEvents(woodAbility, this);

        // 配方
        spiritItemManager.registerRecipes();

        // 调度器
        hudManager.startScheduler();
        oreManager.startRegenScheduler();
        spaceAbility.startRestoreScheduler();
        spaceAbility.startDomainScheduler();
        // spiritItemManager.startGlowScheduler(); — 无世界灵矿, 关闭粒子发光任务

        getLogger().info("======================================");
        getLogger().info(" LuoXiaoHeiAbilities v2.4.8 已启动");
        getLogger().info(" 五大能力 + 修炼5阶 + HUD + 灵物(指令给予)");
        getLogger().info("======================================");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.saveAll();
        if (spaceAbility != null) spaceAbility.forceRestoreAll();
        if (hudManager != null) hudManager.cleanup();
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("LuoXiaoHeiAbilities 已关闭");
    }

    public static LuoXiaoHeiPlugin getInstance() { return instance; }

    // Getters
    public ConfigManager getConfigManager() { return configManager; }
    public MessagesManager getMessagesManager() { return messagesManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public CultivationManager getCultivationManager() { return cultivationManager; }
    public HUDManager getHudManager() { return hudManager; }
    public KeybindManager getKeybindManager() { return keybindManager; }
    public SpiritItemManager getSpiritItemManager() { return spiritItemManager; }
    public OreManager getOreManager() { return oreManager; }
    public OreGenerator getOreGenerator() { return oreGenerator; }
    public AbilityManager getAbilityManager() { return abilityManager; }
    public MetalAbility getMetalAbility() { return metalAbility; }
    public SpaceAbility getSpaceAbility() { return spaceAbility; }
    public FireAbility getFireAbility() { return fireAbility; }
    public ThunderAbility getThunderAbility() { return thunderAbility; }
    public WoodAbility getWoodAbility() { return woodAbility; }
}

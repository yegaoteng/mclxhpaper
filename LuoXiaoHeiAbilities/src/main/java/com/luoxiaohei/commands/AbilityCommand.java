package com.luoxiaohei.commands;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.abilities.AbilityType;
import com.luoxiaohei.data.PlayerData;
import com.luoxiaohei.input.KeybindManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Chunk;

import java.util.*;

/**
 * /ability 指令 v2.1
 * - set <玩家> <类型>     指定玩家觉醒能力
 * - on/off [玩家]         开关技能
 * - cycle                 手动切换技能
 * - list                  列出能力
 * - reload                重载配置
 * - info [玩家]           查看信息
 * - bind <cycle|toggle> <按键>  绑定按键
 * - givespirit <玩家> <数量>    给予灵力
 * - givexp <玩家> <数量>        给予修炼经验
 * - items [类型] [数量]          获取灵矿/灵物 (创造模式也可用)
 */
public class AbilityCommand implements CommandExecutor, TabCompleter {

    private final LuoXiaoHeiPlugin plugin;

    public AbilityCommand(LuoXiaoHeiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) { help(s); return true; }
        switch (args[0].toLowerCase()) {
            case "list": listAbilities(s); break;
            case "set": cmdSet(s, args); break;
            case "on": cmdToggle(s, args, true); break;
            case "off": cmdToggle(s, args, false); break;
            case "cycle": cmdCycle(s, args); break;
            case "reload": cmdReload(s); break;
            case "info": cmdInfo(s, args); break;
            case "bind": cmdBind(s, args); break;
            case "givespirit": cmdGiveSpirit(s, args); break;
            case "givexp": cmdGiveXp(s, args); break;
            case "items": cmdItems(s, args); break;
            case "genores": cmdGenOres(s, args); break;
            default: help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("§b===== §e罗小黑能力系统 v2.4.3 §b=====");
        s.sendMessage("§7/ability list §f- 列出能力");
        s.sendMessage("§7/ability set <玩家> <metal|space|fire|thunder|wood>");
        s.sendMessage("§7/ability on|off [玩家] §f- 开关技能");
        s.sendMessage("§7/ability cycle §f- 手动切换技能");
        s.sendMessage("§7/ability bind <cycle|toggle> <按键>");
        s.sendMessage("§7/ability items [ore|particle|block|melon|all] [数量] §f- 获取灵物");
        s.sendMessage("§7/ability givespirit <玩家> <数量> §f- 给予灵力");
        s.sendMessage("§7/ability givexp <玩家> <数量> §f- 给予修炼经验");
        s.sendMessage("§7/ability genores [半径] §f- 对已加载区块回填灵矿");
        s.sendMessage("§7/ability info [玩家] §f- 查看信息");
        s.sendMessage("§7/ability reload §f- 重载配置");
        s.sendMessage("§e左键空气=释放技能, Shift=切换技能, F=开关技能");
    }

    private void listAbilities(CommandSender s) {
        s.sendMessage("§b=== 可用能力 ===");
        for (AbilityType t : AbilityType.values()) {
            if (t == AbilityType.NONE) continue;
            String st = plugin.getAbilityManager().isAbilityEnabled(t) ? "§a[启用]" : "§c[禁用]";
            s.sendMessage(t.getColor() + "  " + t.name() + "(" + t.getChinese() + ") " + st);
        }
    }

    private void cmdSet(CommandSender s, String[] args) {
        if (!s.hasPermission("ability.admin")) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return; }
        if (args.length < 3) { s.sendMessage("§c用法: /ability set <玩家> <类型>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-player-not-found")); return; }
        AbilityType type = AbilityType.fromString(args[2]);
        if (type == AbilityType.NONE) {
            plugin.getAbilityManager().clearPlayerAbility(target);
            s.sendMessage("§a已清除 " + target.getName() + " 的能力");
            return;
        }
        if (!plugin.getAbilityManager().isAbilityEnabled(type)) {
            s.sendMessage(plugin.getMessagesManager().getPrefixed("ability-disabled-in-config")); return;
        }
        if (plugin.getAbilityManager().setPlayerAbility(target, type))
            s.sendMessage("§a" + target.getName() + " 已觉醒「" + type.getChinese() + "」");
    }

    private void cmdToggle(CommandSender s, String[] args, boolean enable) {
        Player target;
        if (args.length >= 2) {
            if (!s.hasPermission("ability.admin")) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return; }
            target = Bukkit.getPlayer(args[1]);
        } else if (s instanceof Player p) { target = p; }
        else { s.sendMessage("§c控制台: /ability " + (enable?"on":"off") + " <玩家>"); return; }
        if (target == null) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-player-not-found")); return; }
        PlayerData d = plugin.getPlayerDataManager().getData(target);
        if (enable && d.getAbilityType() == AbilityType.NONE) {
            s.sendMessage(plugin.getMessagesManager().getPrefixed("ability-not-set")); return;
        }
        plugin.getAbilityManager().toggleAbility(target, enable);
    }

    private void cmdBind(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("§c仅玩家可绑定按键"); return; }
        if (!p.hasPermission(plugin.getConfig().getString("permissions.bind", "ability.use"))) {
            s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return;
        }
        if (args.length < 3) {
            p.sendMessage("§7用法: /ability bind <cycle|toggle> <SNEAK|SWAP_HANDS|SHIFT_SWAP|DROP|SHIFT_DROP>");
            PlayerData d = plugin.getPlayerDataManager().getData(p);
            p.sendMessage("§7当前: 切换=" + d.getBindCycle() + " 开关=" + d.getBindToggle());
            return;
        }
        String action = args[1].toLowerCase();
        String key = args[2].toUpperCase();
        if (!KeybindManager.VALID_BINDS.contains(key)) {
            p.sendMessage(plugin.getMessagesManager().getPrefixed("keybind-invalid")); return;
        }
        PlayerData d = plugin.getPlayerDataManager().getData(p);
        if (action.equals("cycle")) d.setBindCycle(key);
        else if (action.equals("toggle")) d.setBindToggle(key);
        else { p.sendMessage("§c操作必须是 cycle 或 toggle"); return; }
        p.sendMessage(plugin.getMessagesManager().getPrefixed("keybind-set", "action", action, "key", key));
    }

    private void cmdCycle(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("§c仅玩家可切换技能"); return; }
        PlayerData d = plugin.getPlayerDataManager().getData(p);
        if (d.getAbilityType() == AbilityType.NONE) {
            s.sendMessage(plugin.getMessagesManager().getPrefixed("ability-not-set")); return;
        }
        plugin.getKeybindManager().cycleSkill(p);
    }

    private void cmdItems(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { s.sendMessage("§c仅玩家可获取物品"); return; }
        if (!p.hasPermission("ability.use")) {
            s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return;
        }
        var sim = plugin.getSpiritItemManager();
        String type = args.length >= 2 ? args[1].toLowerCase() : "all";
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (Exception e) { amount = 1; }
        }
        switch (type) {
            case "ore":
                p.getInventory().addItem(sim.createRawOre(amount));
                p.sendMessage("§a获得 §f灵矿 §ax" + amount);
                break;
            case "particle":
                p.getInventory().addItem(sim.createParticle(amount));
                p.sendMessage("§a获得 §f灵粒 §ax" + amount);
                break;
            case "block":
                p.getInventory().addItem(sim.createSpiritBlock(amount));
                p.sendMessage("§a获得 §f灵块 §ax" + amount);
                break;
            case "melon":
                p.getInventory().addItem(sim.createSpiritMelon(amount));
                p.sendMessage("§a获得 §f灵瓜 §ax" + amount);
                break;
            case "all":
                p.getInventory().addItem(sim.createRawOre(amount));
                p.getInventory().addItem(sim.createParticle(amount));
                p.getInventory().addItem(sim.createSpiritBlock(amount));
                p.getInventory().addItem(sim.createSpiritMelon(amount));
                p.sendMessage("§a获得全部灵物 §ax" + amount);
                break;
            default:
                p.sendMessage("§7用法: /ability items <ore|particle|block|melon|all> [数量]");
                break;
        }
    }

    private void cmdGiveSpirit(CommandSender s, String[] args) {
        if (!s.hasPermission(plugin.getConfig().getString("permissions.give-spiritual", "ability.admin"))) {
            s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return;
        }
        if (args.length < 3) { s.sendMessage("§c用法: /ability givespirit <玩家> <数量>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-player-not-found")); return; }
        int amount;
        try { amount = Integer.parseInt(args[2]); } catch (Exception e) { s.sendMessage("§c数量必须是数字"); return; }
        plugin.getPlayerDataManager().addSpiritual(target, amount);
        s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-give-spirit", "player", target.getName(), "amount", String.valueOf(amount)));
        target.sendMessage("§b+" + amount + " 灵力");
    }

    private void cmdGiveXp(CommandSender s, String[] args) {
        if (!s.hasPermission(plugin.getConfig().getString("permissions.give-xp", "ability.admin"))) {
            s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return;
        }
        if (args.length < 3) { s.sendMessage("§c用法: /ability givexp <玩家> <数量>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-player-not-found")); return; }
        int amount;
        try { amount = Integer.parseInt(args[2]); } catch (Exception e) { s.sendMessage("§c数量必须是数字"); return; }
        plugin.getPlayerDataManager().addXp(target, amount);
        s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-give-xp", "player", target.getName(), "amount", String.valueOf(amount)));
        target.sendMessage("§6+" + amount + " 修炼经验");
        plugin.getCultivationManager().checkLevelUp(target);
    }

    private void cmdReload(CommandSender s) {
        if (!s.hasPermission("ability.admin")) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return; }
        plugin.getConfigManager().loadConfigs();
        plugin.getMessagesManager().load();
        s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-reload"));
    }

    private void cmdGenOres(CommandSender s, String[] args) {
        // v2.4.8+: 灵矿世界生成已废弃, 提示用 /ability items 获取
        s.sendMessage("§e[灵矿系统] §7世界灵矿自动生成已废弃, 不再在区块中放置灵矿方块。");
        s.sendMessage("§e[灵矿系统] §f请使用 §a/ability items <ore|particle|block|melon|all> [数量] §f直接获取物品。");
    }

    private void cmdInfo(CommandSender s, String[] args) {
        Player target;
        if (args.length >= 2) target = Bukkit.getPlayer(args[1]);
        else if (s instanceof Player p) target = p;
        else { s.sendMessage("§c用法: /ability info [玩家]"); return; }
        if (target == null) { s.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-player-not-found")); return; }
        PlayerData d = plugin.getPlayerDataManager().getData(target);
        s.sendMessage("§b=== " + target.getName() + " ===");
        s.sendMessage(" §7能力: " + (d.getAbilityType()==AbilityType.NONE ? "§7无" : d.getAbilityType().getColor()+d.getAbilityType().getChinese()));
        s.sendMessage(" §7技能: " + (d.isEnabled() ? "§a开" : "§c关"));
        s.sendMessage(" §7阶数: §f" + plugin.getCultivationManager().getLevelChinese(d.getCultivationLevel()));
        s.sendMessage(" §6经验: §f" + d.getCultivationXp() + "/" + plugin.getCultivationManager().getXpToNext(d.getCultivationLevel()));
        s.sendMessage(" §b灵力: §f" + d.getSpiritual() + "/" + d.getMaxSpiritual());
        s.sendMessage(" §7按键: §f切换=" + d.getBindCycle() + " 开关=" + d.getBindToggle());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> r = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("list","set","on","off","cycle","info","reload","bind","givespirit","givexp","items","genores"))
                if (sub.startsWith(args[0].toLowerCase())) r.add(sub);
            return r;
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("reload")
                && !args[0].equalsIgnoreCase("cycle") && !args[0].equalsIgnoreCase("items")
                && !args[0].equalsIgnoreCase("genores")) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) r.add(p.getName());
            return r;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            for (AbilityType t : AbilityType.values()) {
                if (t != AbilityType.NONE && t.name().toLowerCase().startsWith(args[2].toLowerCase())) r.add(t.name().toLowerCase());
            }
            return r;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bind")) {
            for (String b : KeybindManager.VALID_BINDS)
                if (b.startsWith(args[2].toUpperCase())) r.add(b);
            return r;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bind")) {
            for (String a : Arrays.asList("cycle", "toggle"))
                if (a.startsWith(args[1].toLowerCase())) r.add(a);
            return r;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("items")) {
            for (String a : Arrays.asList("ore", "particle", "block", "melon", "all"))
                if (a.startsWith(args[1].toLowerCase())) r.add(a);
            return r;
        }
        return Collections.emptyList();
    }
}

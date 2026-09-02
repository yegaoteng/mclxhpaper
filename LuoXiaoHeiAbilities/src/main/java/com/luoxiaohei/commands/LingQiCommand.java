package com.luoxiaohei.commands;

import com.luoxiaohei.LuoXiaoHeiPlugin;
import com.luoxiaohei.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /lingqi 指令 v2.0
 */
public class LingQiCommand implements CommandExecutor {

    private final LuoXiaoHeiPlugin plugin;

    public LingQiCommand(LuoXiaoHeiPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("ability.admin")) {
                sender.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-no-permission")); return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) { sender.sendMessage(plugin.getMessagesManager().getPrefixed("cmd-player-not-found")); return true; }
        } else if (sender instanceof Player p) { target = p; }
        else { sender.sendMessage("§c控制台: /lingqi <玩家>"); return true; }
        PlayerData d = plugin.getPlayerDataManager().getData(target);
        int spirit = d.getSpiritual(), max = d.getMaxSpiritual();
        int pct = max > 0 ? (int)((double)spirit/max*100) : 0;
        sender.sendMessage("§b========= 灵力信息 ==========");
        sender.sendMessage("§7玩家: §f" + target.getName());
        sender.sendMessage("§7阶数: §f" + plugin.getCultivationManager().getLevelChinese(d.getCultivationLevel()));
        sender.sendMessage("§7灵力: §b" + spirit + "§7 / §b" + max + " §7(" + pct + "%)");
        sender.sendMessage("§6经验: §f" + d.getCultivationXp() + "/" + plugin.getCultivationManager().getXpToNext(d.getCultivationLevel()));
        sender.sendMessage("§7[" + buildBar(pct) + "§7]");
        sender.sendMessage("§b=============================");
        return true;
    }

    private String buildBar(int pct) {
        StringBuilder sb = new StringBuilder();
        int filled = pct / 5;
        for (int i = 0; i < 20; i++) {
            if (i < filled) {
                if (pct > 66) sb.append("§b|");
                else if (pct > 33) sb.append("§a|");
                else sb.append("§c|");
            } else sb.append("§7|");
        }
        return sb.toString();
    }
}

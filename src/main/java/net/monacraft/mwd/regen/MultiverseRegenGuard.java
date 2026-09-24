package net.monacraft.mwd.regen;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.monacraft.mwd.bootstrap.BootstrapState;
import net.monacraft.mwd.compiler.CompilationResult;
import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.IOException;
import java.util.Locale;

public final class MultiverseRegenGuard implements Listener {
    private final MiniMessage messages = MiniMessage.miniMessage();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (block(event.getPlayer(), event.getMessage())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (block(event.getSender(), event.getCommand())) event.setCancelled(true);
    }

    private boolean block(CommandSender sender, String raw) {
        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 3 || !parts[0].equalsIgnoreCase("mv") || !parts[1].equalsIgnoreCase("regen")) return false;
        String target = parts[2].toLowerCase(Locale.ROOT);
        for (CompilationResult result : BootstrapState.get().compilations().values()) {
            if (!result.success()) continue;
            if (target.equals(result.dimensionKey().toLowerCase(Locale.ROOT)) || target.equals(folderName(result).toLowerCase(Locale.ROOT))) {
                sender.sendMessage(messages.deserialize("<red>MonaWorldDatapacksのカスタムディメンションには /mv regen を使用できません。</red>"));
                sender.sendMessage(messages.deserialize("<yellow>代わりに</yellow> <white>/mwd regen " + escape(result.world()) + " confirm</white> <yellow>を実行してから再起動してください。</yellow>"));
                return true;
            }
        }
        return false;
    }

    private String folderName(CompilationResult result) {
        try { return CustomWorldPath.resolve(CustomWorldPath.serverRoot(BootstrapState.get().dataDirectory()), result.dimensionKey()).getFileName().toString(); }
        catch (IOException ignored) { return ""; }
    }

    private String escape(String value) { return messages.escapeTags(value); }
}

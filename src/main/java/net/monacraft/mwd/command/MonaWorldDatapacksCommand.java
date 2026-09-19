package net.monacraft.mwd.command;

import io.papermc.paper.command.brigadier.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.monacraft.mwd.bootstrap.BootstrapState;
import net.monacraft.mwd.compiler.*;
import net.monacraft.mwd.config.*;
import net.monacraft.mwd.diagnostic.*;
import net.monacraft.mwd.pack.*;
import net.monacraft.mwd.plugin.MonaWorldDatapacksPlugin;
import net.monacraft.mwd.profile.ProfileRegistry;
import org.bukkit.command.CommandSender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class MonaWorldDatapacksCommand implements BasicCommand {
    private final MonaWorldDatapacksPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    public MonaWorldDatapacksCommand(MonaWorldDatapacksPlugin plugin) { this.plugin = plugin; }

    @Override public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "status" -> { require(sender, "monaworlddatapacks.status"); status(sender); }
                case "packs" -> { require(sender, "monaworlddatapacks.status"); async(sender, () -> packs(sender)); }
                case "worlds" -> { require(sender, "monaworlddatapacks.status"); worlds(sender); }
                case "info" -> { require(sender, "monaworlddatapacks.status"); need(args, 2); info(sender, args[1]); }
                case "scan" -> { require(sender, "monaworlddatapacks.scan"); need(args, 2); async(sender, () -> scan(sender, args[1], false)); }
                case "report" -> { require(sender, "monaworlddatapacks.scan"); need(args, 2); async(sender, () -> scan(sender, args[1], true)); }
                case "assign" -> { require(sender, "monaworlddatapacks.assign"); need(args, 3); assign(sender, args); }
                case "unassign" -> { require(sender, "monaworlddatapacks.assign"); need(args, 3); unassign(sender, args[1], args[2]); }
                case "compile" -> { require(sender, "monaworlddatapacks.compile"); async(sender, () -> compile(sender, args.length > 1 ? args[1] : null)); }
                case "doctor" -> { require(sender, "monaworlddatapacks.doctor"); doctor(sender, args.length > 1 ? args[1] : null); }
                case "reload-config" -> { require(sender, "monaworlddatapacks.admin"); reload(sender); }
                case "version" -> message(sender, "<gold>MonaWorldDatapacks</gold> <white>" + plugin.getPluginMeta().getVersion() + "</white> / transform " + DatapackCompiler.TRANSFORM_VERSION);
                default -> help(sender);
            }
        } catch (CommandFailure | IOException failure) { message(sender, "<red>" + escape(failure.getMessage()) + "</red>"); }
    }

    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) return filter(List.of("status","packs","worlds","info","scan","assign","unassign","compile","doctor","report","reload-config","version"), args.length == 0 ? "" : args[0]);
        BootstrapState state = BootstrapState.get();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && Set.of("info","compile","doctor","assign","unassign").contains(sub)) return filter(state.configuration().worlds().keySet(), args[1]);
        if ((sub.equals("scan") || sub.equals("report") || (args.length == 3 && Set.of("assign","unassign").contains(sub)))) {
            try { return filter(state.packManager().listPackIds(), args[args.length - 1]); } catch (IOException ignored) { return List.of(); }
        }
        if (sub.equals("assign") && args.length == 4) return filter(new ProfileRegistry().ids(), args[3]);
        return List.of();
    }

    private void status(CommandSender sender) {
        BootstrapState state = BootstrapState.get(); long ok = state.compilations().values().stream().filter(CompilationResult::success).count();
        message(sender, "<gold><bold>MonaWorldDatapacks</bold></gold> <gray>— scoped worldgen</gray>");
        message(sender, "<gray>割り当て:</gray> <white>" + state.configuration().worlds().size() + "</white> <gray>/ compiled:</gray> <green>" + ok + "</green> <gray>/ errors:</gray> <red>" + state.bootstrapErrors().size() + "</red>");
        message(sender, "<yellow>worldgen変更はホットリロードされません。反映には通常再起動が必要です。</yellow>");
    }
    private void packs(CommandSender sender) throws IOException {
        List<String> ids = BootstrapState.get().packManager().listPackIds();
        sync(() -> message(sender, "<gold>Packs:</gold> " + (ids.isEmpty() ? "<gray>(none)</gray>" : "<white>" + escape(String.join(", ", ids)) + "</white>")));
    }
    private void worlds(CommandSender sender) {
        BootstrapState.get().configuration().worlds().values().forEach(w -> message(sender, "<gold>" + escape(w.worldName()) + "</gold> <gray>" + escape(w.environment()) + " / " + escape(w.profile()) + " / " + (w.enabled() ? "enabled" : "disabled") + "</gray>"));
    }
    private void info(CommandSender sender, String world) {
        WorldAssignment a = BootstrapState.get().configuration().worlds().get(world);
        if (a == null) throw new CommandFailure("Unknown world assignment: " + world);
        CompilationResult c = BootstrapState.get().compilations().get(world);
        message(sender, "<gold>" + escape(world) + "</gold> <gray>profile:</gray> <white>" + escape(a.profile()) + "</white> <gray>source:</gray> <white>" + a.sourceDimension() + "</white>");
        message(sender, "<gray>packs:</gray> <white>" + escape(a.datapacks().toString()) + "</white>");
        if (c != null) message(sender, "<gray>compiled:</gray> " + (c.success() ? "<green>" + c.dimensionKey() + "</green>" : "<red>" + escape(String.join("; ", c.messages())) + "</red>"));
    }
    private void scan(CommandSender sender, String pack, boolean writeReport) throws IOException {
        DatapackAnalysis a = BootstrapState.get().packManager().scan(pack);
        var detected = new ProfileRegistry().detect(a);
        String text = report(a);
        Path reportPath = BootstrapState.get().dataDirectory().resolve("reports").resolve(pack + ".md");
        if (writeReport) Files.writeString(reportPath, text, StandardCharsets.UTF_8);
        sync(() -> {
            message(sender, "<gold>Pack:</gold> <white>" + escape(a.id()) + ".zip</white> <gray>format:</gray> <white>" + a.metadata().formatDisplay() + "</white>");
            message(sender, "<gray>Namespaces:</gray> <white>" + escape(String.join(", ", a.namespaces())) + "</white>");
            message(sender, "<gray>Worldgen:</gray> <white>" + a.worldgenCount() + "</white> <gray>Functions:</gray> <white>" + a.count(net.monacraft.mwd.resource.ResourceType.FUNCTION) + "</white>");
            message(sender, "<gray>Detected profile:</gray> <white>" + detected.id() + "</white> <gray>source:</gray> <white>" + detected.sourceDimension() + "</white>");
            message(sender, "<gray>Compatibility:</gray> " + (a.compatibility().safeForStrictMode() ? "<green>SUPPORTED</green>" : "<yellow>" + a.compatibility().result() + "</yellow>"));
            if (writeReport) message(sender, "<green>Report:</green> <white>" + escape(reportPath.toString()) + "</white>");
        });
    }
    private String report(DatapackAnalysis a) {
        StringBuilder out = new StringBuilder("# MonaWorldDatapacks Compatibility Report\n\n")
                .append("- Pack: `").append(a.id()).append("`\n- SHA-256: `").append(a.sha256()).append("`\n- Pack format: ").append(a.metadata().formatDisplay())
                .append("\n- Namespaces: ").append(a.namespaces()).append("\n- Result: **").append(a.compatibility().result()).append("**\n\n## Resource classes\n\n");
        a.compatibility().counts().forEach((k,v) -> out.append("- ").append(k).append(": ").append(v).append('\n'));
        out.append("\n## Problems\n\n");
        if (a.compatibility().problems().isEmpty() && a.compatibility().unknownReferences().isEmpty()) out.append("None.\n");
        else { a.compatibility().problems().forEach(p -> out.append("- ").append(p).append('\n')); a.compatibility().unknownReferences().forEach(p -> out.append("- UNKNOWN_REFERENCE: ").append(p).append('\n')); }
        return out.toString();
    }
    private void assign(CommandSender sender, String[] args) throws IOException {
        String profile = args.length > 3 ? args[3] : "GENERIC"; new ProfileRegistry().named(profile);
        new AssignmentStore(BootstrapState.get().dataDirectory()).assign(args[1], args[2], profile);
        message(sender, "<green>割り当てを保存しました。</green> <yellow>安全な反映にはサーバー再起動が必要です。/reloadは使用しないでください。</yellow>");
    }
    private void unassign(CommandSender sender, String world, String pack) throws IOException {
        new AssignmentStore(BootstrapState.get().dataDirectory()).unassign(world, pack);
        message(sender, "<green>割り当てを解除しました。</green> <yellow>反映にはサーバー再起動が必要です。</yellow>");
    }
    private void compile(CommandSender sender, String world) {
        BootstrapState state = BootstrapState.get(); DatapackCompiler compiler = new DatapackCompiler(state.dataDirectory(), state.configuration().plugin(), state.packManager());
        List<WorldAssignment> targets = state.configuration().worlds().values().stream().filter(WorldAssignment::enabled).filter(a -> world == null || a.worldName().equalsIgnoreCase(world)).toList();
        if (targets.isEmpty()) throw new CommandFailure("No enabled assignment matched");
        List<CompilationResult> results = targets.stream().map(compiler::compile).toList();
        sync(() -> { for (CompilationResult result : results) message(sender, result.success() ? "<green>Compiled " + escape(result.world()) + " -> " + result.dimensionKey() + "</green> <yellow>再起動後にdiscoverされます。</yellow>" : "<red>Failed " + escape(result.world()) + ": " + escape(String.join("; ", result.messages())) + "</red>"); });
    }
    private void doctor(CommandSender sender, String world) {
        for (DiagnosticResult result : new DoctorService().run(world)) {
            String color = switch (result.severity()) { case OK -> "green"; case WARNING -> "yellow"; case ERROR -> "red"; };
            message(sender, "<" + color + ">[" + result.severity() + "]</" + color + "> <white>" + escape(result.check()) + "</white> <gray>" + escape(result.detail()) + "</gray>");
        }
    }
    private void reload(CommandSender sender) throws IOException {
        new ConfigManager(BootstrapState.get().dataDirectory()).load();
        message(sender, "<green>設定ファイルの構文を確認しました。</green> <yellow>datapack registryはfreeze済みのため、実際の反映には再起動が必要です。</yellow>");
    }
    private void help(CommandSender sender) { message(sender, "<gold>/mwd</gold> <gray>status|packs|worlds|info|scan|assign|unassign|compile|doctor|report|reload-config|version</gray>"); }
    private void async(CommandSender sender, IoAction action) {
        message(sender, "<gray>処理を開始しました…</gray>");
        CompletableFuture.runAsync(() -> { try { action.run(); } catch (IOException | RuntimeException e) { sync(() -> message(sender, "<red>" + escape(e.getClass().getSimpleName() + ": " + e.getMessage()) + "</red>")); } });
    }
    private void sync(Runnable task) { plugin.getServer().getScheduler().runTask(plugin, task); }
    private void message(CommandSender sender, String text) { sender.sendMessage(mm.deserialize(text)); }
    private static void require(CommandSender sender, String permission) { if (!sender.hasPermission(permission)) throw new CommandFailure("権限がありません: " + permission); }
    private static void need(String[] args, int count) { if (args.length < count) throw new CommandFailure("引数が不足しています"); }
    private static Collection<String> filter(Collection<String> values, String prefix) { String lower=prefix.toLowerCase(Locale.ROOT); return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList(); }
    private static String escape(String value) { return MiniMessage.miniMessage().escapeTags(String.valueOf(value)); }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static final class CommandFailure extends RuntimeException { CommandFailure(String message) { super(message); } }
}

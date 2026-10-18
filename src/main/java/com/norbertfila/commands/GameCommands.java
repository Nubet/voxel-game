package com.norbertfila.commands;

import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.norbertfila.chat.ChatHistory;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GameCommands {
    public record Reply(String text, boolean error) {}

    public record Completion(int start, int end, String text) {}

    private record Execution(CommandSource source, List<String> output) {}

    private final CommandDispatcher<Execution> dispatcher = new CommandDispatcher<>();

    public GameCommands() {
        dispatcher.register(LiteralArgumentBuilder.<Execution>literal("seed").executes(context -> {
            context.getSource()
                    .output()
                    .add("Seed: " + context.getSource().source().seed());
            return 1;
        }));
        dispatcher.register(LiteralArgumentBuilder.<Execution>literal("help").executes(context -> {
            context.getSource()
                    .output()
                    .add(String.join(" | ", dispatcher.getAllUsage(dispatcher.getRoot(), context.getSource(), true)));
            return 1;
        }));
        var modes = LiteralArgumentBuilder.<Execution>literal("gamemode");
        for (var mode : PlayerController.Mode.values()) {
            String name = mode.name().toLowerCase(Locale.ROOT);
            modes.then(LiteralArgumentBuilder.<Execution>literal(name).executes(context -> {
                if (!context.getSource().source().setMode(mode))
                    throw error("Cannot change game mode here; move to clear, loaded space.");
                context.getSource().output().add("Game mode: " + name);
                return 1;
            }));
        }
        dispatcher.register(modes);
        dispatcher.register(LiteralArgumentBuilder.<Execution>literal("tp")
                .then(RequiredArgumentBuilder.<Execution, Double>argument("x", doubleArg())
                        .then(RequiredArgumentBuilder.<Execution, Double>argument("y", doubleArg())
                                .then(RequiredArgumentBuilder.<Execution, Double>argument("z", doubleArg())
                                        .executes(context -> {
                                            double x = getDouble(context, "x"),
                                                    y = getDouble(context, "y"),
                                                    z = getDouble(context, "z");
                                            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                                                throw error("Coordinates must be finite numbers.");
                                            if (!context.getSource()
                                                    .source()
                                                    .teleport(new PlayerState.Position(x, y, z)))
                                                throw error("Teleport unavailable or target outside world bounds.");
                                            context.getSource().output().add("Preparing teleport...");
                                            return 1;
                                        })))));
        dispatcher.register(LiteralArgumentBuilder.<Execution>literal("locate")
                .then(LiteralArgumentBuilder.<Execution>literal("biome")
                        .then(RequiredArgumentBuilder.<Execution, String>argument("biome", word())
                                .suggests((context, builder) -> {
                                    for (var biome : BiomeType.values()) {
                                        String name = biome.name().toLowerCase(Locale.ROOT);
                                        if (name.startsWith(
                                                builder.getRemaining().toLowerCase(Locale.ROOT))) builder.suggest(name);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    BiomeType biome;
                                    try {
                                        biome = BiomeType.valueOf(
                                                getString(context, "biome").toUpperCase(Locale.ROOT));
                                    } catch (IllegalArgumentException invalid) {
                                        throw error("Unknown biome: " + getString(context, "biome"));
                                    }
                                    if (!context.getSource().source().locate(biome))
                                        throw error("Biome search unavailable or already running.");
                                    context.getSource()
                                            .output()
                                            .add("Locating " + biome.name().toLowerCase(Locale.ROOT) + "...");
                                    return 1;
                                }))));
        dispatcher.register(LiteralArgumentBuilder.<Execution>literal("give")
                .then(LiteralArgumentBuilder.<Execution>literal("@s")
                        .then(RequiredArgumentBuilder.<Execution, String>argument("item", word())
                                .executes(context -> give(context, 1))
                                .then(RequiredArgumentBuilder.<Execution, Integer>argument("count", integer(1, 64))
                                        .executes(context -> give(context, getInteger(context, "count")))))));
    }

    public Reply execute(String input, CommandSource source) {
        if (input == null || !input.startsWith("/") || input.codePointCount(0, input.length()) > ChatHistory.MAX_INPUT)
            return new Reply("Invalid command input.", true);
        var execution = new Execution(source, new ArrayList<>());
        try {
            dispatcher.execute(input.substring(1), execution);
            return new Reply(String.join(" | ", execution.output()), false);
        } catch (CommandSyntaxException invalid) {
            return new Reply(invalid.getMessage(), true);
        }
    }

    public List<Completion> suggest(String input, int caret) {
        if (input == null
                || !input.startsWith("/")
                || caret < 1
                || caret > input.length()
                || input.codePointCount(0, input.length()) > ChatHistory.MAX_INPUT) return List.of();
        var parsed = dispatcher.parse(input.substring(1, caret), new Execution(null, List.of()));
        return dispatcher.getCompletionSuggestions(parsed).join().getList().stream()
                .map(suggestion -> new Completion(
                        suggestion.getRange().getStart() + 1,
                        suggestion.getRange().getEnd() + 1,
                        suggestion.getText()))
                .toList();
    }

    public String usage(String input) {
        if (input == null || !input.startsWith("/")) return "";
        String name = input.substring(1).split(" ", 2)[0];
        var node = dispatcher.getRoot().getChild(name);
        return node == null
                ? ""
                : String.join(" | ", dispatcher.getAllUsage(node, new Execution(null, List.of()), true));
    }

    private static int give(CommandContext<Execution> context, int count) throws CommandSyntaxException {
        String name = getString(context, "item");
        for (int id = 1; id <= 255; id++) {
            if (!BlockId.isValid((byte) id)) continue;
            if (!BlockRegistry.DEFAULT.definition((byte) id).name().equals(name)) continue;
            int remaining = context.getSource().source().give((byte) id, count);
            context.getSource()
                    .output()
                    .add("Added " + (count - remaining) + " x " + name
                            + (remaining == 0 ? "" : "; no space for " + remaining));
            return count - remaining;
        }
        throw error("Unknown item: " + name);
    }

    private static CommandSyntaxException error(String text) {
        return new SimpleCommandExceptionType(() -> text).create();
    }
}

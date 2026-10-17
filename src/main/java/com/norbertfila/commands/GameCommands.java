package com.norbertfila.commands;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.norbertfila.chat.ChatHistory;
import com.norbertfila.player.PlayerController;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GameCommands {
    public record Reply(String text, boolean error) {}

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

package com.sci.torcherino.benchmarkharness;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;

public final class BenchmarkCommand extends CommandBase {
    private final BenchmarkController controller;

    public BenchmarkCommand(BenchmarkController controller) {
        this.controller = controller;
    }

    @Override
    public String getName() {
        return "torcherino-bench";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/torcherino-bench <run furnace-suite "
            + "[all|vanilla|thermal|thermal80|enderio] [diagnostic|timing]"
            + "|status|export [path]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        if (args.length == 0) {
            throw new CommandException(getUsage(sender));
        }

        if ("run".equalsIgnoreCase(args[0])) {
            run(sender, args);
            return;
        }
        if ("status".equalsIgnoreCase(args[0])) {
            sendStatus(sender);
            return;
        }
        if ("export".equalsIgnoreCase(args[0])) {
            export(sender, args);
            return;
        }

        throw new CommandException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(
        MinecraftServer server,
        ICommandSender sender,
        String[] args,
        @Nullable BlockPos targetPos
    ) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "run", "status", "export");
        }
        if (args.length == 2 && "run".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "furnace-suite");
        }
        if (args.length == 3
            && "run".equalsIgnoreCase(args[0])
            && "furnace-suite".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(
                args,
                "all",
                "vanilla",
                "thermal",
                "thermal80",
                "enderio"
            );
        }
        if (args.length == 4
            && "run".equalsIgnoreCase(args[0])
            && "furnace-suite".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "diagnostic", "timing");
        }
        return Collections.emptyList();
    }

    private void run(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || !"furnace-suite".equalsIgnoreCase(args[1])) {
            throw new CommandException(
                "/torcherino-bench run furnace-suite "
                    + "[all|vanilla|thermal|thermal80|enderio] "
                    + "[diagnostic|timing]"
            );
        }
        String layer = args.length >= 3 ? args[2] : "all";
        String mode = args.length >= 4 ? args[3] : "diagnostic";
        BenchmarkController.RunRequestResult result =
            controller.startFurnaceSuite(layer, mode);
        send(sender, result.getMessage());
    }

    private void sendStatus(ICommandSender sender) {
        for (String line : controller.describeStatus()) {
            send(sender, line);
        }
    }

    private void export(ICommandSender sender, String[] args) {
        File target = args.length >= 2
            ? new File(args[1])
            : controller.defaultExportDirectory();
        BenchmarkController.ExportResult result = controller.export(target);
        send(sender, result.getMessage());
    }

    private static void send(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }
}

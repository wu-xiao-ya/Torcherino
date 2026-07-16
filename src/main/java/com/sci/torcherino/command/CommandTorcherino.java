package com.sci.torcherino.command;

import com.sci.torcherino.acceleration.AccelerationProfiler;
import com.sci.torcherino.acceleration.AccelerationService;
import com.sci.torcherino.acceleration.AdapterRegistry;
import com.sci.torcherino.acceleration.AdapterReport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CommandTorcherino extends CommandBase {
    @Override
    public String getName() {
        return "torcherino";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/torcherino <adapters|plan|profile start|stop|status>";
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

        if ("adapters".equalsIgnoreCase(args[0])) {
            showAdapters(sender);
            return;
        }
        if ("plan".equalsIgnoreCase(args[0])) {
            showPlans(sender);
            return;
        }
        if ("profile".equalsIgnoreCase(args[0])) {
            profile(sender, args);
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
            return getListOfStringsMatchingLastWord(args, "adapters", "plan", "profile");
        }
        if (args.length == 2 && "profile".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "start", "stop", "status");
        }
        return Collections.emptyList();
    }

    private static void showAdapters(ICommandSender sender) {
        for (AdapterReport report : AdapterRegistry.getInstance().getReports()) {
            send(
                sender,
                report.getId()
                    + " [" + report.getClassification() + "] "
                    + (report.isEnabled() ? "enabled" : "disabled")
                    + " " + report.getSignature()
                    + " - " + report.getDetail()
            );
        }
    }

    private static void showPlans(ICommandSender sender) {
        List<String> plans = AccelerationService.describePlans();
        if (plans.isEmpty()) {
            send(sender, "No active Torcherino world plans");
            return;
        }
        for (String plan : plans) {
            send(sender, plan);
        }
    }

    private static void profile(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new CommandException("/torcherino profile <start|stop|status>");
        }

        AccelerationProfiler profiler = AccelerationProfiler.getInstance();
        if ("start".equalsIgnoreCase(args[1])) {
            profiler.start();
            send(sender, "Torcherino profiler started");
            return;
        }
        if ("stop".equalsIgnoreCase(args[1])) {
            profiler.stop();
            send(sender, "Torcherino profiler stopped");
            return;
        }
        if ("status".equalsIgnoreCase(args[1])) {
            send(sender, "Profiler " + (profiler.isEnabled() ? "running" : "stopped"));
            for (Map<String, Object> row : profiler.snapshot(10)) {
                send(sender, Arrays.toString(row.entrySet().toArray()));
            }
            return;
        }
        throw new CommandException("/torcherino profile <start|stop|status>");
    }

    private static void send(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }
}

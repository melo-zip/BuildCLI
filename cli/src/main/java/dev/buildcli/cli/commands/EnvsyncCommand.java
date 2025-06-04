package dev.buildcli.cli.commands;

import dev.buildcli.cli.environment.EnvSync;
import dev.buildcli.core.domain.BuildCLICommand;
import picocli.CommandLine.Command;

@Command(
        name = "envsync",
        aliases = {"env"},
        description = "Synchronizes environment variables by allowing import and export operations.",
        mixinStandardHelpOptions = true
)
public class EnvsyncCommand implements BuildCLICommand {
    @Override
    public void run() { new EnvSync().sync(); }
}

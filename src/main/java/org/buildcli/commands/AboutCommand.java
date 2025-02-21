package org.buildcli.commands;

import org.buildcli.domain.BuildCLICommand;
import org.buildcli.utils.BuildCLIIntro;
import picocli.CommandLine.Command;

@Command(name = "about", aliases = {"-a"}, description = "Displays information about BuildCLI, including its purpose and usage.", mixinStandardHelpOptions = true)
public class AboutCommand implements BuildCLICommand {

  @Override
  public void run() {
    BuildCLIIntro.about();
  }
}

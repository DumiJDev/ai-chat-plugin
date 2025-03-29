package dev.buildcli.plugin.bdcliaichat;

import dev.buildcli.plugin.BuildCLICommandPlugin;
import dev.buildcli.plugin.bdcliaichat.utils.Repl;
import picocli.CommandLine.Command;

@Command(name = "chat", description = "Interactive AI Chat", mixinStandardHelpOptions = true)
public class BdcliaichatCommand extends BuildCLICommandPlugin {
  @Override
  public void run() {
    try (var repl = new Repl()) {
      repl.start();
    }
  }

  @Override
  public String version() {
    return "0.0.1-SNAPSHOT";
  }

  @Override
  public String name() {
    return "bdcli-ai-chat";
  }

  @Override
  public String description() {
    return "Build CLI Plugin";
  }

  @Override
  public String[] parents() {
    return new String[]{"ai"};
  }
}

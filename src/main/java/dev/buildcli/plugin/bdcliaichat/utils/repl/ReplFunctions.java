package dev.buildcli.plugin.bdcliaichat.utils.repl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.buildcli.core.utils.BeautifyShell.*;

/**
 * Utility class providing various helper functions for the REPL interface.
 * These functions handle commands like help display, variable listing, and screen clearing.
 */
public final class ReplFunctions {

  // Available commands with descriptions for help display
  private static final Map<String, String> COMMANDS = new LinkedHashMap<>();
  private static final Logger log = LoggerFactory.getLogger(ReplFunctions.class);


  static {
    COMMANDS.put("exit", "Exit the REPL");
    COMMANDS.put(":help", "Display this help message");
    COMMANDS.put(":var", "Set a variable");
    COMMANDS.put(":vars", "List defined variables in the current session");
    COMMANDS.put(":clear", "Clear the screen");
  }

  /**
   * Display a help message showing available commands and their descriptions.
   *
   * @param repl The Repl instance to use for output
   */
  public static void printHelp(Repl repl) {
    repl.println(bold("\nAvailable Commands:"));

    int maxCommandLength = COMMANDS.keySet().stream()
        .mapToInt(String::length)
        .max()
        .orElse(10);

    COMMANDS.forEach((command, description) -> {
      String paddedCommand = command + " ".repeat(maxCommandLength - command.length() + 2);
      repl.println(yellowFg(paddedCommand) + description);
    });

    repl.println("\n" + italic("Any other input will be sent to the AI assistant."));
  }

  /**
   * List variables defined in the current REPL session.
   *
   * @param repl The Repl instance to use for output
   */
  public static void listVariables(Repl repl) {
    // This would normally fetch variables from a context or session storage
    // For now, we'll display placeholder content

    repl.println(bold("\nDefined Variables:"));

    if (repl.variables.isEmpty()) {
      repl.println(italic("No variables are currently defined."));
      repl.println();
      repl.println("You can define variables using assignment syntax:");
      repl.println(yellowFg("var") + " myVariable = \"some value\"");
      repl.println();
      repl.println("Once defined, variables can be referenced in subsequent messages.");
    } else {
      repl.variables.forEach((key, value) -> {
        repl.println(yellowFg(key) + " = " + value);
      });
    }
  }

  /**
   * Clear the terminal screen.
   *
   * @param repl The Repl instance to use for output
   */
  public static void clearScreen(Repl repl) {
    try {
      // ANSI escape sequence to clear screen and move cursor to home position
      repl.print("\033[H\033[2J");
      repl.print("\033[0;0H");

      // Alternative approach for systems where ANSI escape codes might not work
      String os = System.getProperty("os.name").toLowerCase();

      if (os.contains("windows")) {
        // On Windows, try using the cls command
        new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
      } else {
        // On Unix-like systems, try using the clear command
        new ProcessBuilder("clear").inheritIO().start().waitFor();
      }

      // Display a welcome message after clearing
      printBanner(repl);
    } catch (IOException | InterruptedException e) {
      // If the clear command fails, fall back to printing newlines
      for (int i = 0; i < 50; i++) {
        repl.println("");
      }
      repl.printError("Note: Screen clearing may not work perfectly in all terminals");
    }
  }

  public static void printBanner(Repl repl) {
    repl.println(table(List.of(
        "BuildCLI AI Chat",
        "0.0.1"
    )));
  }

  public static void setVariable(Repl repl, String args) {
    var parts = args.split("=");

    if (parts.length != 2) {
      log.error("Invalid variable syntax");
      return;
    }

    var name = parts[0].trim();
    var value = parts[1].trim();

    repl.variables.put(name, value);

    repl.println(brightGreenFg("Set variable " + name + " " + value));
  }
}
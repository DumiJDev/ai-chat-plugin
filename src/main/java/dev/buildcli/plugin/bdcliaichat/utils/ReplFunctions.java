package dev.buildcli.plugin.bdcliaichat.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.buildcli.core.utils.BeautifyShell.*;

/**
 * Utility class providing various helper functions for the REPL interface.
 * These functions handle commands like help display, variable listing, and screen clearing.
 */
public class ReplFunctions {

  // Available commands with descriptions for help display
  private static final Map<String, String> COMMANDS = new LinkedHashMap<>();

  static {
    COMMANDS.put("exit", "Exit the REPL");
    COMMANDS.put(":help", "Display this help message");
    COMMANDS.put(":functions", "List available utility functions");
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
   * List available utility functions that can be used within the REPL.
   *
   * @param repl The Repl instance to use for output
   */
  public static void listFunctions(Repl repl) {
    repl.println(bold("\nAvailable Utility Functions:"));

    Map<String, String> functions = new LinkedHashMap<>();
    functions.put("!help()", "Display information about using utility functions");
    functions.put("!code(language, code)", "Format and syntax highlight code");
    functions.put("!math(expression)", "Evaluate a mathematical expression");
    functions.put("!search(query)", "Search for information (when available)");
    functions.put("!file(path)", "Read the content of a file");
    functions.put("!url(address)", "Fetch the content from a URL");

    int maxFunctionLength = functions.keySet().stream()
        .mapToInt(String::length)
        .max()
        .orElse(15);

    functions.forEach((function, description) -> {
      String paddedFunction = function + " ".repeat(maxFunctionLength - function.length() + 2);
      repl.println(cyanFg(paddedFunction) + description);
    });

    repl.println("\n" + italic("Example: !math(sqrt(16) + 10)"));
    repl.println(italic("Note: Functions can be used within your messages to the AI."));
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

    repl.println(italic("No variables are currently defined."));
    repl.println();
    repl.println("You can define variables using assignment syntax:");
    repl.println(yellowFg("var") + " myVariable = \"some value\"");
    repl.println();
    repl.println("Once defined, variables can be referenced in subsequent messages.");
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
}
package dev.buildcli.plugin.bdcliaichat.utils;

import dev.buildcli.core.actions.ai.AIChat;
import dev.buildcli.core.actions.ai.AIService;
import dev.buildcli.core.actions.ai.factories.GeneralAIServiceFactory;
import dev.buildcli.core.utils.ai.IAParamsUtils;
import dev.buildcli.core.utils.async.Async;
import dev.buildcli.core.utils.config.ConfigContextLoader;
import dev.buildcli.core.utils.markdown.MarkdownInterpreter;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.buildcli.core.constants.ConfigDefaultConstants.AI_MODEL;
import static dev.buildcli.core.constants.ConfigDefaultConstants.AI_VENDOR;
import static dev.buildcli.core.utils.BeautifyShell.*;
import static java.lang.Thread.sleep;

/**
 * A Read-Eval-Print-Loop (REPL) for interacting with AI services in a terminal environment.
 * This class provides a chat-like interface for communication with an AI model.
 */
public class Repl implements AutoCloseable {
  // Substituindo o Logger Java pelo SLF4J
  private static final Logger logger = LoggerFactory.getLogger(Repl.class);

  private static final int DEFAULT_TYPING_DELAY_MS = 20; // Reduced from 300ms for better UX
  private static final String PROMPT = "me > ";
  private static final String CONTINUATION_PROMPT = "... ";
  private static final String[] THINKING_ANIMATION = {
      "⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"  // Braille pattern animation
  };
  private static final int THINKING_ANIMATION_DELAY_MS = 100;

  private final Terminal terminal;
  private final LineReader reader;
  private final AIService aiService;
  private final int typingDelay;
  private final MarkdownInterpreter markdownInterpreter;

  /**
   * Creates a new REPL instance with default settings.
   */
  public Repl() {
    this(DEFAULT_TYPING_DELAY_MS);
  }

  /**
   * Creates a new REPL instance with a custom typing delay.
   *
   * @param typingDelay The delay in milliseconds between characters when displaying AI responses
   */
  public Repl(int typingDelay) {
    try {
      this.typingDelay = typingDelay;

      this.terminal = TerminalBuilder.builder()
          .system(true)
          .build();

      this.terminal.enterRawMode();

      // Configure available commands with descriptions for better auto-completion
      List<String> commands = Arrays.asList(
          "exit", ":help", ":functions", ":vars", ":clear"
      );

      Completer completer = new AggregateCompleter(
          new StringsCompleter(commands)
      );

      // Configure line reader with history and completion
      reader = LineReaderBuilder.builder()
          .terminal(terminal)
          .parser(new DefaultParser())
          .completer(completer)
          .history(new DefaultHistory())
          .variable(LineReader.HISTORY_FILE, System.getProperty("user.home") + "/.ai_db_repl_history")
          .option(LineReader.Option.CASE_INSENSITIVE, true)
          .option(LineReader.Option.AUTO_FRESH_LINE, true)
          .build();

      // Setup AI service from configuration
      var config = ConfigContextLoader.getAllConfigs();
      var aiParams = IAParamsUtils.createAIParams(
          config.getProperty(AI_MODEL).orElse(null),
          config.getProperty(AI_VENDOR).orElse(null)
      );

      aiService = new GeneralAIServiceFactory().create(aiParams);
    } catch (IOException e) {
      logger.error("Failed to initialize REPL", e);
      throw new RuntimeException("Failed to initialize terminal: " + e.getMessage(), e);
    }
    markdownInterpreter = new MarkdownInterpreter();
  }

  /**
   * Starts the REPL and enters the main interaction loop.
   */
  public void start() {
    printSuccess("AI REPL started. Type 'exit' to quit.");
    printSuccess("Type ':help' for available commands.");

    try {
      mainLoop();
    } catch (IOException e) {
      logger.error("Error in REPL operation", e);
      printError("Fatal error: " + e.getMessage());
    }
  }

  /**
   * The main loop of the REPL that handles user input and AI responses.
   */
  private void mainLoop() throws IOException {
    while (true) {
      try {
        String line = reader.readLine(PROMPT);

        if (line == null || line.trim().isEmpty()) {
          continue;
        }

        line = line.trim();

        // Handle exit command
        if ("exit".equalsIgnoreCase(line)) {
          printSuccess("Exiting REPL...");
          break;
        }

        // Handle special commands
        if (line.startsWith(":")) {
          handleSpecialCommand(line);
          continue;
        }

        // Handle multiline input
        if (!isCompleteInput(line)) {
          line = readMultilineInput(line);
        }

        // Process input with the prompt interpreter to enrich with file/URL content
        String enrichedPrompt = new AIPromptInterpreter(line).interpret();

        // Get AI response
        String aiResponse = generateAIResponse(enrichedPrompt);
        if (aiResponse != null) {
          displayAIResponse(markdownInterpreter.interpret(aiResponse));
        }

      } catch (UserInterruptException e) {
        printError("Interrupted");
      } catch (EndOfFileException e) {
        printSuccess("Exiting REPL...");
        break;
      } catch (Exception e) {
        logger.warn("Error processing input", e);
        printError("Error: " + e.getMessage());
      }
    }
  }

  /**
   * Displays the AI response with a typing effect.
   *
   * @param aiMessage The AI response text
   */
  private void displayAIResponse(String aiMessage) {
    print("\nAI > ");

    for (var word : aiMessage.split(" ")) {
      if (word.equals("\n")) {
        println();
      } else {
        print(word + " ");
      }

      try {
        sleep(Duration.ofMillis(typingDelay));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Typing display interrupted", e);
        break;
      }
    }

    println("\n");
  }

  /**
   * Generates a response from the AI service.
   *
   * @param userMessage The user's message
   * @return The AI's response
   */
  private String generateAIResponse(String userMessage) {
    AtomicBoolean animationRunning = new AtomicBoolean(true);
    Thread animationThread = null;

    try {
      animationThread = new Thread(() -> {
        try {
          runThinkingAnimation(animationRunning);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      animationThread.start();

      var task = Async.run(() -> aiService.generate(
          new AIChat("You are a Buildcli Assistant, you talk about tech", userMessage)
      ));

      task.catchAny(throwable -> {
        animationRunning.set(false);
        return null;
      });

      String response = task.await();

      animationRunning.set(false);
      if (animationThread != null && animationThread.isAlive()) {
        animationThread.join(1000);
        if (animationThread.isAlive()) {
          animationThread.interrupt();
        }
      }

      terminal.puts(InfoCmp.Capability.carriage_return);
      terminal.writer().print(" ".repeat(30));
      terminal.puts(InfoCmp.Capability.carriage_return);
      terminal.writer().flush();

      return new AIPromptInterpreter(response).interpret();
    } catch (InterruptedException e) {
      animationRunning.set(false);
      if (animationThread != null && animationThread.isAlive()) {
        animationThread.interrupt();
      }

      Thread.currentThread().interrupt();
      logger.warn("AI response generation interrupted", e);
      throw new RuntimeException("AI response generation was interrupted", e);
    }
  }


  private void runThinkingAnimation(AtomicBoolean running) throws InterruptedException {
    int frame = 0;
    String baseMessage = "Thinking";

    while (running.get()) {
      String animChar = THINKING_ANIMATION[frame % THINKING_ANIMATION.length];

      int dots = (frame / THINKING_ANIMATION.length) % 4;
      String dotsStr = ".".repeat(dots);

      String spaces = " ".repeat(3 - dots);

      String message = animChar + " " + italic(baseMessage + dotsStr + spaces);

      terminal.puts(InfoCmp.Capability.carriage_return);

      print(content(message).blueFg());

      frame++;
      sleep(THINKING_ANIMATION_DELAY_MS);
    }
  }

  /**
   * Reads multiline input if the initial input is incomplete.
   *
   * @param initialLine The first line of input
   * @return The complete multiline input
   */
  private String readMultilineInput(String initialLine) {
    StringBuilder inputBuilder = new StringBuilder(initialLine);

    while (!isCompleteInput(inputBuilder.toString())) {
      String nextLine = reader.readLine(CONTINUATION_PROMPT);
      if (nextLine == null) {
        break;
      }
      inputBuilder.append("\n").append(nextLine);
    }

    return inputBuilder.toString();
  }

  /**
   * Determines if the input is complete and ready for processing.
   * This implementation checks for balanced braces and other indicators of complete input.
   *
   * @param input The input to check
   * @return true if the input is complete, false otherwise
   */
  private boolean isCompleteInput(String input) {
    if (input.endsWith("\\")) {
      return false;
    }

    // Count opening and closing braces
    int openBraces = 0;
    int openBrackets = 0;
    int openParens = 0;

    for (char c : input.toCharArray()) {
      switch (c) {
        case '{':
          openBraces++;
          break;
        case '}':
          openBraces--;
          break;
        case '[':
          openBrackets++;
          break;
        case ']':
          openBrackets--;
          break;
        case '(':
          openParens++;
          break;
        case ')':
          openParens--;
          break;
      }
    }

    return openBraces == 0 && openBrackets == 0 && openParens == 0;
  }

  /**
   * Handles special commands starting with ':'.
   *
   * @param command The command string
   */
  private void handleSpecialCommand(String command) {
    String[] parts = command.substring(1).split("\\s+", 2);
    String cmd = parts[0].toLowerCase();
    String args = parts.length > 1 ? parts[1] : "";

    switch (cmd) {
      case "help":
        ReplFunctions.printHelp(this);
        break;
      case "functions":
        ReplFunctions.listFunctions(this);
        break;
      case "vars":
        ReplFunctions.listVariables(this);
        break;
      case "clear":
        ReplFunctions.clearScreen(this);
        break;
      default:
        printError("Unknown command: " + cmd);
        ReplFunctions.printHelp(this);
        break;
    }
  }

  /**
   * Prints an error message in red.
   *
   * @param message The error message
   */
  public void printError(String message) {
    println(redFg(message));
  }

  /**
   * Prints a success message in green.
   *
   * @param message The success message
   */
  public void printSuccess(String message) {
    println(greenFg(message));
  }

  /**
   * Prints a message to the terminal.
   *
   * @param message The message to print
   */
  public void println(Object message) {
    terminal.writer().println(message);
    terminal.writer().flush();
  }

  /**
   * Prints \n to the terminal.
   */
  public void println() {
    terminal.writer().println();
    terminal.writer().flush();
  }

  /**
   * Prints a message to the terminal without a newline.
   *
   * @param message The message to print
   */
  public void print(Object message) {
    terminal.writer().print(message);
    terminal.writer().flush();
  }

  /**
   * Closes the REPL and releases resources.
   */
  @Override
  public void close() {
    try {
      if (terminal != null) {
        terminal.close();
      }
    } catch (IOException e) {
      logger.warn("Error closing terminal", e);
    }
  }
}
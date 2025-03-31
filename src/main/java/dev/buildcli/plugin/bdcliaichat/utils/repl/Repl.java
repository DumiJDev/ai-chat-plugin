package dev.buildcli.plugin.bdcliaichat.utils.repl;

import dev.buildcli.core.actions.ai.AIChat;
import dev.buildcli.core.actions.ai.AIService;
import dev.buildcli.core.utils.async.Async;
import dev.buildcli.core.utils.markdown.MarkdownInterpreter;
import dev.buildcli.plugin.bdcliaichat.utils.AIPromptInterpreter;
import dev.buildcli.plugin.bdcliaichat.utils.speech.AISpeech;
import dev.buildcli.plugin.bdcliaichat.utils.speech.FreettsAISpeech;
import org.jline.builtins.Completers;
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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.buildcli.core.constants.ConfigDefaultConstants.AI_VENDOR;
import static dev.buildcli.core.utils.BeautifyShell.*;
import static dev.buildcli.core.utils.config.ConfigContextLoader.getAllConfigs;
import static java.lang.System.getProperty;
import static java.lang.Thread.sleep;
import static java.time.Duration.ofMillis;

/**
 * A Read-Eval-Print-Loop (REPL) for interacting with AI services in a terminal environment.
 * This class provides a chat-like interface for communication with an AI model.
 */
public class Repl implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(Repl.class);

  private static final String PROMPT;
  private static final String AI_NAME;
  private static final String[] THINKING_ANIMATION = {
      "⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"  // Braille pattern animation
  };
  private static final int THINKING_ANIMATION_DELAY_MS = 100;

  private static final String AI_PROMPT = getAllConfigs().getProperty("buildcli.ai.chat.prompt").orElse("You are a funny assistant, you speak about: tech, tech news, STEAM topic. You cannot talk about gambiarra or religion or politic (or Javascript, its a joke, ignore if you want)");

  static {
    PROMPT = content(getProperty("user.name").toUpperCase()).padding(0, 1).brightBlueBg().blackFg().toString();
    AI_NAME = content(getAllConfigs().getProperty(AI_VENDOR).orElse("jlama").toUpperCase()).padding(0, 1).brightGreenBg().blackFg().toString();
  }

  final Map<String, String> variables = new HashMap<>();
  private final Terminal terminal;
  private final LineReader reader;
  private final MarkdownInterpreter markdownInterpreter;
  private final AISpeech aiSpeech;
  private AIService aiService;

  /**
   * Creates a new REPL instance with default settings.
   */
  public Repl() {
    try {

      this.terminal = TerminalBuilder.builder()
          .system(true)
          .build();

      this.terminal.enterRawMode();

      // Configure available commands with descriptions for better auto-completion
      List<String> commands = Arrays.asList(
          "exit", ":help", ":clear", ":var", ":vars", ":new"
      );

      Completer completer = new AggregateCompleter(
          new StringsCompleter(commands),
          new Completers.FileNameCompleter(),
          new Completers.DirectoriesCompleter(new File("."))
      );

      // Configure line reader with history and completion
      reader = LineReaderBuilder.builder()
          .terminal(terminal)
          .parser(new DefaultParser())
          .completer(completer)
          .history(new DefaultHistory())
          .variable(LineReader.HISTORY_FILE, getProperty("user.home") + "/.ai_db_repl_history")
          .option(LineReader.Option.CASE_INSENSITIVE, true)
          .option(LineReader.Option.AUTO_FRESH_LINE, true)
          .build();

      // Setup AI service from configuration

      aiService = ReplFunctions.iaChatInit();
    } catch (IOException e) {
      logger.error("Failed to initialize REPL", e);
      throw new RuntimeException("Failed to initialize terminal: " + e.getMessage(), e);
    }
    markdownInterpreter = new MarkdownInterpreter();
    aiSpeech = new FreettsAISpeech();
  }

  /**
   * Starts the REPL and enters the main interaction loop.
   */
  public void start() {
    ReplFunctions.clearScreen(this);

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
        String line = reader.readLine(PROMPT + "\n");

        if (line == null || line.trim().isEmpty()) {
          continue;
        }

        line = line.trim();

        // Handle exit command
        if ("exit".equalsIgnoreCase(line)) {
          printSuccess("Goodbye! ';'");
          break;
        }

        // Handle special commands
        if (line.startsWith(":")) {
          handleSpecialCommand(line);
          continue;
        }

        // Process input with the prompt interpreter to enrich with file/URL content
        String enrichedPrompt = new AIPromptInterpreter(line).interpret();

        // Get AI response
        String aiResponse = chat(enrichedPrompt);
        if (aiResponse != null) {
          displayAIResponse(markdownInterpreter.interpret(aiResponse));
        }

      } catch (UserInterruptException e) {
        printError("Interrupted");
      } catch (EndOfFileException e) {
        printSuccess("Goodbye! ';'");
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
    println();
    print(AI_NAME);
    println();

    if ("true".equalsIgnoreCase(variables.getOrDefault("text", "true"))) {
      for (var line : aiMessage.split("\n")) {
        var lineSplit = line.split(" ");
        for (var word : lineSplit) {
          print(word + " ");

          try {
            sleep(ofMillis(100 / lineSplit.length));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Typing display interrupted", e);
            break;
          }
        }
        println();
        try {
          sleep(ofMillis(10));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          logger.warn("Typing display interrupted", e);
          break;
        }
      }
    }

    if ("true".equalsIgnoreCase(variables.getOrDefault("voice", "false"))) {
      aiSpeech.speak(aiMessage);
    }

    println();
  }

  /**
   * Generates a response from the AI service.
   *
   * @param userMessage The user's message
   * @return The AI's response
   */
  private String chat(String userMessage) {
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
          new AIChat(AI_PROMPT, userMessage, true)
      )).catchAny(throwable -> {
        animationRunning.set(false);
        return "Ops! Can you try again?";
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
   * Handles special commands starting with ':'.
   *
   * @param command The command string
   */
  private void handleSpecialCommand(String command) {
    if (!command.matches(":[A-Za-z]+( [A-Za-z0-9]+( )?=( )?.+)?")) {
      logger.warn("Invalid command: {}", command);
      return;
    }

    String[] parts = command.substring(1).trim().split("\\s+", 2);
    String cmd = parts[0].toLowerCase();

    switch (cmd) {
      case "help":
        ReplFunctions.printHelp(this);
        break;
      case "clear":
        ReplFunctions.clearScreen(this);
        break;
      case "vars":
        ReplFunctions.listVariables(this);
        break;
      case "new":
        aiService = ReplFunctions.iaChatInit();
        ReplFunctions.clearScreen(this);
        break;
      case "var":
        if (parts.length > 1) {
          String args = parts[1];
          ReplFunctions.setVariable(this, args);
        } else {
          logger.warn("Invalid variable: {}", parts[0]);
        }
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
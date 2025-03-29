package dev.buildcli.plugin.bdcliaichat.utils;

import dev.buildcli.core.utils.OS;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Class responsible for interpreting an AI prompt and enriching it with contents
 * from local files and URLs found in the text.
 */
public class AIPromptInterpreter {
  private static final Logger LOGGER = Logger.getLogger(AIPromptInterpreter.class.getName());
  private static final int CONNECTION_TIMEOUT_MS = 5000;
  private static final HttpClient client = HttpClient.newHttpClient();

  private final String prompt;
  private final Pattern filePattern;
  private final Pattern urlPattern;

  /**
   * Creates a new prompt interpreter.
   *
   * @param prompt The prompt text to interpret
   */
  public AIPromptInterpreter(String prompt) {
    this.prompt = prompt;
    this.filePattern = Pattern.compile(OS.isWindows()
        ? "([A-Z]:(\\\\[A-Za-z0-9_.-]+(\\s[A-Za-z0-9_.-]+)*)*(\\\\)?+)"
        : "(/[A-Za-z0-9_.-]+(\\s[A-Za-z0-9_.-]+)*/?)");
    this.urlPattern = Pattern.compile("https?://([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?");
  }

  /**
   * Interprets the prompt, adding contents from files and URLs found.
   *
   * @return The prompt enriched with the contents
   */
  public String interpret() {
    Map<String, String> fileMap = extractFileContents();
    Map<String, String> urlMap = extractUrlContents();

    return buildEnrichedPrompt(fileMap, urlMap);
  }

  /**
   * Extracts the content of files found in the prompt.
   *
   * @return A map with file paths and their contents
   */
  private Map<String, String> extractFileContents() {
    Map<String, String> fileMap = new HashMap<>();
    var fileMatcher = filePattern.matcher(prompt);

    while (fileMatcher.find()) {
      String filePath = fileMatcher.group();
      Path path = Paths.get(filePath);

      if (Files.isRegularFile(path)) {
        try {
          String content = Files.readString(path, StandardCharsets.UTF_8);
          fileMap.put(filePath, content);
        } catch (IOException e) {
          LOGGER.log(Level.WARNING, "Failed to read file: " + filePath, e);
          fileMap.put(filePath, "Error reading file: " + e.getMessage());
        }
      }
    }

    return fileMap;
  }

  /**
   * Extracts the content of URLs found in the prompt.
   *
   * @return A map with URLs and their contents
   */
  private Map<String, String> extractUrlContents() {
    Map<String, String> urlMap = new HashMap<>();
    var urlMatcher = urlPattern.matcher(prompt);

    while (urlMatcher.find()) {
      String urlStr = urlMatcher.group();
      try {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlStr))
            .timeout(Duration.ofMillis(CONNECTION_TIMEOUT_MS))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String content = response.body();

        if (response.statusCode() == 200) {
          urlMap.put(urlStr, content);
        }

      } catch (IOException | InterruptedException e) {
        LOGGER.log(Level.WARNING, "Failed trying to access: " + urlStr, e);
        urlMap.put(urlStr, "Error accessing: " + e.getMessage());
      }
    }

    return urlMap;
  }


  /**
   * Builds the enriched prompt with the extracted contents.
   *
   * @param fileMap Map of files and their contents
   * @param urlMap  Map of URLs and their contents
   * @return The enriched prompt
   */
  private String buildEnrichedPrompt(Map<String, String> fileMap, Map<String, String> urlMap) {
    StringBuilder builder = new StringBuilder(prompt);

    if (!fileMap.isEmpty()) {
      builder.append("\n\n").append("Files:");
      for (Map.Entry<String, String> entry : fileMap.entrySet()) {
        builder.append("\n").append(entry.getKey()).append(":\n")
            .append("```\n").append(entry.getValue()).append("\n```\n");
      }
    }

    if (!urlMap.isEmpty()) {
      builder.append("\n\n").append("URLs:");
      for (Map.Entry<String, String> entry : urlMap.entrySet()) {
        builder.append("\n").append(entry.getKey()).append(":\n")
            .append("```\n").append(entry.getValue()).append("\n```\n");
      }
    }

    return builder.toString();
  }
}
package dev.buildcli.cli.environment;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnixEnvironmentManager extends EnvironmentManager {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "^\\s*export\\s+(?<key>[^=]+)\\s*=\\s*(?<value>.*?)\\s*(#.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void setVariable(String key, String value, String system) {
        String rcFile = getShellRcFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rcFile, true))) {
            writer.write("\nexport " + key + "=\"" + escapeValue(value) + "\"");
            System.out.println("Variable " + key + " added/changed in " + rcFile);
        } catch (IOException e) {
            System.err.println("Error adding variable: " + e.getMessage());
        }
    }

    @Override
    public void deleteVariable(String key, String system) {
        Path rcPath = Paths.get(getShellRcFile());
        try {
            List<String> lines = Files.readAllLines(rcPath, StandardCharsets.UTF_8);

            List<String> newLines = lines.stream()
                    .filter(line -> !isVariableLine(line, key))
                    .collect(Collectors.toList());

            if (newLines.size() != lines.size()) {
                Files.write(rcPath, newLines, StandardCharsets.UTF_8);
            } else {
                System.out.println("Variable " + key + " not found");
            }
        } catch (IOException e) {
            System.err.println("Error deleting variable: " + e.getMessage());
        }
    }

    @Override
    public boolean variableExists(String key, String system) {
        Path filePath = Paths.get(getShellRcFile());
        try (Stream<String> lines = Files.lines(filePath)) {
            return lines.anyMatch(line -> isVariableLine(line, key));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Map<String, String> exportVariables(List<String> keys, String system) {
        Map<String, String> variables = new HashMap<>();
        Path filePath = Paths.get(getShellRcFile());
        try (Stream<String> lines = Files.lines(filePath)) {
            lines.forEach(line -> parseVariableLine(line, variables, keys));
        } catch (IOException e) {
            System.err.println("Error exporting variables: " + e.getMessage());
        }
        return variables;
    }

    private boolean isVariableLine(String line, String targetKey) {
        Matcher matcher = VARIABLE_PATTERN.matcher(line);
        return matcher.matches() && matcher.group("key").trim().equalsIgnoreCase(targetKey);
    }

    private void parseVariableLine(String line, Map<String, String> variables, List<String> filterKeys) {
        Matcher matcher = VARIABLE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String key = matcher.group("key").trim();
            String value = matcher.group("value").replaceAll("^['\"]|['\"]$", "");

            if (filterKeys == null || filterKeys.contains(key)) {
                variables.put(key, value);
            }
        }
    }

    private String escapeValue(String value) {
        return value.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("$", "\\$");
    }

    private String getShellRcFile() {
        String os = System.getProperty("os.name").toLowerCase();
        String shell = System.getenv("SHELL");
        String home = System.getProperty("user.home");
        if (os.contains("mac")) {
            if (shell != null && shell.contains("zsh")) {
                return home + "/.zshrc";
            } else {
                Path bashProfile = Paths.get(home, ".bash_profile");
                return Files.exists(bashProfile) ? bashProfile.toString() : home + "/.bashrc";
            }
        } else {
            return home + "/.bashrc";
        }
    }
}

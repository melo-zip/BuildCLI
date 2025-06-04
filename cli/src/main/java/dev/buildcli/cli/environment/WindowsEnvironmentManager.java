package dev.buildcli.cli.environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WindowsEnvironmentManager extends EnvironmentManager {

    @Override
    public void setVariable(String key, String value, String system) {
        String root = system.equals("system") ? "HKLM" : "HKCU";
        List<String> command = List.of("reg", "add", root + "\\Environment", "/v", key, "/t", "REG_SZ", "/d", value, "/f");
        executeCommand(command, "set");
    }

    @Override
    public void deleteVariable(String key, String system) {
        String root = system.equals("system") ? "HKLM" : "HKCU";
        List<String> command = List.of("reg", "delete", root + "\\Environment", "/v", key, "/f");
        executeCommand(command, "delete");
    }

    @Override
    public boolean variableExists(String key, String system) {
        String root = system.equals("system") ? "HKLM" : "HKCU";
        List<String> command = List.of("reg", "query", root + "\\Environment", "/v", key);
        return executeCommand(command, "query") == 0;
    }

    @Override
    public Map<String, String> exportVariables(List<String> keys, String system) {
        Map<String, String> envVars = new HashMap<>();
        String root = system.equals("system") ? "HKLM" : "HKCU";
        List<String> command = List.of("reg", "query", root + "\\Environment");
        Pattern pattern = Pattern.compile("^\\s+(\\S+)\\s+REG_\\S+\\s+(.*)$");

        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.matches()) {
                        String varName = matcher.group(1);
                        String varValue = matcher.group(2);
                        varValue = expandRegistryValue(varValue);
                        if (keys == null || keys.contains(varName)) {
                            envVars.put(varName, varValue);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Erro ao exportar variáveis: " + e.getMessage());
        }
        return envVars;
    }

    private String expandRegistryValue(String value) {
        Pattern envPattern = Pattern.compile("%(\\w+)%");
        Matcher envMatcher = envPattern.matcher(value);
        StringBuilder expanded = new StringBuilder();

        while (envMatcher.find()) {
            String envVar = envMatcher.group(1);
            String envValue = System.getenv(envVar);
            envMatcher.appendReplacement(expanded,
                    envValue != null ? Matcher.quoteReplacement(envValue) : "%" + envVar + "%");
        }
        envMatcher.appendTail(expanded);
        return expanded.toString();
    }

    private int executeCommand(List<String> command, String action) {
        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            Process process = pb.start();
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Erro ao " + action + " variável: " + e.getMessage());
            return -1;
        }
    }
}

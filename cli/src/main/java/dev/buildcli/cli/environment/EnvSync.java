package dev.buildcli.cli.environment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EnvSync {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Scanner scanner = new Scanner(System.in);

    public void sync() {
        interactiveMode();
    }

    private static void interactiveMode() {
        System.out.println("Welcome to the Environment Variable Manager!");
        System.out.println("Note: JSON files for import must be in the project root, and exports will be saved there.");


        System.out.print("Import or export variables? (i/e): ");
        String mode = scanner.nextLine().trim().toLowerCase();

        if (mode.equals("i")) {
            handleImport();
        } else if (mode.equals("e")) {
            handleExport();
        } else {
            System.out.println("Invalid option!");
        }
    }

    private static void handleImport() {
        System.out.print("Enter JSON filename to import (ex: vars.json): ");
        String filename = scanner.nextLine().trim();
        if (!filename.endsWith(".json")) {
            filename += ".json";
        }
        Map<String, String> envVars = importEnvVars(filename);

        if (envVars.isEmpty()) {
            System.out.println("No variables found. Switching to manual input.");
            envVars = interactiveInput();
        }

        System.out.println("Variables to set: " + envVars);
        System.out.print("Confirm? (y/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) return;

        EnvironmentManager manager = getManager();
        List<String> selectedKeys = handleExistingVariables(manager, envVars);

        if (selectedKeys != null) {
            Map<String, String> variablesToSet = filterVariablesToSet(manager, envVars, selectedKeys);

            String scope = manager instanceof WindowsEnvironmentManager ? getSystemScope(manager) : "user";
            for (String key : variablesToSet.keySet()) {
                manager.deleteVariable(key, scope);
            }
            setVariables(manager, variablesToSet);
            System.out.println("Variables imported successfully!");
        } else {
            System.out.println("Operation cancelled.");
        }
    }

    private static Map<String, String> filterVariablesToSet(
            EnvironmentManager manager,
            Map<String, String> envVars,
            List<String> selectedKeys
    ) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            String key = entry.getKey();

            if (selectedKeys.contains(key) || !manager.variableExists(key, "user")) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static void handleExport() {
        System.out.print("Enter variables to export (comma-separated or 'all'): ");
        String input = scanner.nextLine().trim();

        List<String> keys = input.equalsIgnoreCase("all") ? null : Arrays.asList(input.split(","));

        EnvironmentManager manager = getManager();
        Map<String, String> envVars = manager.exportVariables(keys, getSystemScope(manager));

        System.out.print("Enter export filename (ex: vars.json): ");
        String filename = scanner.nextLine().trim();
        if (!filename.endsWith(".json")) {
            filename += ".json";
        }
        exportEnvVars(envVars, filename);
    }

    private static EnvironmentManager getManager() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? new WindowsEnvironmentManager() : new UnixEnvironmentManager();
    }

    private static String getSystemScope(EnvironmentManager manager) {
        if (manager instanceof WindowsEnvironmentManager) {
            System.out.print("System scope (user/system): ");
            return scanner.nextLine().trim().toLowerCase();
        }
        return "user";
    }

    private static List<String> handleExistingVariables(EnvironmentManager manager, Map<String, String> envVars) {
        List<String> existingKeys = envVars.keySet().stream()
                .filter(key -> manager.variableExists(key, "user"))
                .toList();

        if (existingKeys.isEmpty()) {
            return Collections.emptyList();
        }

        System.out.println("Existing variables: " + existingKeys);
        System.out.print("Overwrite all? (y/n): ");
        String choice = scanner.nextLine().trim().toLowerCase();

        if (choice.equals("y")) {
            return existingKeys;
        } else if (choice.equals("n")) {
            System.out.println("Enter variable key(s) to overwrite (comma-separated): ");
            String variableInput = scanner.nextLine().trim();

            List<String> selectedKeys = Arrays.stream(variableInput.split(","))
                    .map(String::trim)
                    .filter(k -> !k.isEmpty())
                    .distinct()
                    .filter(existingKeys::contains)
                    .toList();

            return selectedKeys.isEmpty() ? null : selectedKeys;
        } else {
            System.out.println("Invalid option. Operation cancelled.");
            return null;
        }
    }

    private static void setVariables(EnvironmentManager manager, Map<String, String> envVars) {
        String scope = manager instanceof WindowsEnvironmentManager ?
                getSystemScope(manager) : "user";

        envVars.forEach((k, v) -> manager.setVariable(k, v, scope));
    }

    private static Map<String, String> interactiveInput() {
        Map<String, String> vars = new HashMap<>();
        while (true) {
            System.out.print("Enter variable key (empty to finish): ");
            String key = scanner.nextLine().trim();
            if (key.isEmpty()) break;

            System.out.print("Enter value: ");
            String value = scanner.nextLine().trim();
            vars.put(key, value);
        }
        return vars;
    }

    private static Map<String, String> importEnvVars(String filename) {
        try {
            return mapper.readValue(new File(filename), new TypeReference<>() {
            });
        } catch (IOException e) {
            System.err.println("Error importing variables: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static void exportEnvVars(Map<String, String> envVars, String filename) {
        try {


            mapper.writeValue(new File(filename), envVars);
            System.out.printf("Variables exported successfully to '%s'", filename);
        } catch (IOException e) {
            System.err.println("Error exporting variables: " + e.getMessage());
        }
    }
}
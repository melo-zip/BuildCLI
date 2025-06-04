package dev.buildcli.cli.environment;

import java.util.*;

abstract class EnvironmentManager {
    public abstract void setVariable(String key, String value, String system);
    public abstract void deleteVariable(String key, String system);
    public abstract boolean variableExists(String key, String system);
    public abstract Map<String, String> exportVariables(List<String> keys, String system);
}


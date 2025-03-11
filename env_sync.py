import abc
import os
import platform
import json

class EnvironmentVariableManager(abc.ABC):
    @abc.abstractmethod
    def set_variable(self, key, value, system="user"):
        pass

    def variable_exists_in_shell_rc(self, rc_file, key):
        if os.path.exists(rc_file):
            try:
                with open(rc_file, "r") as f:
                    contents = f.read()
                if f"export {key}=" in contents:
                    return True
            except Exception as e:
                print(f"Error reading {rc_file}: {e}")
        return False
    
    def variable_exists_in_registry(self, key, system="user"):
        if system == "user":
            key_path = r"Environment"
            root_key = "HKEY_CURRENT_USER"
        elif system == "system":
            key_path = r"Environment"
            root_key = "HKEY_LOCAL_MACHINE"
        else:
            return False
        
        try:
            import winreg
            with winreg.OpenKey(root_key, key_path) as reg_key:
                try:
                    winreg.QueryValueEx(reg_key, key)
                    return True
                except FileNotFoundError:
                    return False
        except Exception as e:
            print(f"Error accessing registry: {e}")
            return False 

    def get_shell_rc_file(self):
        system = platform.system()
        if system == "Darwin":
            shell = os.environ.get("SHELL", "")
            if "zsh" in shell:
                return os.path.expanduser("~/.zshrc")
        return os.path.expanduser("~/.bashrc")

    def export_variables(self, keys=None):
        """
        Default export method for Unix-like systems:
        Parses the shell rc file (e.g., .bashrc or .zshrc) for lines like:
            export KEY=VALUE
        Returns a dictionary of exported variables.
        If keys is provided (as a list), only those keys are exported.
        """
        rc_file = self.get_shell_rc_file()
        env_vars = {}
        if os.path.exists(rc_file):
            try:
                with open(rc_file, "r") as f:
                    for line in f:
                        line = line.strip()
                        if line.startswith("export "):
                            remainder = line[len("export "):]
                            if '=' in remainder:
                                var_name, var_value = remainder.split('=', 1)
                                var_name = var_name.strip()
                                var_value = var_value.strip().strip('"').strip("'")
                                if keys is None or var_name in keys:
                                    env_vars[var_name] = var_value
            except Exception as e:
                print(f"Error reading {rc_file}: {e}")
        else:
            print(f"Shell rc file not found: {rc_file}")
        return env_vars

class WindowsEnvironmentVariableManager(EnvironmentVariableManager):
    def delete_variable(self, key, system="user"):
        try:
            import winreg
            if system == "user":
                key_path = winreg.HKEY_CURRENT_USER
            elif system == "system":
                key_path = winreg.HKEY_LOCAL_MACHINE
            else:
                raise ValueError("Invalid system value. Use 'user' or 'system'.")
            
            with winreg.OpenKey(key_path, "Environment", 0, winreg.KEY_ALL_ACCESS) as reg_key:
                winreg.DeleteValue(reg_key, key)
            print(f"Environment variable {key} deleted from Windows ({system}).")
        except Exception as e:
            print(f"Error deleting variable: {e}")

    def set_variable(self, key, value, system="user"):
        try:
            import winreg
            if system == "user":
                key_path = winreg.HKEY_CURRENT_USER
            elif system == "system":
                key_path = winreg.HKEY_LOCAL_MACHINE
            else:
                raise ValueError("Invalid system value. Use 'user' or 'system'.")

            with winreg.OpenKey(key_path, "Environment", 0, winreg.KEY_ALL_ACCESS) as reg_key:
                winreg.SetValueEx(reg_key, key, 0, winreg.REG_SZ, value)
            print(f"Environment variable {key}={value} added to Windows ({system}).")
        except Exception as e:
            print(f"Error setting variable: {e}")

    def export_variables(self, keys=None, system="user"):
        """
        Export environment variables from the Windows registry.
        If keys is provided (as a list), only export those variables.
        """
        import winreg
        env_vars = {}
        try:
            if system == "user":
                key_path = r"Environment"
                root = winreg.HKEY_CURRENT_USER
            else:
                key_path = r"Environment"
                root = winreg.HKEY_LOCAL_MACHINE
            reg_key = winreg.OpenKey(root, key_path)
            i = 0
            while True:
                try:
                    var = winreg.EnumValue(reg_key, i)
                    var_name = var[0]
                    var_value = var[1]
                    if keys is None or var_name in keys:
                        env_vars[var_name] = var_value
                    i += 1
                except OSError:
                    break
            winreg.CloseKey(reg_key)
        except Exception as e:
            print(f"Error reading registry: {e}")
        return env_vars

class LinuxEnvironmentVariableManager(EnvironmentVariableManager):
    def remove_variable_from_shell_rc(self, rc_file, key):
        try:
            with open(rc_file, "r") as f:
                lines = f.readlines()
            
            with open(rc_file, "w") as f:
                for line in lines:
                    if not line.startswith(f"export {key}="):
                        f.write(line)
            print(f"Environment variable {key} removed from {rc_file}.")
        except Exception as e:
            print(f"Error removing variable from shell rc: {e}")

    def set_variable(self, key, value, system="user"):
        try:
            bashrc = os.path.expanduser("~/.bashrc")
            with open(bashrc, "a") as f:
                f.write(f"\nexport {key}={value}")
            print(f"Environment variable {key}={value} added to {bashrc}")
        except FileNotFoundError:
            print(f"Error: Configuration file not found at {bashrc}")
        except Exception as e:
            print(f"Error setting variable: {e}")

class MacOSenvironmentVariableManager(EnvironmentVariableManager):
    def set_variable(self, key, value, system="user"):
        try:
            zshrc = os.path.expanduser("~/.zshrc")
            with open(zshrc, "a") as f:
                f.write(f"\nexport {key}={value}")
            print(f"Environment variable {key}={value} added to {zshrc}")
        except FileNotFoundError:
            print(f"Error: Configuration file not found at {zshrc}")
        except Exception as e:
            print(f"Error setting variable: {e}")

def get_environment_variable_manager():
    operational_system = platform.system()
    if operational_system == "Windows":
        return WindowsEnvironmentVariableManager()
    elif operational_system == "Linux":
        return LinuxEnvironmentVariableManager()
    elif operational_system == "Darwin":
        return MacOSenvironmentVariableManager()
    else:
        raise ValueError(f"Unsupported operating system: {operational_system}")

def interactive_input_all():
    env_vars = {}
    print("Enter environment variables (press Enter without input to finish):")
    while True:
        key = input("Variable key: ").strip()
        if not key:
            break
        value = input(f"Value for '{key}': ").strip()
        env_vars[key] = value
    return env_vars

def export_env_vars(env_vars, filename):
    try:
        with open(filename, "w") as f:
            json.dump(env_vars, f, indent=4)
        print(f"Environment variables exported to {filename}")
    except Exception as e:
        print(f"Error exporting variables: {e}")

def import_env_vars(filename):
    try:
        with open(filename, "r") as f:
            return json.load(f)
    except Exception as e:
        print(f"Error importing variables from {filename}: {e}")
        return {}

def interactive_mode():
    print("Welcome to the Interactive Environment Variable Manager!")
    
    mode = input("Do you want to import or export variables? (i/e): ").strip().lower()
    
    if mode == "i":
        filename = input("Enter the file name (e.g., config.json): ").strip()
        env_vars = import_env_vars(filename)
        if not env_vars:
            print("No variables imported. Switching to manual input.")
            env_vars = interactive_input_all()

        if not env_vars:
            print("No environment variables provided. Exiting.")
            return

        confirm = input(f"{env_vars}\nDo you want to set these variables on your system? (y/n): ").strip().lower()
        if confirm not in ("y", "yes"):
            print("Operation cancelled. Exiting without setting variables.")
            return

        manager = get_environment_variable_manager()
        rc_file = manager.get_shell_rc_file()

        existing_vars = []
        if isinstance(manager, WindowsEnvironmentVariableManager):
            for key in env_vars.keys():
                if manager.variable_exists_in_registry(key, system="user"):
                    existing_vars.append(key)
        else:
            rc_file = manager.get_shell_rc_file()
            for key in env_vars.keys():
                if manager.variable_exists_in_shell_rc(rc_file, key):
                    existing_vars.append(key)

        if existing_vars:
            print(f"The following variables already exist: {existing_vars}")
            overwrite_all = input("Do you want to overwrite all of them? (y/n): ").strip().lower()
            if overwrite_all not in ("y", "yes"):
                overwrite_choice = input("Enter the environment variable key(s) to overwrite (separate multiple keys with commas), type 'all' to overwrite all variables, or press Enter to cancel (no changes will be made): ")
                if overwrite_choice.lower() == "all":
                    keys = None
                else:
                   existing_vars = [k.strip() for k in overwrite_choice.split(",") if k.strip()]
                if not existing_vars:
                    print("No valid environment variables found for overwrite. Exiting.")
                    return
                
            for key in existing_vars:
                if isinstance(manager, WindowsEnvironmentVariableManager):
                    manager.delete_variable(key, system="user")
                else:
                    manager.remove_variable_from_shell_rc(rc_file, key)
            
        if isinstance(manager, WindowsEnvironmentVariableManager):
            system_choice = input("Set variables as 'user' or 'system'? ").strip().lower()
            for key, value in env_vars.items():
                manager.set_variable(key, value, system=system_choice)
        else:
            for key, value in env_vars.items():
                manager.set_variable(key, value)
    elif mode == "e":
        
        export_choice = input("Enter the environment variable key(s) to export (separate multiple keys with commas), type 'all' to export all variables, or press Enter to cancel (no changes will be made): ").strip()
        if export_choice.lower() == "all":
            keys = None
        else:
            keys = [k.strip() for k in export_choice.split(",") if k.strip()]
        manager = get_environment_variable_manager()
        if isinstance(manager, WindowsEnvironmentVariableManager):
            system_choice = input("Export variables from 'user' or 'system'? (user/system): ").strip().lower()
            env_vars = manager.export_variables(keys, system=system_choice)
        else:
            env_vars = manager.export_variables(keys)
        if not env_vars:
            print("No valid environment variables found for export. Exiting.")
            return
        filename = input("Enter the file name to export to (e.g., export.json): ").strip()
        export_env_vars(env_vars, filename)
    else:
        print("Invalid option. Exiting.")
        return

if __name__ == "__main__":
    interactive_mode()
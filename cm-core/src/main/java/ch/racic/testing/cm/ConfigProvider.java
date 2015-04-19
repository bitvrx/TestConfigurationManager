/*
 * Copyleft (c) 2015. This code is for learning purposes only.
 * Do whatever you like with it but don't take it as perfect code.
 * //Michel Racic (http://rac.su/+)//
 */

package ch.racic.testing.cm;

import ch.racic.testing.cm.annotation.ClassConfig;
import ch.racic.testing.cm.guice.ConfigModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.apache.commons.exec.OS;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

/**
 * This ConfigProvider handles configuration management tailored for usage in TestNG. It maintains several layers of
 * properties which can override the previous layer.
 */
public class ConfigProvider {

    private static final Logger log = LogManager.getLogger(ConfigProvider.class);

    private ConfigProvider parentConfig;

    private ConfigEnvironment environment;
    private String clazz;

    private List<Module> guiceModules;

    private static final String CONFIG_BASE_FOLDER = "config";
    private static final String CONFIG_GLOBAL_BASE_FOLDER = "global";
    private static final String CONFIG_CLASS_FOLDER = "class";
    private static final String CONFIG_OS_FOLDER = "os";

    private AggregatedResourceBundle propsGlobal, propsEnv, propsGlobalClass, propsEnvClass, propsTestNG, propsCustomClass, propsOS;

    private FilenameFilter propertiesFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            if (
            /** Filter locale bundles as we will construct the ResourceBundle with the default file **/
                    name.matches("^([a-zA-Z0-9.-]*\\.properties)$")
                    ) return true;
            else return false;
        }
    };

    public ConfigProvider(ConfigEnvironment environment, Class clazz) {
        this(null, environment, clazz, null);
    }

    public ConfigProvider(ConfigProvider parent, ConfigEnvironment environment, Class clazz, AggregatedResourceBundle testngParams) {
        this.parentConfig = parent;
        this.environment = environment;
        // Extract class name for loading if annotation present
        ClassConfig classConfig = (ClassConfig) clazz.getAnnotation(ClassConfig.class);
        if (classConfig != null && classConfig.value() != null) this.clazz = classConfig.value().getSimpleName();
        else if (classConfig != null && classConfig.fileName().contentEquals("")) this.clazz = classConfig.fileName();
        else this.clazz = clazz.getSimpleName();
        loadProperties();
        propsTestNG = testngParams;
    }

    private void loadProperties() {
        // check that base config directory exists to be used as a base for all following actions
        File configBaseFolder = FileUtils.toFile(getClass().getClassLoader().getResource(CONFIG_BASE_FOLDER));
        if (!(configBaseFolder != null && configBaseFolder.exists() && configBaseFolder.isDirectory())) {
            log.error("Configuration initialisation error", new IOException("Config folder not existing or not a directory"));
        }

        // Load global base properties
        File configGlobalBaseFolder = new File(configBaseFolder, CONFIG_GLOBAL_BASE_FOLDER);
        if (configGlobalBaseFolder.exists() && configGlobalBaseFolder.isDirectory()) {
            propsGlobal = new AggregatedResourceBundle();
            // Get list of files in global folder and load it into props
            for (File f : configGlobalBaseFolder.listFiles(propertiesFilter)) {

                log.debug("Loading PropertiesResourceBundle from " + f.getPath() + ((environment != null && environment.getLocale() != null) ? " using locale " + environment.getLocale() : ""));
                if (environment != null && environment.getLocale() != null)
                    propsGlobal.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + CONFIG_GLOBAL_BASE_FOLDER + "." + f.getName().replace(".properties", ""), environment.getLocale()));
                else
                    propsGlobal.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + CONFIG_GLOBAL_BASE_FOLDER + "." + f.getName().replace(".properties", "")));
            }
        } else {
            log.warn("global folder not existing, can't load default values");
        }

        // Load environment base properties
        File configEnvironmentBaseFolder = null;
        if (environment != null) {
            configEnvironmentBaseFolder = new File(configBaseFolder, environment.getCode());
            if (!(configEnvironmentBaseFolder.exists() && configEnvironmentBaseFolder.isDirectory())) {
                log.error("Configuration initialisation error", new IOException("Environment specific configuration folder does not exist for " + environment));
            } else {
                propsEnv = new AggregatedResourceBundle();
                // Get list of files in environment folder and load it into props, overriding existing global properties
                for (File f : configEnvironmentBaseFolder.listFiles(propertiesFilter)) {
                    log.debug("Loading PropertiesResourceBundle from " + f.getPath() + ((environment.getLocale() != null) ? " using locale " + environment.getLocale() : ""));
                    if (environment.getLocale() != null)
                        propsEnv.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + environment.getCode() + "." + f.getName().replace(".properties", ""), environment.getLocale()));
                    else
                        propsEnv.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + environment.getCode() + "." + f.getName().replace(".properties", "")));
                }
            }
        }

        // Load global class properties
        if (configGlobalBaseFolder.exists() && configGlobalBaseFolder.isDirectory() && clazz != null) {
            // Check if a class file exists
            File classProps = new File(configGlobalBaseFolder, CONFIG_CLASS_FOLDER + "/" + clazz + ".properties");
            if (classProps.exists() && classProps.isFile()) {
                propsGlobalClass = new AggregatedResourceBundle();
                // It exists, load it into props
                log.debug("Loading PropertiesResourceBundle from " + classProps.getPath() + ((environment != null && environment.getLocale() != null) ? " using locale " + environment.getLocale() : ""));
                if (environment != null && environment.getLocale() != null)
                    propsGlobalClass.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + CONFIG_GLOBAL_BASE_FOLDER + "." + CONFIG_CLASS_FOLDER + "." + clazz, environment.getLocale()));
                else
                    propsGlobalClass.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + CONFIG_GLOBAL_BASE_FOLDER + "." + CONFIG_CLASS_FOLDER + "." + clazz));
            }
        }

        // Load environment class properties
        if (configEnvironmentBaseFolder != null && clazz != null) {
            // Check if a class file exists
            File classProps = new File(configEnvironmentBaseFolder, CONFIG_CLASS_FOLDER + "/" + clazz + ".properties");
            if (classProps.exists() && classProps.isFile()) {
                propsEnvClass = new AggregatedResourceBundle();
                // It exists, load it into props
                log.debug("Loading PropertiesResourceBundle from " + classProps.getPath() + ((environment.getLocale() != null) ? " using locale " + environment.getLocale() : ""));
                if (environment.getLocale() != null)
                    propsEnvClass.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + environment.getCode() + "." + CONFIG_CLASS_FOLDER + "." + clazz, environment.getLocale()));
                else
                    propsEnvClass.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + environment.getCode() + "." + CONFIG_CLASS_FOLDER + "." + clazz));
            }
        }

        loadOSProperties();

    }

    private void loadOSProperties() {
        String detectedOS = null;
        propsOS = new AggregatedResourceBundle();
        if (OS.isFamilyWindows()) {
            // Load windows properties
            propsOS.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + CONFIG_OS_FOLDER + ".windows"));
        } else if (OS.isFamilyUnix()) {
            // Load linux properties
            propsOS.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + CONFIG_OS_FOLDER + ".linux"));
        } else if (OS.isFamilyMac()) {
            // Load mac properties
            propsOS.mergeOverride(ResourceBundle.getBundle(CONFIG_BASE_FOLDER + "." + CONFIG_OS_FOLDER + ".mac"));
        }

    }

    public ConfigEnvironment getEnvironment() {
        return environment;
    }

    public String getClazz() {
        return clazz;
    }

    /**
     * Get the property value for this key, return null if it's not existing.
     *
     * @param key
     * @return value
     */
    public String get(String key) {
        return get(key, null);
    }

    /**
     * Get the property value for this key, returns the given defaultValue if it's not existing.
     *
     * @param key
     * @param defaultValue
     * @return value
     */
    public String get(String key, String defaultValue) {
        if (parentConfig != null && parentConfig.contains(key)) {
            log.debug("Retrieved property [" + key + "] from parent config");
            return parentConfig.get(key);
        }
        if (propsTestNG != null && propsTestNG.containsKey(key)) {
            log.debug("Retrieved property [" + key + "] from TestNG Parameters");
            return propsTestNG.getString(key);
        } else if (propsOS != null && propsOS.containsKey(key)) {
            log.debug("Retrieved property [" + key + "] from OS properties");
            return propsOS.getString(key);
        } else if (propsCustomClass != null && propsCustomClass.containsKey(key)) {
            log.debug("Retrieved property [" + key + "] from custom set class properties");
            return propsCustomClass.getString(key);
        } else if (propsEnvClass != null && propsEnvClass.containsKey(key)) {
            log.debug("Retrieved property [" + key + "] from Environment class properties");
            return propsEnvClass.getString(key);
        } else if (propsGlobalClass != null && propsGlobalClass.containsKey(key)) {
            log.debug("Retrieved property [" + key + "] from Global class properties");
            return propsGlobalClass.getString(key);
        } else if (propsEnv != null && propsEnv.containsKey(key)) {
            log.debug("Retrieved property [" + key + "] from Environment properties");
            return propsEnv.getString(key);
        } else if (propsGlobal != null && propsGlobal.containsKey(key)) {
            log.debug("Retrieved property [" + key + "] from Global properties");
            return propsGlobal.getString(key);
        } else {
            log.warn("Property [" + key + "] has not been found, returning default value");
            return defaultValue;
        }
    }

    /**
     * Check if the key is somewhere in the properties chain on all layers including parent.
     *
     * @param key
     * @return
     */
    public boolean contains(String key) {
        if (parentConfig.contains(key)) return true;
        if (propsTestNG.containsKey(key)) return true;
        if (propsCustomClass.containsKey(key)) return true;
        if (propsEnvClass.containsKey(key)) return true;
        if (propsGlobalClass.containsKey(key)) return true;
        if (propsEnv.containsKey(key)) return true;
        if (propsGlobal.containsKey(key)) return true;
        // 404 no property found
        return false;
    }

    /**
     * Loads a special class properties file which overrides all the layers except the TestNG and OS parameter layer.
     *
     * @param custom Properties object
     */
    public void loadCustomClassProperties(Properties custom) {
        if (propsCustomClass == null) propsCustomClass = new AggregatedResourceBundle();
        propsCustomClass.mergeOverride(custom);
    }

    /**
     * Log all the properties by category, starting with the lowest layer.
     */
    public void logAvailableProperties() {
        logProperties("Global", propsGlobal);
        if (environment != null) logProperties("Environment " + environment, propsEnv);
        logProperties("Global class", propsGlobalClass);
        if (environment != null) logProperties("Environment class " + environment, propsEnvClass);
        logProperties("TestNG parameters", propsTestNG);
    }

    private void logProperties(String title, AggregatedResourceBundle props) {
        if (props == null) return;
        log.info("CM Properties available from " + title);
        for (String key : props.keySet())
            log.info("\tKey[" + key + "], Value[" + props.getString(key) + "]");
    }


    public ConfigProvider setGuiceModules(List<Module> guiceModules) {
        this.guiceModules = guiceModules;
        return this;
    }

    /**
     * Create an instance of a class using the config injector and any guice modules given in parameters. It will use
     * this ConfigProvider as parent, that means that all the properties from this object will override the properties
     * it loads from it's own class files.
     *
     * @param type
     * @param modules ...
     * @param <T>
     * @return object instance
     */
    public <T> T create(Class<T> type, Module... modules) {
        List<Module> mList = new ArrayList<Module>(Arrays.asList(modules));
        if (guiceModules != null) {
            mList.addAll(guiceModules);
        }
        mList.add(new ConfigModule(parentConfig, environment, type, propsTestNG));
        Injector injector = com.google.inject.Guice.createInjector(mList);
        return injector.getInstance(type);
    }

}

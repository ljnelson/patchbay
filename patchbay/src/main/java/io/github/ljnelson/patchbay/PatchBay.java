/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023 Laird Nelson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.github.ljnelson.patchbay;

import java.lang.System.Logger;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import java.util.function.Function;

import io.github.ljnelson.jakarta.config.ConfigException;
import io.github.ljnelson.jakarta.config.InvalidConfigurationClassException;
import io.github.ljnelson.jakarta.config.Loader;
import io.github.ljnelson.jakarta.config.NoSuchObjectException;

import jdk.incubator.concurrent.ScopedValue;

import static java.lang.System.Logger.Level.DEBUG;

public final class PatchBay implements Loader {

  private static final Logger logger = System.getLogger(PatchBay.class.getName());
  
  private static final ClassValue<Boolean> IS_CONFIGURATION_CLASS = new ClassValue<>() {
      @Override
      protected final Boolean computeValue(final Class<?> c) {
        return computeIsConfigurationClass(c);
      }
    };

  private static final ScopedValue<PatchBay> PATCHBAY = ScopedValue.newInstance();

  private static final ScopedValue<Class<?>> LOAD_REQUEST = ScopedValue.newInstance();


  /*
   * Instance fields.
   */


  private final Configuration.Coordinates coordinates;

  private final ClassValue<ConfigurationObjectProvider> configurationObjectProvidersByClass;

  private final ClassValue<List<LogicalModelProvider>> logicalModelProvidersByClass;

  private final ClassValue<LogicalModel.Configuration> logicalModelsByClass;


  /*
   * Constructors.
   */


  @Deprecated // for ServiceLoader usage
  public PatchBay() {
    this(ServiceLoaderConfiguration.INSTANCE);
  }

  public PatchBay(final Configuration configuration) {
    super();
    this.coordinates = Objects.requireNonNull(configuration.coordinates(), "configuration.coordinates()");

    final List<ConfigurationObjectProvider> unsortedConfigurationObjectProviders = new ArrayList<>(configuration.configurationObjectProviders());
    Collections.sort(unsortedConfigurationObjectProviders, Comparator.comparingInt(ConfigurationObjectProvider::priority)); // "first priority" priority, not "highest priority" priority
    final List<ConfigurationObjectProvider> configurationObjectProviders = Collections.unmodifiableList(unsortedConfigurationObjectProviders);
    this.configurationObjectProvidersByClass = new ClassValue<>() {
        @Override
        protected final ConfigurationObjectProvider computeValue(final Class<?> configurationClass) {
          return PatchBay.this.computeConfigurationObjectProviderFor(configurationObjectProviders, configurationClass);
        }
      };

    final List<LogicalModelProvider> unsortedLogicalModelProviders = new ArrayList<>(configuration.logicalModelProviders());
    Collections.sort(unsortedLogicalModelProviders, Comparator.comparingInt(LogicalModelProvider::priority)); // "first priority" priority, not "highest priority" priority
    final List<LogicalModelProvider> logicalModelProviders = Collections.unmodifiableList(unsortedLogicalModelProviders);
    this.logicalModelProvidersByClass = new ClassValue<>() {
        @Override
        protected final List<LogicalModelProvider> computeValue(final Class<?> configurationClass) {
          return PatchBay.this.computeLogicalModelProvidersFor(logicalModelProviders, configurationClass);
        }
      };

    this.logicalModelsByClass = new ClassValue<>() {
        @Override
        protected final LogicalModel.Configuration computeValue(final Class<?> configurationClass) {
          return PatchBay.this.computeLogicalModelFor(configurationClass);
        }
      };

    for (final ConfigurationObjectProvider provider : configurationObjectProviders) {
      provider.configure(this);
    }
    for (final LogicalModelProvider provider : logicalModelProviders) {
      provider.configure(this);
    }
    if (logger.isLoggable(DEBUG)) {
      logger.log(DEBUG, "configurationObjectProviders: " + configurationObjectProviders);
      logger.log(DEBUG, "logicalModelProviders: " + logicalModelProviders);
    }
  }


  /*
   * Public instance methods.
   */


  @Override // Loader
  public final <T> T load(final Class<T> configurationClass) {

    // Is this the bootstrap request? Bootstrap if so.
    if (configurationClass == Loader.class) {
      return configurationClass.cast(this.load());
    }

    validateConfigurationClass(configurationClass);

    if (LOAD_REQUEST.orElse(null) == configurationClass) {
      throw new NoSuchObjectException();
    }
    try {
      return ScopedValue.where(LOAD_REQUEST, configurationClass, () -> this.findConfigurationObjectFor(this.logicalModel(configurationClass), configurationClass));
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new InternalError(e);
    }
  }

  public final Configuration.Coordinates coordinates() {
    return this.coordinates;
  }

  public final LogicalModel.Configuration logicalModel(final Class<?> c) {
    return this.logicalModelsByClass.get(c);
  }


  /*
   * Private instance methods.
   */


  private final PatchBay load() {
    if (PATCHBAY.isBound()) {
      return PATCHBAY.get();
    }
    try {
      return ScopedValue.where(PATCHBAY, new PatchBay(this.load(Configuration.class)), PATCHBAY::get);
    } catch (final NoSuchObjectException e) {
      return this;
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new ConfigException(e.getMessage(), e);
    }
  }

  private final LogicalModel.Configuration computeLogicalModelFor(final Class<?> configurationClass) {
    final List<LogicalModelProvider> logicalModelProviders = this.logicalModelProvidersByClass.get(configurationClass);
    if (logicalModelProviders.isEmpty()) {
      throw new NoSuchObjectException();
    }
    LogicalModel.Configuration defaults = LogicalModel.Configuration.of();
    LogicalModel.Configuration logicalModel = null;
    for (int i = logicalModelProviders.size() - 1; i >= 0; i--) {
      logicalModel = logicalModelProviders.get(i).logicalModelFor(this, configurationClass);
      if (logicalModel != null) {
        logicalModel = new LogicalModel.ConfigurationWithDefaults(logicalModel, defaults);
      }
      defaults = logicalModel;
    }
    return logicalModel == null ? defaults : logicalModel;
  }

  // Called once, ever, from a ClassValue.
  private final List<LogicalModelProvider> computeLogicalModelProvidersFor(final List<LogicalModelProvider> logicalModelProviders, final Class<?> configurationClass) {
    final ArrayList<LogicalModelProvider> list = new ArrayList<>(logicalModelProviders.size());
    for (final LogicalModelProvider logicalModelProvider : logicalModelProviders) {
      if (logicalModelProvider.accepts(this, configurationClass)) {
        list.add(logicalModelProvider);
      }
    }
    list.trimToSize();
    if (logger.isLoggable(DEBUG)) {
      final StringBuilder sb = new StringBuilder("computed LogicalModelProviders for ")
        .append(configurationClass)
        .append(":\n");
      for (int i = 0; i < list.size(); i++) {
        final LogicalModelProvider p = list.get(i);
        sb.append(i + 1)
          .append(": ")
          .append(p)
          .append(" (priority: ")
          .append(p.priority())
          .append(")\n");
      }
      logger.log(DEBUG, sb.toString());
    }
    return Collections.unmodifiableList(list);
  }

  private final <T> T findConfigurationObjectFor(final LogicalModel.Configuration logicalModel, final Class<T> configurationClass) {
    final ConfigurationObjectProvider configurationObjectProvider = this.configurationObjectProvidersByClass.get(configurationClass);
    assert configurationObjectProvider != null;
    return configurationObjectProvider.configurationObjectFor(this, logicalModel, configurationClass);
  }

  // Called once, ever, from a ClassValue.
  private final ConfigurationObjectProvider computeConfigurationObjectProviderFor(final Iterable<? extends ConfigurationObjectProvider> configurationObjectProviders, final Class<?> configurationClass) {
    for (final ConfigurationObjectProvider configurationObjectProvider : configurationObjectProviders) {
      if (configurationObjectProvider.accepts(this, configurationClass)) {
        return configurationObjectProvider;
      }
    }
    return ServiceLoaderConfigurationObjectProvider.INSTANCE;
  }


  /*
   * Public static methods.
   */


  public static final boolean configurationClass(final Class<?> c) {
    return IS_CONFIGURATION_CLASS.get(c);
  }

  public static final boolean configurationKey(final Method m) {
    if (m == null) {
      return false;
    }
    final Class<?> c = m.getDeclaringClass();
    if (!c.isInterface() ||
        c.isAnnotation() ||
        c.isHidden()) {
      return false;
    }
    if (m.getParameterCount() > 0 ||
        m.getTypeParameters().length > 0 ||
        m.getReturnType() == void.class ||
        m.getReturnType() == Void.class) {
      return false;
    }
    return true;
  }


  /*
   * Private static methods.
   */


  private static final boolean computeIsConfigurationClass(final Class<?> c) {
    if (c == null ||
        !c.isInterface() ||
        c.isAnnotation() ||
        c.isHidden() ||
        c.getTypeParameters().length > 0) {
      return false;
    }
    // Skipping "must be public" and "must not be sealed" for now.
    for (final Method m : c.getMethods()) {
      if (m.getTypeParameters().length > 0) {
        return false;
      }
    }
    return true;
  }

  private static final <T> Class<T> validateConfigurationClass(final Class<T> c) {
    if (configurationClass(c)) {
      return c;
    }
    throw new InvalidConfigurationClassException(c.getName());
  }


  /*
   * Inner and nested classes.
   */


  // A PatchBay's Configuration.
  public static interface Configuration {

    // A PatchBay instance's configuration.

    public default List<ConfigurationObjectProvider> configurationObjectProviders() {
      return List.of();
    }

    public default List<LogicalModelProvider> logicalModelProviders() {
      return List.of();
    }

    public default Coordinates coordinates() {
      return Coordinates.of();
    }

    public static Configuration of() {
      final class DefaultConfiguration implements Configuration {
        private static final Configuration INSTANCE = new DefaultConfiguration();
      }
      return DefaultConfiguration.INSTANCE;
    }

    public static interface Coordinates {

      public default String application() {
        return null;
      }

      public default String module() {
        return null;
      }

      public default String component() {
        return null;
      }

      public static Coordinates of() {
        final class NoCoordinates implements Coordinates {
          private static final Coordinates INSTANCE = new NoCoordinates();
        };
        return NoCoordinates.INSTANCE;
      }

    }

  }

  public static final class LogicalModel {

    private LogicalModel() {
    }

    public static interface Value {

      public default boolean expected() {
        return true;
      }

      public default Kind kind() {
        return Kind.OTHER;
      }

      public enum Kind {
        ABSENCE,
        CONFIGURATION,
        LIST,
        OTHER,
        RAW;
      }

    }

    public static final record Absence(boolean expected) implements LogicalModel.Value {

      private static final Absence EXPECTED = new Absence(true);

      private static final Absence UNEXPECTED = new Absence(false);

      @Override
      public final Value.Kind kind() {
        return Value.Kind.ABSENCE;
      }

      public static final Absence ofExpected() {
        return EXPECTED;
      }

      public static final Absence ofUnexpected() {
        return EXPECTED;
      }

    }

    public static final record RawValue(boolean expected, Object value) implements LogicalModel.Value {

      public RawValue {
        Objects.requireNonNull(value, "value");
      }

      @Override
      public final Value.Kind kind() {
        return Kind.RAW;
      }

      @Override
        public final String toString() {
        return String.valueOf(this.value());
      }

      public static final RawValue of(final boolean expected, final Object value) {
        return new RawValue(expected, value);
      }

    }

    public static interface ListValue extends Value {

      @Override
      public default Kind kind() {
        return Kind.LIST;
      }

      public default int size() {
        return 0;
      }

      public default Value value(final int index) {
        return null;
      }

      public static ListValue of() {
        final class EmptyListValue implements ListValue {
          private static final ListValue INSTANCE = new EmptyListValue();
        };
        return EmptyListValue.INSTANCE;
      }

    }

    public static interface Configuration extends Value {

      public default Configuration defaults() {
        return of();
      }

      public default Set<String> keys() {
        return Set.of();
      }

      @Override
      public default Kind kind() {
        return Kind.CONFIGURATION;
      }

      public default Value value(final String canonicalConfigurationKey) {
        return null;
      }

      public static Configuration of() {
        final class EmptyConfiguration implements Configuration {
          private static final Configuration INSTANCE = new EmptyConfiguration();
        };
        return EmptyConfiguration.INSTANCE;
      }

    }

    static final class ConfigurationWithDefaults implements Configuration {

      private static final System.Logger logger = System.getLogger(ConfigurationWithDefaults.class.getName());
      
      private final Set<String> keys;

      private final Function<? super String, ? extends Value> f;

      private final Configuration defaults;

      ConfigurationWithDefaults(final Configuration c, final Configuration defaults) {
        super();
        final Set<String> keys = new HashSet<>(c.keys());
        keys.addAll(defaults.keys());
        this.keys = Collections.unmodifiableSet(keys);
        this.defaults = defaults == null ? Configuration.of() : defaults;
        this.f = k -> {
          Value v = c.value(k);
          if (v == null) {
            if (logger.isLoggable(DEBUG)) {
              logger.log(DEBUG, "null value found for \"" + k + "\"; getting default");
            }
            v = this.defaults().value(k);
          }
          if (logger.isLoggable(DEBUG)) {
            logger.log(DEBUG, k + " = " + v);
          }
          return v;
        };
      }

      @Override
      public final Configuration defaults() {
        return this.defaults;
      }

      @Override
      public final Set<String> keys() {
        return this.keys;
      }

      @Override
      public final Value value(final String key) {
        return this.f.apply(key);
      }

    }

  }

  public static interface Provider {

    public default void configure(final PatchBay loader) {

    }

  }

  // A PatchBay.Provider that gets a glop of configuration relevant for a configuration class.
  public static interface LogicalModelProvider extends Provider {

    public static final int DEFAULT_PRIORITY = 100;

    public default LogicalModel.Configuration logicalModelFor(final PatchBay loader,
                                                              final Class<?> configurationClass) {
      return null;
    }

    public default int priority() { // "first priority" priority, not "lowest priority" priority
      return DEFAULT_PRIORITY;
    }

    public default boolean accepts(final PatchBay loader, final Class<?> configurationClass) {
      return true;
    }

  }

  // Something that makes a configuration object.
  public static interface ConfigurationObjectProvider extends Provider {

    public static final int DEFAULT_PRIORITY = 100;

    // Precondition: configurationClass is actually a valid configuration class
    public default <T, U extends T> U configurationObjectFor(final PatchBay loader,
                                                             final LogicalModel.Configuration logicalModel,
                                                             final Class<T> configurationClass)
    {
      return null;
    }

    public default int priority() { // "last priority" priority, not "highest priority"
      return DEFAULT_PRIORITY;
    }

    public default boolean accepts(final PatchBay loader, final Class<?> configurationClass) {
      return true;
    }

  }

  public static final class ServiceLoaderConfiguration implements Configuration {

    private static final ServiceLoaderConfiguration INSTANCE = new ServiceLoaderConfiguration();
    
    public ServiceLoaderConfiguration() {
      super();
    }

    @Override
    public final List<ConfigurationObjectProvider> configurationObjectProviders() {
      return ServiceLoader.load(ConfigurationObjectProvider.class)
        .stream()
        .map(ServiceLoader.Provider::get)
        .toList();
    }

    @Override
    public final List<LogicalModelProvider> logicalModelProviders() {
      return ServiceLoader.load(LogicalModelProvider.class)
        .stream()
        .map(ServiceLoader.Provider::get)
        .toList();
    }

    @Override
    public final Coordinates coordinates() {
      return ServiceLoader.load(Coordinates.class)
        .findFirst()
        .orElseGet(Coordinates::of);
    }    
    
  }

  // A default ConfigurationObjectProvider that is the last fallback and the one used for bootstrapping.
  public static final class ServiceLoaderConfigurationObjectProvider implements ConfigurationObjectProvider {

    private static final ServiceLoaderConfigurationObjectProvider INSTANCE = new ServiceLoaderConfigurationObjectProvider();

    private static final ClassValue<?> configurationObjects = new ClassValue<>() {
        @Override
        protected final Object computeValue(final Class<?> configurationClass) {
          return ServiceLoader.load(configurationClass).findFirst().orElseThrow(NoSuchObjectException::new);
        }
      };

    public ServiceLoaderConfigurationObjectProvider() {
      super();
    }

    @Override
    public final int priority() {
      return Integer.MAX_VALUE; // "last priority" priority, not "highest priority" priority
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T, U extends T> U configurationObjectFor(final PatchBay ignoredLoader,
                                                           final LogicalModel.Configuration ignoredLogicalModel,
                                                           final Class<T> configurationClass)
    {
      return (U)configurationObjects.get(configurationClass);
    }

  }

}

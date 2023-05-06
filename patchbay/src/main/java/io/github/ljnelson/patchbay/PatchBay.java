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

  private final ClassValue<Object> configurationObjectsByClass;

  private final ClassValue<List<LogicalModelProvider>> logicalModelProvidersByClass;

  private final ClassValue<io.github.ljnelson.patchbay.logical.Configuration> logicalModelsByClass;


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
    Collections.sort(unsortedConfigurationObjectProviders,
                     Comparator.comparingInt(ConfigurationObjectProvider::priority) // "first priority" priority, not "highest priority" priority
                     .thenComparing(p -> p.getClass().getName()));
    final List<ConfigurationObjectProvider> configurationObjectProviders = Collections.unmodifiableList(unsortedConfigurationObjectProviders);
    this.configurationObjectProvidersByClass = new ClassValue<>() {
        @Override
        protected final ConfigurationObjectProvider computeValue(final Class<?> configurationClass) {
          return PatchBay.this.computeConfigurationObjectProviderFor(configurationObjectProviders, configurationClass);
        }
      };

    final List<LogicalModelProvider> unsortedLogicalModelProviders = new ArrayList<>(configuration.logicalModelProviders());
    Collections.sort(unsortedLogicalModelProviders,
                     Comparator.comparingInt(LogicalModelProvider::priority) // "first priority" priority, not "highest priority" priority
                     .thenComparing(p -> p.getClass().getName()));
    final List<LogicalModelProvider> logicalModelProviders = Collections.unmodifiableList(unsortedLogicalModelProviders);
    this.logicalModelProvidersByClass = new ClassValue<>() {
        @Override
        protected final List<LogicalModelProvider> computeValue(final Class<?> configurationClass) {
          return PatchBay.this.computeLogicalModelProvidersFor(logicalModelProviders, configurationClass);
        }
      };

    this.configurationObjectsByClass = new ClassValue<>() {
        @Override
        protected final Object computeValue(final Class<?> configurationClass) {
          return PatchBay.this.computeConfigurationObject(PatchBay.this.logicalModel(configurationClass), configurationClass);
        }
      };
    this.logicalModelsByClass = new ClassValue<>() {
        @Override
        protected final io.github.ljnelson.patchbay.logical.Configuration computeValue(final Class<?> configurationClass) {
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
      return ScopedValue.where(LOAD_REQUEST, configurationClass, () -> this.computeConfigurationObject(this.logicalModel(configurationClass), configurationClass));
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new InternalError(e);
    }
  }

  public final Configuration.Coordinates coordinates() {
    return this.coordinates;
  }

  public final io.github.ljnelson.patchbay.logical.Configuration logicalModel(final Class<?> c) {
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

  private final io.github.ljnelson.patchbay.logical.Configuration computeLogicalModelFor(final Class<?> configurationClass) {
    final List<LogicalModelProvider> logicalModelProviders = this.logicalModelProvidersByClass.get(configurationClass);
    if (logicalModelProviders.isEmpty()) {
      throw new NoSuchObjectException();
    }
    io.github.ljnelson.patchbay.logical.Configuration defaults = io.github.ljnelson.patchbay.logical.Configuration.ofUnmodeled();
    io.github.ljnelson.patchbay.logical.Configuration logicalModel = null;
    for (int i = logicalModelProviders.size() - 1; i >= 0; i--) {
      logicalModel = logicalModelProviders.get(i).logicalModelFor(this, configurationClass);
      if (logicalModel != null) {
        logicalModel = new io.github.ljnelson.patchbay.logical.Configuration(logicalModel, defaults);
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

  public final <T> T computeConfigurationObject(final io.github.ljnelson.patchbay.logical.Configuration logicalModel, final Class<T> configurationClass) {
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

  // Something that provides something to a PatchBay.
  public static sealed interface Provider permits ConfigurationObjectProvider, LogicalModelProvider {

    public default void configure(final PatchBay loader) {

    }

  }

  // A PatchBay.Provider that gets a glop of configuration relevant for a configuration class.
  public static non-sealed interface LogicalModelProvider extends Provider {

    public static final int DEFAULT_PRIORITY = 100;

    public default io.github.ljnelson.patchbay.logical.Configuration logicalModelFor(final PatchBay loader,
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
  public static non-sealed interface ConfigurationObjectProvider extends Provider {

    public static final int DEFAULT_PRIORITY = 100; // "first priority" priority, not "highest priority" priority

    // Precondition: configurationClass is actually a valid configuration class
    public default <T, U extends T> U configurationObjectFor(final PatchBay loader,
                                                             final io.github.ljnelson.patchbay.logical.Configuration logicalModel,
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
      // No need to sort. Because there may be many possible implementations of PatchBay.Configuration, PatchBay does
      // the sorting. See the PatchBay constructor.
      return ServiceLoader.load(ConfigurationObjectProvider.class)
        .stream()
        .map(ServiceLoader.Provider::get)
        .toList();
    }

    @Override
    public final List<LogicalModelProvider> logicalModelProviders() {
      // No need to sort. Because there may be many possible implementations of PatchBay.Configuration, PatchBay does
      // the sorting. See the PatchBay constructor.
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
                                                           final io.github.ljnelson.patchbay.logical.Configuration ignoredLogicalModel,
                                                           final Class<T> configurationClass)
    {
      return (U)configurationObjects.get(configurationClass);
    }

  }

}

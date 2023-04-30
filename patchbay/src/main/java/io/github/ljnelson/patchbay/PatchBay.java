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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import io.github.ljnelson.jakarta.config.ConfigException;
import io.github.ljnelson.jakarta.config.InvalidConfigurationClassException;
import io.github.ljnelson.jakarta.config.Loader;
import io.github.ljnelson.jakarta.config.NoSuchObjectException;

import jdk.incubator.concurrent.ScopedValue;

public final class PatchBay implements Loader {


  private static final ScopedValue<PatchBay> PATCHBAY = ScopedValue.newInstance();


  /*
   * Instance fields.
   */


  private final Configuration.Coordinates coordinates;

  private final ClassValue<ConfigurationObjectProvider> configurationObjectProvidersByClass;

  private final ClassValue<LogicalModelProvider> logicalModelProvidersByClass;


  /*
   * Constructors.
   */


  @Deprecated // for ServiceLoader usage
  public PatchBay() {
    this(Configuration.of());
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
          return PatchBay.this.findConfigurationObjectProviderFor(configurationObjectProviders, configurationClass);
        }
      };
    final List<LogicalModelProvider> unsortedLogicalModelProviders = new ArrayList<>(configuration.logicalModelProviders());
    Collections.sort(unsortedLogicalModelProviders, Comparator.comparingInt(LogicalModelProvider::priority)); // "first priority" priority, not "highest priority" priority
    final List<LogicalModelProvider> logicalModelProviders = Collections.unmodifiableList(unsortedLogicalModelProviders);
    this.logicalModelProvidersByClass = new ClassValue<>() {
        @Override
        protected final LogicalModelProvider computeValue(final Class<?> configurationClass) {
          return PatchBay.this.findLogicalModelProviderFor(logicalModelProviders, configurationClass);
        }
      };
  }


  /*
   * Public instance methods.
   */


  @Override
  public final <T> T load(final Class<T> configurationClass) {

    // Is this the bootstrap request? Bootstrap if so.
    if (configurationClass == Loader.class) {
      return configurationClass.cast(this.load());
    }

    validateConfigurationClass(configurationClass);

    // Step 1: find the persistent configuration as a logical model.
    final LogicalModel.Configuration logicalModel = this.findLogicalModelFor(configurationClass);

    // Step 2: ask the configurationObjectProvider to build a configuration object from the logical model.
    return this.findConfigurationObjectFor(logicalModel, configurationClass);
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
      // return new PatchBay(this.load(Configuration.class));
    } catch (final NoSuchObjectException e) {
      return this;
    } catch (final ConfigException e) {
      throw e;
    } catch (final Exception e) {
      throw new ConfigException(e.getMessage(), e);
    }
  }

  private final <T, L extends LogicalModel.Configuration> L findLogicalModelFor(final Class<T> configurationClass) {
    final LogicalModelProvider logicalModelProvider = this.logicalModelProvidersByClass.get(configurationClass);
    if (logicalModelProvider == null) {
      throw new NoSuchObjectException();
    }
    return logicalModelProvider.logicalModelFor(configurationClass);
  }

  private final LogicalModelProvider findLogicalModelProviderFor(final List<LogicalModelProvider> logicalModelProviders, final Class<?> configurationClass) {
    for (final LogicalModelProvider logicalModelProvider : logicalModelProviders) {
      if (logicalModelProvider.accepts(configurationClass)) {
        return logicalModelProvider;
      }
    }
    return null;
  }

  private final <L extends LogicalModel.Configuration, T, U extends T> T findConfigurationObjectFor(final L logicalModel, final Class<T> configurationClass) {
    final ConfigurationObjectProvider configurationObjectProvider = this.configurationObjectProvidersByClass.get(configurationClass);
    assert configurationObjectProvider != null;
    return configurationObjectProvider.configurationObjectFor(logicalModel, configurationClass);
  }

  private final ConfigurationObjectProvider findConfigurationObjectProviderFor(final List<ConfigurationObjectProvider> configurationObjectProviders, final Class<?> configurationClass) {
    for (final ConfigurationObjectProvider configurationObjectProvider : configurationObjectProviders) {
      if (configurationObjectProvider.accepts(configurationClass)) {
        return configurationObjectProvider;
      }
    }
    return ServiceLoaderConfigurationObjectProvider.INSTANCE;
  }


  /*
   * Private static methods.
   */


  private static final <T> Class<T> validateConfigurationClass(final Class<T> c) {
    if (!c.isInterface()) {
      throw new InvalidConfigurationClassException(c.getName());
    }
    if (c.getTypeParameters().length > 0) {
      throw new InvalidConfigurationClassException(c.getName());
    }
    if (c.isAnnotation()) {
      throw new InvalidConfigurationClassException(c.getName());
    }
    return c;
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

      public static Value ofRaw(final String string) {
        final record RawValue(String rawValue) implements Value {

          RawValue {
            Objects.requireNonNull(rawValue, "rawValue");
          }

          @Override
          public final Kind kind() {
            return Kind.RAW;
          }
          
          @Override
          public final String toString() {
            return this.rawValue();
          }
        };
        return new RawValue(string);
      }

      public static Value ofAbsence() {
        final record Absence() implements Value {          

          private static final Value INSTANCE = new Absence();

          @Override
          public final Kind kind() {
            return Kind.ABSENCE;
          }
          
          @Override
          public final String toString() {
            return "(missing)";
          }
        };
        return Absence.INSTANCE;
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

  }

  // Something that gets a glop of configuration relevant for a configuration class.
  public static interface LogicalModelProvider {

    public default <T, U extends LogicalModel.Configuration> U logicalModelFor(final Class<T> configurationClass) {
      return null;
    }

    public default int priority() {
      return 0;
    }

    public default boolean accepts(final Class<?> configurationClass) {
      return true;
    }

  }

  // Something that makes a configuration object.
  public static interface ConfigurationObjectProvider {

    // Precondition: configurationClass is actually a valid configuration class
    public default <L extends LogicalModel.Configuration, T, U extends T> U configurationObjectFor(final L logicalModel, final Class<T> configurationClass) {
      return null;
    }

    public default int priority() {
      return 0;
    }

    public default boolean accepts(final Class<?> configurationClass) {
      return true;
    }

  }

  // A default ConfigurationObjectProvider that is the last fallback and the one used for bootstrapping.
  public static final class ServiceLoaderConfigurationObjectProvider implements ConfigurationObjectProvider {

    private static final ServiceLoaderConfigurationObjectProvider INSTANCE = new ServiceLoaderConfigurationObjectProvider();

    private final ClassValue<ServiceLoader<?>> serviceLoaders;

    public ServiceLoaderConfigurationObjectProvider() {
      super();
      this.serviceLoaders = new ClassValue<>() {
          @Override
          protected final ServiceLoader<?> computeValue(final Class<?> configurationClass) {
            return ServiceLoader.load(configurationClass);
          }
        };
    }

    @Override
    public final int priority() {
      return Integer.MAX_VALUE; // "last priority" priority, not "highest priority" priority
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <L extends LogicalModel.Configuration, T, U extends T> U configurationObjectFor(final L ignoredLogicalModel, final Class<T> configurationClass) {
      final ServiceLoader<U> sl = (ServiceLoader<U>)this.serviceLoaders.get(configurationClass);
      synchronized (sl) {
        return sl.findFirst().orElseThrow(NoSuchObjectException::new);
      }
    }

  }

}

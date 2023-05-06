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
package io.github.ljnelson.patchbay.logical;

import java.lang.System.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.function.Function;

import static java.lang.System.Logger.Level.DEBUG;

public final class Configuration extends Value {

  private static final Logger logger = System.getLogger(Configuration.class.getName());

  private static final Configuration EMPTY_MODELED = new Configuration(true, Set.of(), k -> null);

  private static final Configuration EMPTY_UNMODELED = new Configuration(false, Set.of(), k -> null);

  private final boolean modeled;

  private final Map<String, Value> modeledValues;

  private final Function<? super String, ? extends Value> valueFunction;

  public Configuration(final boolean modeled, final Map<? extends String, ? extends Value> map) {
    this(modeled, map.keySet(), map::get);
  }

  public Configuration(final Configuration source) {
    this(source.modeled(), source.modeledKeys(), source::value);
  }

  public Configuration(final boolean modeled,
                       final Set<? extends String> modeledKeys,
                       final Function<? super String, ? extends Value> valueFunction) {
    super();
    this.modeled = modeled;
    this.valueFunction = Objects.requireNonNull(valueFunction, "valueFunction");
    if (modeledKeys.isEmpty()) {
      this.modeledValues = Map.of();
    } else {
      final Map<String, Value> modeledValues = new HashMap<>();
      for (final String modeledKey : modeledKeys) {
        modeledValues.put(modeledKey, valueFunction.apply(modeledKey)); // could put null; that's on purpose
      }
      this.modeledValues = Collections.unmodifiableMap(modeledValues);
    }
  }

  public Configuration(final Configuration c, final Configuration defaults) {
    super();
    this.modeled = c.modeled();
    final Set<String> modeledKeys;
    if (defaults == null) {
      modeledKeys = c.modeledKeys();
      this.valueFunction = c::value;
    } else {
      modeledKeys = new HashSet<>(c.modeledKeys());
      modeledKeys.addAll(defaults.modeledKeys());
      this.valueFunction = k -> value(c.value(k), defaults.value(k));
    }
    final Map<String, Value> modeledValues = new HashMap<>();
    for (final String modeledKey : modeledKeys) {
      modeledValues.put(modeledKey, this.valueFunction.apply(modeledKey)); // could put null; that's on purpose
    }
    this.modeledValues = Collections.unmodifiableMap(modeledValues);
  }

  @Override
  public final boolean modeled() {
    return this.modeled;
  }

  public final Set<String> modeledKeys() {
    return this.modeledValues.keySet();
  }

  public final Value value(final String key) {
    return this.modeledValues.containsKey(key) ? this.modeledValues.get(key) : this.valueFunction.apply(key);
  }

  @Override
  public final Kind kind() {
    return Kind.CONFIGURATION;
  }

  public static final Configuration ofModeled() {
    return EMPTY_MODELED;
  }

  public static final Configuration ofUnmodeled() {
    return EMPTY_UNMODELED;
  }

  private static final Value value(final Value sourceValue, final Value backupValue) {
    if (sourceValue == null) {
      return backupValue;
    } else if (backupValue == null) {
      return sourceValue;
    }
    return switch (sourceValue) {
    case Configuration sc -> switch (backupValue) {
      case Configuration bc -> new Configuration(sc, bc);
      default -> sc;
    };
    default -> sourceValue;
    };
  }


}

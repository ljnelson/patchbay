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
package io.github.ljnelson.patchbay.provider.logicalmodel.jackson.shared;

import java.io.IOException;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import io.github.ljnelson.jakarta.config.ConfigException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;

import io.github.ljnelson.patchbay.PatchBay;
import io.github.ljnelson.patchbay.PatchBay.LogicalModel;
import io.github.ljnelson.patchbay.PatchBay.LogicalModelProvider;

public abstract class AbstractJacksonLogicalModelProvider<C extends ObjectCodec, F extends JsonFactory> implements LogicalModelProvider {


  /*
   * Static fields.
   */


  private static final VarHandle CONFIG;

  static {
    try {
      CONFIG = MethodHandles.lookup().findVarHandle(AbstractJacksonLogicalModelProvider.class, "config", ProviderConfiguration.class);
    } catch (final IllegalAccessException | NoSuchFieldException e) {
      throw (ExceptionInInitializerError)new ExceptionInInitializerError(e.getMessage()).initCause(e);
    }
  }


  /*
   * Instance fields.
   */


  // Efficiently stores the set of canonical representations of configuration keys effectively declared by a
  // configuration class. This is an instance field because it uses the virtual computeKeys(Class<?>) method defined
  // below.
  private final ClassValue<Set<String>> keys;

  // My configuration.
  private volatile ProviderConfiguration config;


  /*
   * Constructors.
   */


  protected AbstractJacksonLogicalModelProvider() {
    super();
    this.keys = new ClassValue<>() {
        @Override
        protected final Set<String> computeValue(final Class<?> c) {
          return AbstractJacksonLogicalModelProvider.this.computeKeys(c);
        }
      };

  }


  /*
   * Instance methods.
   */


  protected abstract C codec(final Class<?> configurationClass);

  protected JsonParser parser(final Class<?> configurationClass, final F f) throws IOException {
    return null;
  }

  @Override
  public void configure(final PatchBay loader) {

  }

  protected final TreeNode treeNode(final Class<?> configurationClass) throws IOException {
    return this.treeNode(configurationClass, this.codec(configurationClass));
  }

  protected TreeNode treeNode(final Class<?> configurationClass, final C codec) throws IOException {
    final JsonParser parser = this.parser(configurationClass, (F)codec.getFactory());
    if (parser == null) {
      return codec.createObjectNode();
    }
    parser.setCodec(codec);
    return parser.readValueAsTree();
  }
  
  protected final LogicalModel.Configuration translate(final Class<?> configurationClass) {
    return this.translate(configurationClass, this.codec(configurationClass));
  }

  protected LogicalModel.Configuration translate(final Class<?> configurationClass, final C codec) {
    try {
      return this.translate(configurationClass, this.treeNode(configurationClass, codec), codec);
    } catch (final IOException e) {
      throw new ConfigException(e.getMessage(), e);
    }
  }

  // Turn a method into a configuration key canonical representation.
  protected String keyFor(final Method m) {
    return m.getName();
  }

  // Given a configuration class, return a Collection of canonical representations of configuration keys it logically
  // contains. Probably can move up to PatchBay level.
  final Set<String> keys(final Class<?> c) {
    return this.keys.get(c);
  }

  protected String rawValue(final boolean expected, final TreeNode v, final C codec) {
    try {
      return codec.treeToValue(v, String.class);
    } catch (final IOException e) {
      throw new ConfigException(e.getMessage(), e);
    }
  }

  @Override
  public <T, U extends LogicalModel.Configuration> U logicalModelFor(final PatchBay loader, final Class<T> configurationClass) {
    return (U)this.translate(configurationClass);
  }



  /*
   * Private instance methods.
   */


  private final LogicalModel.Configuration translate(final Class<?> configurationClass,
                                                     final TreeNode objectNode,
                                                     final C codec) {
    if (!PatchBay.configurationClass(configurationClass)) {
      throw new IllegalArgumentException("configurationClass: " + configurationClass);
    }
    return this.translateObjectNode(configurationClass, true, objectNode, codec);
  }

  private final LogicalModel.Value translateTreeNode(final Type t,
                                                     final boolean expected,
                                                     final TreeNode treeNode,
                                                     final C codec) {
    return switch (treeNode) {
    case TreeNode o when o.isObject() -> translateObjectNode(t, expected, o, codec);
    case TreeNode a when a.isArray() -> translateArrayNode(t, expected, a, codec);
    case TreeNode m when m.isMissingNode() -> translateMissingNode(m, expected, codec);
    case TreeNode v when v.isValueNode() -> translateValueNode(t, expected, v, codec);
    default -> throw new AssertionError();
    };
  }

  private final LogicalModel.Configuration translateObjectNode(final Type t,
                                                               final boolean expected,
                                                               final TreeNode objectNode,
                                                               final C codec) {
    if (!objectNode.isObject()) {
      throw new IllegalArgumentException();
    }
    if (objectNode.size() == 0) {
      return LogicalModel.Configuration.of();
    }
    final Map<String, LogicalModel.Value> map = new HashMap<>();
    final Set<String> expectedKeys = this.keys(t); // TODO: use
    final Iterator<String> fieldNamesIterator = objectNode.fieldNames();
    while (fieldNamesIterator.hasNext()) {
      final String fn = fieldNamesIterator.next();
      final String key = fieldNameToKey(fn);
      final Method accessor = this.methodFor(t, objectNode, fn, codec);
      map.put(fn, translateTreeNode(accessor == null ? null : accessor.getGenericReturnType(),
                                    key != null && expectedKeys.contains(key), // was it an expected value or not?
                                    objectNode.get(fn),
                                    codec));
    }
    return new AbstractJacksonLogicalModelProvider.Configuration(expected, expectedKeys, map);
  }

  private final String fieldNameToKey(final String fieldName) { // TODO: could be richer to permit some other strategy?
    return fieldName;
  }

  private final LogicalModel.ListValue translateArrayNode(final Type t,
                                                          final boolean expected,
                                                          final TreeNode arrayNode,
                                                          final C codec) {
    if (!arrayNode.isArray()) {
      throw new IllegalArgumentException("!arrayNode.isArray(): " + arrayNode);
    }
    if (!list(t)) {
      throw new IllegalArgumentException("t: " + t);
    }
    final int size = arrayNode.size();
    if (size == 0) {
      return LogicalModel.ListValue.of();
    }
    final Type listElementType = t instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
    final List<LogicalModel.Value> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(this.translateTreeNode(listElementType, expected, arrayNode.get(i), codec));
    }
    return new AbstractJacksonLogicalModelProvider.ListValue(expected, list);
  }

  private final LogicalModel.Value translateMissingNode(final TreeNode missingNode,
                                                        final boolean expected, // may not be needed
                                                        final C codec) {
    if (!missingNode.isMissingNode()) {
      throw new IllegalArgumentException("!missingNode.isMissingNode(): " + missingNode);
    }
    return expected ? LogicalModel.Absence.ofExpected() : LogicalModel.Absence.ofUnexpected();
  }

  private final LogicalModel.Value translateValueNode(final Type t,
                                                      final boolean expected,
                                                      final TreeNode valueNode,
                                                      final C codec) {
    if (!valueNode.isValueNode()) {
      throw new IllegalArgumentException("!valueNode.isValueNode(): " + valueNode);
    }
    return LogicalModel.RawValue.of(expected, this.rawValue(expected, valueNode, codec));
  }

  private final Set<String> keys(final Type t) {
    return switch (t) {
    case Class<?> c -> this.keys(c);
    case ParameterizedType p -> this.keys(p.getRawType());
    default -> Set.of();
    };
  }

  private final Method methodFor(final Type t,
                                 final TreeNode objectNode,
                                 final String fieldName,
                                 final C codec) {
    return switch (t) {
    case Class<?> c -> this.methodFor(c, objectNode, fieldName, codec);
    case ParameterizedType p -> this.methodFor(p.getRawType(), objectNode, fieldName, codec);
    default -> null;
    };
  }

  private final Method methodFor(final Class<?> c, // nullable
                                 final TreeNode objectNode,
                                 final String fieldName,
                                 final C codec) {
    if (!PatchBay.configurationClass(c)) {
      return null;
    }
    Method m;
    try {
      m = c.getMethod(fieldName);
    } catch (final NoSuchMethodException e) {
      return null;
    }
    return PatchBay.configurationKey(m) ? m : null;
  }

  // Given a Class, if it is a configuration class, return the set of canonical representations of the configuration
  // keys it logically declares. In all other cases return an empty set. Called once, ever, by the keys ClassValue
  // field.
  private final Set<String> computeKeys(final Class<?> configurationClass) {
    if (!PatchBay.configurationClass(configurationClass)) {
      return Set.of();
    }
    final Method[] methods = configurationClass.getMethods();
    if (methods.length == 0) {
      return Set.of();
    }
    final ArrayList<String> keys = new ArrayList<>(methods.length);
    for (final Method m : methods) {
      if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() > 0) {
        continue;
      }
      final String key = this.keyFor(m);
      if (key != null) {
        keys.add(key);
      }
    }
    keys.trimToSize(); // ...because keyFor() may have dropped a key
    return Set.copyOf(keys);
  }


  /*
   * Private static methods.
   */


  private static final boolean configurationClass(final Type t) {
    return t instanceof Class<?> c && PatchBay.configurationClass(c);
  }

  // Is c a List?
  private static final boolean list(final Class<?> c) {
    return List.class.isAssignableFrom(c);
  }

  // Is t a List?
  private static final boolean list(final Type t) {
    return switch (t) {
    case Class<?> c -> list(c);
    case ParameterizedType p -> list(p.getRawType());
    default -> false;
    };
  }



  /*
   * Inner and nested classes
   */


  protected static interface ProviderConfiguration {

  }

  static final class Configuration implements LogicalModel.Configuration {

    private final boolean expected;

    private final Set<String> keys;

    private final Set<String> expectedKeys;

    private final Set<String> unexpectedKeys;

    private final Function<? super String, ? extends LogicalModel.Value> valueFunction;

    Configuration(final boolean expected, final Set<? extends String> expectedKeys, final Map<? extends String, ? extends LogicalModel.Value> map) {
      super();
      this.expected = expected;
      final Map<String, LogicalModel.Value> m = Map.copyOf(map);
      this.expectedKeys = expectedKeys == null || expectedKeys.isEmpty() ? Set.of() : Set.copyOf(expectedKeys);
      Set<String> keys = new HashSet<>(m.keySet());
      keys.removeIf(this.expectedKeys::contains);
      this.unexpectedKeys = Set.copyOf(keys);
      keys.addAll(this.expectedKeys);
      this.keys = Collections.unmodifiableSet(keys);
      this.valueFunction = k -> {
        LogicalModel.Value v = m.get(k);
        if (v == null) {
          if (this.expectedKeys.contains(k)) {
            return LogicalModel.Absence.ofExpected();
          }
          assert !this.unexpectedKeys.contains(k) : "Somehow an unexpected key had a null value";
        } else {
          assert expectedKeys.contains(k) ? v.expected() : !v.expected();
        }
        return v;
      };
    }

    @Override
    public final boolean expected() {
      return this.expected;
    }

    public final Set<String> expectedKeys() {
      return this.expectedKeys;
    }

    public final Set<String> unexpectedKeys() {
      return this.unexpectedKeys;
    }

    @Override // LogicalModel.Configuration
    public final Set<String> keys() {
      return this.keys;
    }

    @Override // LogicalModel.Configuration
    public final LogicalModel.Value value(final String key) {
      return this.valueFunction.apply(key);
    }

  }

  static final class ListValue implements LogicalModel.ListValue {

    private final boolean expected;

    private final int size;

    private final IntFunction<? extends LogicalModel.Value> valueFunction;

    ListValue(final boolean expected, final List<? extends LogicalModel.Value> list) {
      super();
      this.expected = expected;
      this.size = list.size();
      this.valueFunction = list::get;
    }

    ListValue(final boolean expected, final int size, final IntFunction<? extends LogicalModel.Value> valueFunction ){
      super();
      this.expected = expected;
      this.size = Math.max(0, size);
      this.valueFunction = Objects.requireNonNull(valueFunction, "valueFunction");
    }

    @Override
    public final boolean expected() {
      return this.expected;
    }

    @Override
    public final int size() {
      return this.size;
    }

    @Override
    public final LogicalModel.Value value(final int index) {
      return this.valueFunction.apply(index);
    }

  }

}

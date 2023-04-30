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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import io.github.ljnelson.jakarta.config.ConfigException;

import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;

import io.github.ljnelson.patchbay.PatchBay.LogicalModel;
import io.github.ljnelson.patchbay.PatchBay.LogicalModelProvider;

public abstract class AbstractJacksonLogicalModelProvider implements LogicalModelProvider {


  /*
   * Static fields.
   */


  // Efficiently stores whether a given class is a configuration class or not.
  private static final ClassValue<Boolean> configurationClass = new ClassValue<>() {
      @Override
      protected final Boolean computeValue(final Class<?> c) {
        return AbstractJacksonLogicalModelProvider.computeConfigurationClass(c);
      }
    };


  /*
   * Instance fields.
   */


  // Efficiently stores the set of canonical representations of configuration keys effectively declared by a
  // configuration class.
  private final ClassValue<Set<String>> keys;


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


  /**
   * Translates the supplied {@link TreeNode} into a {@link LogicalModel.Configuration}, consulting the supplied
   * configuration class for relevant configuration keys, and the supplied {@link ObjectCodec} for representing certain
   * {@link TreeNode}s as {@link String}s, and returns the result.
   *
   * @param configurationClass the configuration class; must not be {@code null}; must be a configuration class
   *
   * @param treeNode the {@link TreeNode} to translate; must {@linkplain TreeNode#isObject() be an "object node"} in the
   * parlance of Jackson; must not be {@code null}
   *
   * @param codec an {@link ObjectCodec} used to represent a {@link TreeNode} that is a {@linkplain TreeNode#isValue()
   * "value node"} in the parlance of Jackson as a {@link String}; must not be {@code null}
   *
   * @return the resulting logical configuration model; never {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @exception ConfigException if any error occurs
   */
  protected final LogicalModel.Configuration translate(final Class<?> configurationClass,
                                                       final TreeNode treeNode,
                                                       final ObjectCodec codec) {
    return this.translateConfigurationClass(configurationClass, null, treeNode, codec);
  }

  // Turn a canonical representation of a configuration key into a Jackson TreeNode "field name".
  protected String fieldNameFor(final Class<?> configurationClass,
                                final String key,
                                final TreeNode objectNode,
                                final ObjectCodec codec) {
    return key;
  }

  private final String fieldNameFor(final Type type,
                                    final String key,
                                    final TreeNode objectNode,
                                    final ObjectCodec codec) {
    return switch (type) {
    case Class<?> c -> this.fieldNameFor(c, key, objectNode, codec);
    case ParameterizedType p -> this.fieldNameFor(p.getRawType(), key, objectNode, codec);
    default -> null;
    };
  }

  // Turn a method into a configuration key canonical representation.
  protected String keyFor(final Method m) {
    return m.getName();
  }

  // Given a canonical representation of a configuration key, and the configuration class it's "for", turn it into a
  // method name.
  protected <T> String methodNameFor(final Class<T> configurationClass, final String configurationKey) {
    return configurationKey;
  }

  // Given a configuration class, return a Collection of canonical representations of configuration keys it logically
  // contains.
  final Set<String> keys(final Class<?> c) {
    return this.keys.get(c);
  }

  private final Set<String> keys(final Type t) {
    return switch (t) {
    case Class<?> c -> this.keys(c);
    case ParameterizedType p -> this.keys(p.getRawType());
    default -> Set.of();
    };
  }

  // Return a Collection of canonical representations of configuration keys that the return type of the supplied public,
  // non-static, zero-argument accessor Method logically has.
  final Set<String> keys(final Method m) {
    final int modifiers = m.getModifiers();
    if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers) || !m.getDeclaringClass().isInterface() || m.getParameterCount() > 0) {
      return Set.of();
    }
    return this.keys(m.getReturnType());
  }

  protected String rawValue(final TreeNode v, final ObjectCodec codec) {
    try {
      return codec.treeToValue(v, String.class);
    } catch (final IOException e) {
      throw new ConfigException(e.getMessage(), e);
    }
  }


  /*
   * Private instance methods.
   */


  private final LogicalModel.Value translate(final Type t,
                                             final TreeNode treeNode,
                                             final ObjectCodec codec) {
    return this.translate(t, null, treeNode, codec);
  }

  private final LogicalModel.Value translate(final Type t,
                                             Set<? extends String> expectedKeys,
                                             final TreeNode treeNode,
                                             final ObjectCodec codec) {
    if (configurationClass(t)) {
      return this.translateConfigurationClass((Class<?>)t, expectedKeys, treeNode, codec);
    } else if (list(t)) {
      return this.translateList(t, treeNode, codec);
    }
    return this.translateOther(t, treeNode, codec);
  }

  private final LogicalModel.Configuration translateConfigurationClass(final Class<?> c,
                                                                       Set<? extends String> expectedKeys,
                                                                       final TreeNode objectNode,
                                                                       final ObjectCodec codec) {
    if (!configurationClass(c)) {
      throw new IllegalArgumentException("c: " + c);
    }
    if (!objectNode.isObject()) {
      throw new IllegalArgumentException("!objectNode.isObject(): " + objectNode);
    }
    if (expectedKeys == null) {
      expectedKeys = this.keys(c);
    }
    if (expectedKeys.isEmpty()) {
      return LogicalModel.Configuration.of();
    }
    final Map<String, LogicalModel.Value> map = new HashMap<>();
    for (final String key : expectedKeys) {
      // Every expected key will have a LogicalModel.Value for it, even if that Value is absence.
      final Method accessor = this.methodFor(c, key, objectNode, codec);
      if (accessor == null) {
        map.put(key, this.translate(null, Set.of(), codec.missingNode(), codec));
        continue;
      }
      final String fieldName = this.fieldNameFor(c, key, objectNode, codec);
      if (fieldName == null) {
        map.put(key, this.translate(null, Set.of(), codec.missingNode(), codec));
        continue;
      }
      final Type accessorType = accessor.getGenericReturnType();
      final Set<String> accessorKeys = this.keys(accessorType);
      final TreeNode value = objectNode.path(fieldName); // yes, path() not get(); see javadocs
      assert value != null; // could very well be a missing node, however
      map.put(key, this.translate(accessorType, accessorKeys, value, codec));
    }
    assert expectedKeys.equals(map.keySet());
    return new AbstractJacksonLogicalModelProvider.Configuration(map);
  }

  private final LogicalModel.ListValue translateList(final Type t, // nullable
                                                     final TreeNode treeNode,
                                                     final ObjectCodec codec) {
    if (!list(t)) {
      throw new IllegalArgumentException("t: " + t);
    }
    if (!treeNode.isArray()) {
      throw new IllegalArgumentException("!treeNode.isArray(): " + treeNode);
    }
    final int size = treeNode.size();
    if (size == 0) {
      return LogicalModel.ListValue.of();
    }
    final Type listElementType = t instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
    final List<LogicalModel.Value> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      final TreeNode listElementNode = treeNode.get(i);
      assert listElementNode != null;
      list.add(this.translate(listElementType, listElementNode, codec));
    }
    return new AbstractJacksonLogicalModelProvider.ListValue(list);
  }

  private final LogicalModel.Value translateOther(final Type t,
                                                  final TreeNode treeNode,
                                                  final ObjectCodec codec) {
    if (treeNode.isValueNode()) {
      return LogicalModel.Value.ofRaw(this.rawValue(treeNode, codec));
    } else if (treeNode.isMissingNode()) {
      return LogicalModel.Value.ofAbsence();
    } else if (configurationClass(t)) {
      throw new IllegalArgumentException("configurationClass(t): " + t + "; call translateConfigurationClass() instead");
    } else if (list(t)) {
      throw new IllegalArgumentException("list(t): " + t + "; call translateList() instead");
    } else {
      throw new IllegalArgumentException("treeNode: " + treeNode);
    }
  }

  // Given a canonical representation of a configuration key, and the configuration interface it's "for", turn it into a
  // public, non-static, zero-argument accessor Method if possible.
  private final Method methodFor(final Class<?> configurationClass,
                                 final String configurationKey,
                                 final TreeNode objectNode,
                                 final ObjectCodec codec) {
    if (configurationClass == null || !configurationClass.isInterface()) {
      // quick
      return null;
    }
    Method m;
    try {
      m = configurationClass.getMethod(this.methodNameFor(configurationClass, configurationKey));
    } catch (final NoSuchMethodException e) {
      return null;
    }
    final int modifiers = m.getModifiers();
    if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
      return null;
    }
    return m;
  }

  private final Method methodFor(final Type type,
                                 final String configurationKey,
                                 final TreeNode objectNode,
                                 final ObjectCodec codec) {
    return switch (type) {
    case Class<?> c -> this.methodFor(c, configurationKey, objectNode, codec);
    case ParameterizedType p -> this.methodFor(p.getRawType(), configurationKey, objectNode, codec);
    default -> null;
    };
  }

  // Given a Class, if it is a configuration class, return the set of canonical representations of the configuration
  // keys it logically declares. In all other cases return an empty set.
  private final Set<String> computeKeys(final Class<?> configurationClass) {
    if (configurationClass == null || !configurationClass.isInterface()) {
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


  // Is c a configuration class?
  private static final boolean configurationClass(final Class<?> c) {
    return c != null && configurationClass.get(c);
  }

  private static final boolean configurationClass(final Type t) {
    return t instanceof Class<?> c && configurationClass(c);
  }

  private static final boolean computeConfigurationClass(final Class<?> c) {
    if (c != null && c.isInterface()) {
      for (final Method m : c.getMethods()) { // public methods, declared and inherited
        if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0) {
          return true;
        }
      }
    }
    return false;
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


  static final class Configuration implements LogicalModel.Configuration {

    private final Set<String> keys;

    private final Function<? super String, ? extends LogicalModel.Value> valueFunction;

    Configuration(final Map<? extends String, ? extends LogicalModel.Value> map) {
      super();
      final Map<String, LogicalModel.Value> m = Map.copyOf(map);
      this.keys = m.keySet();
      this.valueFunction = m::get;
    }

    Configuration(final Set<? extends String> keys, final Function<? super String, ? extends LogicalModel.Value> valueFunction) {
      super();
      this.keys = Set.copyOf(keys);
      this.valueFunction = Objects.requireNonNull(valueFunction, "valueFunction");
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

    private final int size;

    private final IntFunction<? extends LogicalModel.Value> valueFunction;

    ListValue(final List<? extends LogicalModel.Value> list) {
      super();
      this.size = list.size();
      this.valueFunction = list::get;
    }

    ListValue(final int size, final IntFunction<? extends LogicalModel.Value> valueFunction ){
      super();
      this.size = Math.max(0, size);
      this.valueFunction = Objects.requireNonNull(valueFunction, "valueFunction");
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

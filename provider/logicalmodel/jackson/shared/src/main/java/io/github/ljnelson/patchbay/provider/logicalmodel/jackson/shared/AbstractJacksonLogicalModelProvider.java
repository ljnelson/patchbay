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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.lang.System.Logger;

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
import io.github.ljnelson.patchbay.PatchBay.LogicalModelProvider;

import io.github.ljnelson.patchbay.logical.Absence;
import io.github.ljnelson.patchbay.logical.Configuration;
import io.github.ljnelson.patchbay.logical.ListValue;
import io.github.ljnelson.patchbay.logical.RawValue;
import io.github.ljnelson.patchbay.logical.Value;

import io.github.ljnelson.patchbay.provider.logicalmodel.shared.AbstractLogicalModelProvider;

import static java.lang.System.Logger.Level.DEBUG;

public abstract class AbstractJacksonLogicalModelProvider<C extends ObjectCodec, F extends JsonFactory> extends AbstractLogicalModelProvider {


  /*
   * Static fields.
   */

  private static final Logger logger = System.getLogger(AbstractJacksonLogicalModelProvider.class.getName());


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
  // configuration class. This is an instance field because it uses the virtual computeModeledKeys(Class<?>) method
  // defined below.
  private final ClassValue<Set<String>> keys;

  private final Function<? super Class<?>, ? extends C> codecFunction;

  private final Function<? super Class<?>, ? extends InputStream> inputStreamFunction;

  // My configuration.
  private volatile ProviderConfiguration config;


  /*
   * Constructors.
   */


  protected AbstractJacksonLogicalModelProvider(final Function<? super Class<?>, ? extends C> codecFunction) {
    this(codecFunction, null);
  }
  
  protected AbstractJacksonLogicalModelProvider(final Function<? super Class<?>, ? extends C> codecFunction,
                                                final Function<? super Class<?>, ? extends InputStream> inputStreamFunction) {
    super();
    this.keys = new ClassValue<>() {
        @Override
        protected final Set<String> computeValue(final Class<?> c) {
          return AbstractJacksonLogicalModelProvider.this.computeModeledKeys(c);
        }
      };
    this.codecFunction = Objects.requireNonNull(codecFunction, "codecFunction");
    this.inputStreamFunction = inputStreamFunction == null ? c -> null : inputStreamFunction;
  }


  /*
   * Instance methods.
   */


  protected final C codec(final Class<?> configurationClass) {
    return this.codecFunction.apply(configurationClass);
  }

  protected JsonParser parser(final Class<?> configurationClass, final F f) throws IOException {
    final InputStream inputStream = this.inputStream(configurationClass);
    if (inputStream == null) {
      if (logger.isLoggable(DEBUG)) {
        logger.log(DEBUG, "No InputStream for " + configurationClass);
      }
      return null;
    }
    return f.createParser(inputStream);
  }

  protected final InputStream inputStream(final Class<?> configurationClass) {
    return this.inputStreamFunction.apply(configurationClass);
  }


  @Override
  public void configure(final PatchBay loader) {

  }

  protected final TreeNode treeNode(final Class<?> configurationClass) throws IOException {
    return this.treeNode(configurationClass, this.codec(configurationClass));
  }

  protected TreeNode treeNode(final Class<?> configurationClass, final C codec) throws IOException {
    @SuppressWarnings("unchecked")
    final JsonParser parser = this.parser(configurationClass, (F)codec.getFactory());
    if (parser == null) {
      return codec.createObjectNode();
    }
    TreeNode treeNode;
    try {
      parser.setCodec(codec);
      treeNode = parser.readValueAsTree();
    } finally {
      parser.close();
    }
    return treeNode;
  }

  public final Configuration translate(final Class<?> configurationClass) {
    return this.translate(configurationClass, this.codec(configurationClass));
  }

  protected Configuration translate(final Class<?> configurationClass, final C codec) {
    try {
      final TreeNode treeNode = this.treeNode(configurationClass, codec);
      if (logger.isLoggable(DEBUG)) {
        logger.log(DEBUG, "treeNode: " + treeNode);
      }
      return this.translate(configurationClass, treeNode, codec);
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
  public final Set<String> modeledKeys(final Class<?> c) {
    return this.keys.get(c);
  }

  protected String rawValue(final boolean modeled, final TreeNode v, final C codec) {
    try {
      return codec.treeToValue(v, String.class);
    } catch (final IOException e) {
      throw new ConfigException(e.getMessage(), e);
    }
  }

  @Override
  public Configuration logicalModelFor(final PatchBay loader, final Class<?> configurationClass) {
    return this.translate(configurationClass);
  }



  /*
   * Private instance methods.
   */


  private final Configuration translate(final Class<?> configurationClass,
                                        final TreeNode objectNode,
                                        final C codec) {
    if (!PatchBay.configurationClass(configurationClass)) {
      throw new IllegalArgumentException("configurationClass: " + configurationClass);
    }
    return this.translateObjectNode(configurationClass, true, objectNode, codec);
  }

  private final Value translateTreeNode(final Type t,
                                        final boolean modeled,
                                        final TreeNode treeNode,
                                        final C codec) {
    return switch (treeNode) {
    case TreeNode o when o.isObject() -> translateObjectNode(t, modeled, o, codec);
    case TreeNode a when a.isArray() -> translateArrayNode(t, modeled, a, codec);
    case TreeNode m when m.isMissingNode() -> translateMissingNode(m, modeled, codec);
    case TreeNode v when v.isValueNode() -> translateValueNode(t, modeled, v, codec);
    default -> throw new AssertionError();
    };
  }

  private final Configuration translateObjectNode(final Type t,
                                                  final boolean modeled,
                                                  final TreeNode objectNode,
                                                  final C codec) {
    if (!objectNode.isObject()) {
      throw new IllegalArgumentException();
    }
    final Map<String, Value> map = new HashMap<>();
    final Set<String> modeledKeys = this.modeledKeys(t);
    final Iterator<String> fieldNamesIterator = objectNode.fieldNames();
    while (fieldNamesIterator.hasNext()) {
      final String fieldName = fieldNamesIterator.next();
      final String key = this.fieldNameToKey(fieldName, modeledKeys);      
      if (key != null) {
        map.put(key, this.translateTreeNode(this.typeFor(t, key),
                                            modeledKeys.contains(key), // was it a modeled value or not?
                                            objectNode.get(fieldName), // the value (will never be null)
                                            codec));
      }
    }
    return new Configuration(modeled, modeledKeys, map::get);
  }

  // Some future revision of this method may return null to indicate: don't store this key.
  // The returned key need not come from modeledKey, and often does not. It is often just the fieldName itself.
  private final String fieldNameToKey(final String fieldName, final Set<String> modeledKeys) { // TODO: could be richer to permit some other strategy?
    return fieldName;
  }

  private final ListValue translateArrayNode(final Type t,
                                             final boolean modeled,
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
      return ListValue.of();
    }
    final Type listElementType = t instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
    final List<Value> list = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      list.add(this.translateTreeNode(listElementType, modeled, arrayNode.get(i), codec));
    }
    return new ListValue(modeled, list);
  }

  // Here mostly for completeness; never actually called because translateObjectNode() never calls
  // treeNode.path(fieldName); it always calls treeNode.get(fieldName)
  private final Value translateMissingNode(final TreeNode missingNode,
                                           final boolean modeled, // may not be needed
                                           final C codec) {
    if (!missingNode.isMissingNode()) {
      throw new IllegalArgumentException("!missingNode.isMissingNode(): " + missingNode);
    }
    return modeled ? Absence.ofModeled() : Absence.ofUnmodeled();
  }

  private final Value translateValueNode(final Type t,
                                         final boolean modeled,
                                         final TreeNode valueNode,
                                         final C codec) {
    if (!valueNode.isValueNode()) {
      throw new IllegalArgumentException("!valueNode.isValueNode(): " + valueNode);
    }
    return new RawValue(modeled, this.rawValue(modeled, valueNode, codec));
  }

  private final Set<String> modeledKeys(final Type t) {
    return switch (t) {
    case null -> Set.of();
    case Class<?> c -> this.modeledKeys(c);
    case ParameterizedType p -> this.modeledKeys(p.getRawType());
    default -> Set.of();
    };
  }

  private final Type typeFor(final Type t, final String key) {
    return switch (t) {
    case null -> null;
    case Class<?> c -> this.typeFor(c, key);
    case ParameterizedType p -> this.typeFor(p.getRawType(), key);
    default -> null;
    };
  }

  private final Type typeFor(final Class<?> c, final String key) {
    if (!PatchBay.configurationClass(c)) {
      return null;
    }
    Method m;
    try {
      m = c.getMethod(key);
    } catch (final NoSuchMethodException e) {
      return null;
    }
    return PatchBay.configurationKey(m) ? m.getGenericReturnType() : null;
  }

  // Given a Class, if it is a configuration class, return the set of canonical representations of the configuration
  // keys it logically declares. In all other cases return an empty set. Called once, ever, by the keys ClassValue
  // field.
  private final Set<String> computeModeledKeys(final Class<?> configurationClass) {
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
    return c != null && List.class.isAssignableFrom(c);
  }

  // Is t a List?
  private static final boolean list(final Type t) {
    return switch (t) {
    case null -> false;
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

  /*
  static final class Configuration implements LogicalModel.Configuration {

    private final boolean modeled;

    private final Set<String> keys;

    private final Set<String> modeledKeys;

    private final Set<String> unmodeledKeys;

    private final Function<? super String, ? extends LogicalModel.Value> valueFunction;

    Configuration(final boolean modeled, final Set<? extends String> modeledKeys, final Map<? extends String, ? extends LogicalModel.Value> map) {
      super();
      this.modeled = modeled;
      final Map<String, LogicalModel.Value> m = Map.copyOf(map);
      this.modeledKeys = modeledKeys == null || modeledKeys.isEmpty() ? Set.of() : Set.copyOf(modeledKeys);
      Set<String> keys = new HashSet<>(m.keySet());
      keys.removeIf(this.modeledKeys::contains);
      this.unmodeledKeys = Set.copyOf(keys);
      keys.addAll(this.modeledKeys);
      this.keys = Collections.unmodifiableSet(keys);
      this.valueFunction = k -> {
        LogicalModel.Value v = m.get(k);
        if (v == null) {
          if (this.modeledKeys.contains(k)) {
            return LogicalModel.Absence.ofModeled();
          }
          assert !this.unmodeledKeys.contains(k) : "Somehow an unmodeled key had a null value";
        } else {
          assert modeledKeys.contains(k) ? v.modeled() : !v.modeled();
        }
        return v;
      };
    }

    @Override
    public final boolean modeled() {
      return this.modeled;
    }

    public final Set<String> modeledKeys() {
      return this.modeledKeys;
    }

    public final Set<String> unmodeledKeys() {
      return this.unmodeledKeys;
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

    private final boolean modeled;

    private final int size;

    private final IntFunction<? extends LogicalModel.Value> valueFunction;

    ListValue(final boolean modeled, final List<? extends LogicalModel.Value> list) {
      super();
      this.modeled = modeled;
      this.size = list.size();
      this.valueFunction = list::get;
    }

    ListValue(final boolean modeled, final int size, final IntFunction<? extends LogicalModel.Value> valueFunction ){
      super();
      this.modeled = modeled;
      this.size = Math.max(0, size);
      this.valueFunction = Objects.requireNonNull(valueFunction, "valueFunction");
    }

    @Override
    public final boolean modeled() {
      return this.modeled;
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
  */

}

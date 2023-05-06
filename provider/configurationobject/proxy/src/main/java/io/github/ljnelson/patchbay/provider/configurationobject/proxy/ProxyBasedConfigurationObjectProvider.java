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
package io.github.ljnelson.patchbay.provider.configurationobject.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.ljnelson.jakarta.config.NoSuchObjectException;

import io.github.ljnelson.patchbay.PatchBay;
import io.github.ljnelson.patchbay.PatchBay.ConfigurationObjectProvider;

import io.github.ljnelson.patchbay.logical.Absence;
import io.github.ljnelson.patchbay.logical.Configuration;
import io.github.ljnelson.patchbay.logical.ListValue;
import io.github.ljnelson.patchbay.logical.RawValue;
import io.github.ljnelson.patchbay.logical.Value;

public class ProxyBasedConfigurationObjectProvider implements ConfigurationObjectProvider {

  private static final ConcurrentMap<ProxyKey, Proxy> proxies = new ConcurrentHashMap<>();

  public ProxyBasedConfigurationObjectProvider() {
    super();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T, U extends T> U configurationObjectFor(final PatchBay loader,
                                                   final Configuration logicalModel,
                                                   final Class<T> configurationClass) {
    return
      (U)proxies.computeIfAbsent(new ProxyKey(configurationClass, logicalModel),
                                 pk -> (Proxy)Proxy.newProxyInstance(configurationClass.getClassLoader(),
                                                                     new Class<?>[] { configurationClass },
                                                                     new Handler(loader, logicalModel)));
  }

  private final class Handler implements InvocationHandler {

    private final PatchBay loader;

    private final Configuration logicalModel;

    private Handler(final PatchBay loader, final Configuration logicalModel) {
      super();
      this.loader = loader;
      this.logicalModel = Objects.requireNonNull(logicalModel, "logicalModel");
    }

    @Override
    public final Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
      switch (method) {
      case Method om when om.getDeclaringClass() == Object.class:
        return switch (om.getName()) {
        case "equals" -> proxy == args[0];
        case "hashCode" -> System.identityHashCode(proxy);
        case "toString" -> proxy.getClass().getName();
        default -> throw new AssertionError();
        };
      case Method m when m.getParameterCount() == 0 && Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()):
        final Value v = logicalModel.value(method.getName()); // sketch to start
        return switch (v) {
        case null -> {
          if (method.isDefault()) {
            yield InvocationHandler.invokeDefault(proxy, method, args);
          }
          throw new UnsupportedOperationException(method.getName());
        }
        case Absence a -> throw new NoSuchObjectException();
        case Configuration c -> this.loader.computeConfigurationObject(c, method.getReturnType());
        case ListValue l -> throw new UnsupportedOperationException("TODO: implement");
        case RawValue r -> r.value();
        };
      default:
        throw new UnsupportedOperationException();
      }
    }

  }

  private static record ProxyKey(Class<?> configurationClass, Configuration logicalModel) {}

}

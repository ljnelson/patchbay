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

import io.github.ljnelson.jakarta.config.NoSuchObjectException;

import io.github.ljnelson.patchbay.PatchBay;
import io.github.ljnelson.patchbay.PatchBay.ConfigurationObjectProvider;

import io.github.ljnelson.patchbay.logical.Configuration;

public class ProxyBasedProvider implements ConfigurationObjectProvider {

  public ProxyBasedProvider() {
    super();
  }

  @Override
  public <T, U extends T> U configurationObjectFor(final PatchBay loader,
                                                   final Configuration logicalModel,
                                                   final Class<T> configurationClass) {
    throw new NoSuchObjectException();
  }
  
}

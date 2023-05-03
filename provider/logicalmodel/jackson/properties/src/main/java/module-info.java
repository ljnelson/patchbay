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

import io.github.ljnelson.patchbay.PatchBay.LogicalModelProvider;

import io.github.ljnelson.patchbay.provider.logicalmodel.jackson.properties.JacksonSystemPropertiesLogicalModelProvider;
import io.github.ljnelson.patchbay.provider.logicalmodel.jackson.properties.JacksonEnvironmentVariablesLogicalModelProvider;

module io.github.ljnelson.patchbay.provider.logicalmodel.jackson.properties {

  exports io.github.ljnelson.patchbay.provider.logicalmodel.jackson.properties;

  requires transitive com.fasterxml.jackson.dataformat.javaprop;
  
  requires transitive io.github.ljnelson.patchbay;

  requires transitive io.github.ljnelson.patchbay.provider.logicalmodel.jackson.shared;

  provides LogicalModelProvider
    with JacksonSystemPropertiesLogicalModelProvider,
         JacksonEnvironmentVariablesLogicalModelProvider;

}

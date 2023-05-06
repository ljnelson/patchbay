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

@SuppressWarnings("module")
module io.github.ljnelson.patchbay.provider.logicalmodel.jackson.applicationtoml {

  exports io.github.ljnelson.patchbay.provider.logicalmodel.jackson.applicationtoml to test;
  
  requires transitive com.fasterxml.jackson.core;

  requires transitive com.fasterxml.jackson.databind;

  requires transitive com.fasterxml.jackson.dataformat.toml;

  requires transitive io.github.ljnelson.patchbay;

  requires transitive io.github.ljnelson.patchbay.provider.logicalmodel.jackson.shared;

  provides io.github.ljnelson.patchbay.PatchBay.LogicalModelProvider
    with io.github.ljnelson.patchbay.provider.logicalmodel.jackson.applicationtoml.JacksonApplicationTomlClasspathResourceLogicalModelProvider;

}

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
package io.github.ljnelson.patchbay.provider.logicalmodel.jackson.applicationtoml;

import java.lang.System.Logger;

import com.fasterxml.jackson.databind.ObjectReader;

import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import io.github.ljnelson.patchbay.provider.logicalmodel.jackson.shared.AbstractJacksonLogicalModelProvider;

public class JacksonApplicationTomlClasspathResourceLogicalModelProvider extends AbstractJacksonLogicalModelProvider<ObjectReader, TomlFactory> {

  private static final Logger logger = System.getLogger(JacksonApplicationTomlClasspathResourceLogicalModelProvider.class.getName());
  
  public JacksonApplicationTomlClasspathResourceLogicalModelProvider() {
    this(new TomlMapper());
  }

  public JacksonApplicationTomlClasspathResourceLogicalModelProvider(final TomlMapper tomlMapper) {
    super(c -> tomlMapper.reader(),
          c -> c.getResourceAsStream("/application.toml"));

  }

  @Override
  public final String toString() {
    return this.getClass().getName() + ": /application.toml";
  }
  
}

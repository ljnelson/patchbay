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
package io.github.ljnelson.patchbay.provider.logicalmodel.jackson.applicationproperties;

import java.lang.System.Logger;

import com.fasterxml.jackson.databind.ObjectReader;

import com.fasterxml.jackson.dataformat.javaprop.JavaPropsFactory;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsSchema;

import io.github.ljnelson.patchbay.provider.logicalmodel.jackson.shared.AbstractJacksonLogicalModelProvider;

public class JacksonApplicationPropertiesClasspathResourceLogicalModelProvider extends AbstractJacksonLogicalModelProvider<ObjectReader, JavaPropsFactory> {

  private static final Logger logger = System.getLogger(JacksonApplicationPropertiesClasspathResourceLogicalModelProvider.class.getName());
  
  public JacksonApplicationPropertiesClasspathResourceLogicalModelProvider() {
    this(new JavaPropsMapper());
  }

  public JacksonApplicationPropertiesClasspathResourceLogicalModelProvider(final JavaPropsMapper propertiesMapper) {
    super(c -> propertiesMapper.reader(),
          c -> c.getResourceAsStream("/application.properties"));

  }

  @Override
  public final String toString() {
    return this.getClass().getName() + ": /application.properties";
  }
  
}

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
package io.github.ljnelson.patchbay.provider.logicalmodel.jackson.environment;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.fasterxml.jackson.dataformat.javaprop.JavaPropsFactory;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsSchema;

import io.github.ljnelson.patchbay.provider.logicalmodel.jackson.shared.AbstractJacksonLogicalModelProvider;

public final class JacksonSystemPropertiesLogicalModelProvider extends AbstractJacksonLogicalModelProvider<JavaPropsMapper, JavaPropsFactory> {

  public JacksonSystemPropertiesLogicalModelProvider() {
    this(new JavaPropsMapper());
  }

  public JacksonSystemPropertiesLogicalModelProvider(final JavaPropsMapper codec) {
    super(c -> codec);
  }

  @Override
  protected final ObjectNode treeNode(final Class<?> configurationClass, final JavaPropsMapper codec) throws IOException {
    return codec.readSystemPropertiesAs(JavaPropsSchema.emptySchema().withoutPathSeparator(), ObjectNode.class);
  }

  @Override
  public final String toString() {
    return this.getClass().getName() + ": System properties";
  }
  
}

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
package io.github.ljnelson.patchbay.provider.logicalmodel.jackson.properties;

import java.io.IOException;

import com.fasterxml.jackson.core.TreeNode;

import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsSchema;

public final class JacksonEnvironmentVariablesLogicalModelProvider extends JacksonPropertiesLogicalModelProvider {

  public JacksonEnvironmentVariablesLogicalModelProvider() {
    super();
  }

  @Override
  public final int priority() {
    return 50; // "first priority" priority, not "lowest priority" priority
  }
  
  @Override
  protected final TreeNode treeNode(final Class<?> configurationClass, final JavaPropsMapper codec) throws IOException {
    final TreeNode x = codec.createObjectNode();
    @SuppressWarnings("unchecked")
    final Class<TreeNode> c = (Class<TreeNode>)x.getClass();
    return codec.readEnvVariablesAs(JavaPropsSchema.emptySchema().withoutPathSeparator(), c);
  }
  
}

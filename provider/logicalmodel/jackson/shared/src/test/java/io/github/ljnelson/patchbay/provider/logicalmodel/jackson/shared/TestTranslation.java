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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ljnelson.patchbay.PatchBay.LogicalModel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestTranslation {

  private AbstractJacksonLogicalModelProvider p;

  private ObjectMapper codec;

  private TestTranslation() {
    super();
  }

  @BeforeEach
  final void setUp() {
    this.p = new AbstractJacksonLogicalModelProvider() {};
    this.codec = new ObjectMapper();
  }

  @Test
  final void testExepctedKeys() {
    final Set<String> keys = Set.copyOf(this.p.keys(TraversingConfiguration.class));
    assertEquals(3, keys.size());
    assertTrue(keys.contains("toString"));
    assertTrue(keys.contains("fieldA"));
    assertTrue(keys.contains("fieldB"));
  }

  @Test
  final void testKeysForMethod() throws NoSuchMethodException {
    final Method fieldA = TraversingConfiguration.class.getMethod("fieldA");
    assertEquals(Set.of(), Set.copyOf(this.p.keys(fieldA)));
    final Method fieldB = TraversingConfiguration.class.getMethod("fieldB");
    assertEquals(Set.of("fieldC"), Set.copyOf(this.p.keys(fieldB)));
  }

  @Test
  final void testTranslation() throws IOException, ReflectiveOperationException {
    final TreeNode treeNode = codec.readTree("{ \"frequency\" : 32,\n  \"removeMe\" : \"should not see this\"\n}");
    final LogicalModel.Configuration c = p.translate(SampleConfigurationClass.class, treeNode, codec);
    assertSame(LogicalModel.Value.Kind.CONFIGURATION, c.kind());
    assertEquals(Set.of("frequency"), c.keys());
    assertEquals(LogicalModel.Value.ofRaw("32"), c.value("frequency"));
  }

  @Test
  final void testTraversingWithTreeNodes() throws IOException {
    final AbstractJacksonLogicalModelProvider p = new AbstractJacksonLogicalModelProvider() {
        @Override
        protected String keyFor(final Method m) {
          if (m.getName().equals("fieldA")) {
            return null;
          }
          return super.keyFor(m);
        }
      };
    final JsonParser parser =
      codec.createParser("""
                         {
                           "fieldA" : "valueA",
                           "fieldB" : {
                             "fieldC" : "valueC",
                             "fieldD" : "valueD"
                           }
                         }
                         """);
    final TreeNode treeNode = parser.readValueAsTree();
    final LogicalModel.Configuration c = p.translate(TraversingConfiguration.class, treeNode, codec);
    assertEquals(Set.of("fieldB"), c.keys()); // XXX fails because toString()
  }

  private static interface SampleConfigurationClass {

    public default int frequency() {
      return 4;
    }

  }

  private static interface TraversingConfiguration {

    public String fieldA();

    public SubConfiguration fieldB();

    @Override
    public String toString();

    static interface SubConfiguration {

      public String fieldC();

      // fieldD() omitted on purpose

    }

  }

  private static interface ConfigurationWithLists {

    public List<String> listA();
    
  }

}

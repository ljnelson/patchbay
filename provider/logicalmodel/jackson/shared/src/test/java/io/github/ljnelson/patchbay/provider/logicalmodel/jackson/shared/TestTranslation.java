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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ljnelson.patchbay.PatchBay.LogicalModel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestTranslation {

  private ObjectMapper codec;

  private TestTranslation() {
    super();
  }

  @BeforeEach
  final void setUp() {
    final ObjectMapper codec = new ObjectMapper();
    this.codec = codec;
  }

  @Test
  final void testExpectedKeys() {
    final Provider p = new Provider();
    final Set<String> keys = p.keys(TraversingConfiguration.class);
    assertEquals(3, keys.size());
    assertTrue(keys.contains("toString"));
    assertTrue(keys.contains("fieldA"));
    assertTrue(keys.contains("fieldB"));
  }

  @Test
  final void testTranslate() throws JsonProcessingException {
    final Provider p = new Provider("""
                                    {
                                      "fieldA" : "valueA",
                                        "fieldB" : {
                                        "fieldC" : "valueC",
                                          "fieldD" : "valueD"
                                          }
                                    }
                                    """) {
        @Override
        protected String keyFor(final Method m) {
          final String methodName = m.getName();
          if (methodName.equals("fieldA") || methodName.equals("toString")) {
            return null;
          }
          return super.keyFor(m);
        }
      };
    final LogicalModel.Configuration top = p.translate(TraversingConfiguration.class);
    assertEquals(Set.of("fieldA", "fieldB"), top.keys());
    final LogicalModel.Value a = top.value("fieldA");
    final LogicalModel.Configuration b = (LogicalModel.Configuration)top.value("fieldB");
    assertEquals(Set.of("fieldC", "fieldD"), b.keys());
    final LogicalModel.Value c = b.value("fieldC");
    assertSame(c.kind(), LogicalModel.Value.Kind.RAW);
    final LogicalModel.Value d = b.value("fieldD");
    assertSame(d.kind(), LogicalModel.Value.Kind.RAW);
    final LogicalModel.Value missing = b.value("fieldX");
    assertNull(missing); // for now
  }

  private static class Provider extends AbstractJacksonLogicalModelProvider<ObjectCodec, JsonFactory> {

    private final ObjectCodec codec;
    
    private final String json;

    private Provider() {
      this("{}");
    }
    
    private Provider(String json) {
      super();
      this.codec = new ObjectMapper();
      this.json = Objects.requireNonNull(json);
    }
    
    @Override
    protected final ObjectCodec codec(final Class<?> configurationClass) {
      return this.codec;
    }
    
    protected final JsonParser parser(final Class<?> configurationClass, final JsonFactory f) throws IOException {
      return f.createParser(this.json);
    }
    
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

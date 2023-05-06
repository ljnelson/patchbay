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
package test;

import io.github.ljnelson.patchbay.PatchBay;

import io.github.ljnelson.patchbay.logical.Configuration;
import io.github.ljnelson.patchbay.logical.RawValue;
import io.github.ljnelson.patchbay.logical.Value;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestSpike {

  private PatchBay loader;

  private TestSpike() {
    super();
  }

  @BeforeEach
  @SuppressWarnings("deprecation")
  final void setUp() {
    this.loader = new PatchBay();
  }

  @Test
  final void test() {
    final Dummy dummy = this.loader.load(Dummy.class);
    assertNotNull(dummy);
    assertEquals("b", dummy.a());
    final Dummy.Subassembly b = dummy.b();
    assertEquals("d", b.c());
  }

  public static interface Dummy {

    public String a();

    public Subassembly b();

    public static interface Subassembly {

      public String c();

    }

  }

}

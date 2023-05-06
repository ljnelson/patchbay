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
package io.github.ljnelson.patchbay.logical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ListValue extends Value {

  private static final ListValue EMPTY = new ListValue(false, List.of());
  
  private final boolean modeled;

  private final List<Value> values;
  
  public ListValue(final boolean modeled, final Iterable<? extends Value> values) {
    super();
    this.modeled = modeled;
    if (values instanceof List) {
      this.values = List.copyOf((List<? extends Value>)values);
    } else {
      final ArrayList<Value> list = new ArrayList<>();
      for (final Value value : values) {
        list.add(value);
      }
      list.trimToSize();
      this.values = Collections.unmodifiableList(list);
    }
  }

  @Override
  public final boolean modeled() {
    return this.modeled;
  }

  @Override
  public final Kind kind() {
    return Kind.LIST;
  }

  public final int size() {
    return this.values.size();
  }

  public final Value value(final int index) {
    return this.values.get(index);
  }

  @Override
  public final String toString() {
    return this.values.toString();
  }

  public static final ListValue of() {
    return EMPTY;
  }
  
}

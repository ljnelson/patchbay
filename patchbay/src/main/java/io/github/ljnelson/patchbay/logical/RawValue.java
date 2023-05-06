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

public final class RawValue extends Value {

  private final boolean modeled;

  private final Object value;
  
  public RawValue(final boolean modeled, final Object value) {
    super();
    this.modeled = modeled;
    this.value = value;
  }

  @Override
  public final boolean modeled() {
    return this.modeled;
  }

  public final Object value() {
    return this.value;
  }

  @Override
  public final Kind kind() {
    return Kind.RAW;
  }

  @Override
  public final String toString() {
    return String.valueOf(this.value());
  }
  
}

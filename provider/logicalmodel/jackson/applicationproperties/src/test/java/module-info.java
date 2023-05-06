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

module test {

  // Open our test package to JUnit
  opens test to org.junit.platform.commons;
  
  // The module under test
  requires transitive io.github.ljnelson.patchbay.provider.logicalmodel.jackson.applicationproperties;

  requires org.junit.jupiter.api;

  // This bothers me. It is clear that the engine must be on the module path at test time, but this test module does not in
  // fact do anything with it. The "requires" syntax suggests incorrectly that it does. A better approach would be to always and everywhere do --add-modules org.junit.jupiter.engine
  // requires org.junit.jupiter.engine;
  
}

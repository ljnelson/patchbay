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
package io.github.ljnelson.patchbay.cdi;

import java.util.ArrayList;
import java.util.List;

import io.github.ljnelson.jakarta.config.Configuration;
import io.github.ljnelson.jakarta.config.Loader;

import io.github.ljnelson.patchbay.PatchBay;

import jakarta.enterprise.event.Observes;

import jakarta.inject.Singleton;

import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;

public class PatchBayExtension implements Extension {

  private final List<AnnotatedType<?>> types;

  private final Loader loader;
  
  public PatchBayExtension() {
    super();
    this.loader = Loader.bootstrap();
    this.types = new ArrayList<>(3);
  }

  private void addConfigurationAsQualifier(@Observes final BeforeBeanDiscovery event) {
    event.addQualifier(Configuration.class);
  }
  
  private void processConfigurationClass(@Observes
                                         @WithAnnotations(Configuration.class)
                                         final ProcessAnnotatedType<?> event) {
    final AnnotatedType<?> t = event.getAnnotatedType();
    if (t.isAnnotationPresent(Configuration.class) && PatchBay.configurationClass(t.getJavaClass())) {
      this.types.add(t); // to be culled via vetoes perhaps
    }
  }

  private void addBeans(@Observes final AfterBeanDiscovery event) {
    for (final AnnotatedType<?> t : this.types) {
      final Class<?> c = t.getJavaClass();
      assert c.isInterface();
      event.addBean()
        .scope(Singleton.class)
        .types(c)
        .qualifiers(t.getAnnotation(Configuration.class))
        .createWith(cc -> this.loader.load(c));
    }
  }
  
}

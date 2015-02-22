/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.caliper.model;

import static com.google.caliper.model.PersistentHashing.getPersistentHashFunction;
import static javax.persistence.AccessType.FIELD;
import static org.hibernate.annotations.SortType.NATURAL;

import java.util.Map;
import java.util.SortedMap;

import javax.persistence.Access;
import javax.persistence.Cacheable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.QueryHint;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Index;
import org.hibernate.annotations.Sort;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;

/**
 * A configuration of a virtual machine.
 *
 * @author gak@google.com (Gregory Kick)
 */
@Entity
@Access(FIELD)
@Immutable
@Cacheable
@NamedQuery(
    name = "listVmSpecsForHash",
    query = "SELECT v FROM VmSpec v WHERE hash = :hash",
    hints = {
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")})
public final class VmSpec {
  static final VmSpec DEFAULT = new VmSpec();

  @Id @GeneratedValue @ExcludeFromJson private int id;
  @ElementCollection @Sort(type = NATURAL) private SortedMap<String, String> properties;
  @ElementCollection @Sort(type = NATURAL) private SortedMap<String, String> options;
  @ExcludeFromJson @Index(name = "hash_index") private int hash;

  private VmSpec() {
    this.properties = Maps.newTreeMap();
    this.options = Maps.newTreeMap();
  }

  private VmSpec(Builder builder) {
    this.properties = Maps.newTreeMap(builder.properties);
    this.options = Maps.newTreeMap(builder.options);
  }

  public ImmutableSortedMap<String, String> options() {
    return ImmutableSortedMap.copyOf(options);
  }

  public ImmutableSortedMap<String, String> properties() {
    return ImmutableSortedMap.copyOf(properties);
  }

  @Override public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof VmSpec) {
      VmSpec that = (VmSpec) obj;
      return this.properties.equals(that.properties)
          && this.options.equals(that.options);
    } else {
      return false;
    }
  }

  @PrePersist
  @PreUpdate
  private void initHash() {
    if (hash == 0) {
      this.hash = getPersistentHashFunction().hashObject(this, VmSpecFunnel.INSTANCE).asInt();
    }
  }

  @Override public int hashCode() {
    initHash();
    return hash;
  }

  @Override public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("properties", properties)
        .add("options", options)
        .toString();
  }

  enum VmSpecFunnel implements Funnel<VmSpec> {
    INSTANCE;

    @Override public void funnel(VmSpec from, PrimitiveSink into) {
      StringMapFunnel.INSTANCE.funnel(from.properties, into);
      StringMapFunnel.INSTANCE.funnel(from.options, into);
    }
  }

  public static final class Builder {
    private final SortedMap<String, String> properties = Maps.newTreeMap();
    private final SortedMap<String, String> options = Maps.newTreeMap();

    public Builder addOption(String optionName, String value) {
      this.options.put(optionName, value);
      return this;
    }

    public Builder addAllOptions(Map<String, String> options) {
      this.options.putAll(options);
      return this;
    }

    public Builder addProperty(String property, String value) {
      this.properties.put(property, value);
      return this;
    }

    public Builder addAllProperties(Map<String, String> properties) {
      this.properties.putAll(properties);
      return this;
    }

    public VmSpec build() {
      return new VmSpec(this);
    }
  }
}

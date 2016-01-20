/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.NameDefiner;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author vlan
 */
public class PyResolveProcessor implements PsiScopeProcessor {
  @NotNull private final String myName;
  private final boolean myLocalResolve;
  @NotNull private final Map<PsiElement, PsiElement> myResults = Maps.newLinkedHashMap();
  @Nullable private ScopeOwner myOwner;

  public PyResolveProcessor(@NotNull String name) {
    this(name, false);
  }

  public PyResolveProcessor(@NotNull String name, boolean localResolve) {
    myName = name;
    myLocalResolve = localResolve;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    final PsiNamedElement namedElement = PyUtil.as(element, PsiNamedElement.class);
    if (namedElement != null && myName.equals(namedElement.getName())) {
      return tryAddResult(element, null);
    }
    final NameDefiner nameDefiner = PyUtil.as(element, NameDefiner.class);
    if (nameDefiner != null) {
      final PsiElement resolved = resolveInNameDefiner(nameDefiner);
      if (resolved != null) {
        return tryAddResult(resolved, nameDefiner);
      }
      final PyImportElement importElement = PyUtil.as(element, PyImportElement.class);
      if (importElement != null) {
        final String importName = importElement.getVisibleName();
        if (myName.equals(importName)) {
          return tryAddResult(null, importElement);
        }
      }
    }
    return myOwner == null || myOwner == ScopeUtil.getScopeOwner(element);
  }

  @Nullable
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    return null;
  }

  @Override
  public void handleEvent(@NotNull Event event, @Nullable Object associated) {
  }

  @NotNull
  public Map<PsiElement, PsiElement> getResults() {
    return myResults;
  }

  @NotNull
  public Collection<PsiElement> getElements() {
    return myResults.keySet();
  }

  @NotNull
  public Collection<PsiElement> getDefiners() {
    return myResults.values();
  }

  @Nullable
  public ScopeOwner getOwner() {
    return myOwner;
  }

  @Nullable
  private PsiElement resolveInNameDefiner(@NotNull NameDefiner definer) {
    if (myLocalResolve) {
      final PyImportElement importElement = PyUtil.as(definer, PyImportElement.class);
      if (importElement != null) {
        return importElement.getElementNamed(myName, false);
      }
      else if (definer instanceof PyStarImportElement) {
        return null;
      }
    }
    return definer.getElementNamed(myName);
  }

  private boolean tryAddResult(@Nullable PsiElement element, @Nullable PsiElement definer) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(definer != null ? definer : element);
    if (myOwner == null) {
      myOwner = owner;
    }
    final boolean sameScope = owner == myOwner;
    if (sameScope) {
      myResults.put(element, definer != null ? definer : null);
    }
    return sameScope;
  }
}

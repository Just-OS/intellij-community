// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement

internal class UastReferenceContributorChunk(private val supportedUElementTypes: List<Class<out UElement>>) : PsiReferenceProvider() {
  private val providers: MutableList<ProviderRegistration> = mutableListOf()

  fun register(pattern: (UElement, ProcessingContext) -> Boolean,
               provider: UastReferenceProvider) {
    providers.add(ProviderRegistration(pattern, provider))
  }

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val uElement = getOrCreateCachedElement(element, context, supportedUElementTypes) ?: return PsiReference.EMPTY_ARRAY

    val references = providers.asSequence()
      .filter { it.pattern.invoke(uElement, context) }
      .flatMap {
        val referencesByElement = it.provider.getReferencesByElement(uElement, context)

        if (referencesByElement.isNotEmpty()
            && (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isInternal)) {
          for (reference in referencesByElement) {
            if (reference.element !== element)
              throw AssertionError(
                """reference $reference was created for $element but targets ${reference.element}, provider ${it.provider}"""
              )
          }
        }

        referencesByElement.asSequence()
      }
      .toList()

    if (references.isEmpty()) return PsiReference.EMPTY_ARRAY
    return references.toTypedArray()
  }

  override fun acceptsTarget(target: PsiElement): Boolean {
    return providers.any { it.provider.acceptsTarget(target) }
  }
}

internal data class ChunkTag(val priority: Double,
                            val supportedUElementTypes: List<Class<out UElement>>)

private class ProviderRegistration(val pattern: (UElement, ProcessingContext) -> Boolean,
                                   val provider: UastReferenceProvider) {
  override fun toString(): String {
    return "provider(${provider})"
  }
}
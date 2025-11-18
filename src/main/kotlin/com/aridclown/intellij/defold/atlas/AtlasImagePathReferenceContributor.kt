package com.aridclown.intellij.defold.atlas

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainTextFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import kotlin.sequences.emptySequence

class AtlasImagePathReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val atlasFilePattern = psiFile(PsiPlainTextFile::class.java)
            .withName(StandardPatterns.string().endsWith(".atlas"))

        registrar.registerReferenceProvider(atlasFilePattern, AtlasImagePathReferenceProvider())
    }
}

private class AtlasImagePathReferenceProvider : PsiReferenceProvider() {

    private val imageDirective = Regex("""image\s*:\s*"([^"\n]+)"""")

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val file = element as? PsiPlainTextFile ?: return PsiReference.EMPTY_ARRAY
        if (file.text.isEmpty()) return PsiReference.EMPTY_ARRAY

        val virtual = file.virtualFile ?: file.originalFile.virtualFile
        val document = file.viewProvider.document
            ?: virtual?.let { FileDocumentManager.getInstance().getDocument(it) }

        val references = imageDirective.findAll(file.text)
            .flatMap { match ->
                val group = match.groups[1] ?: return@flatMap emptySequence<PsiReference>()
                ranges(group.value, group.range.first)
                    .map { range -> AtlasImagePathReference(file, range, document) }
            }
            .toList()

        return references.toTypedArray()
    }

    private fun ranges(path: String, startOffset: Int): Sequence<TextRange> {
        if (path.isEmpty()) return emptySequence()

        val prefixes = mutableListOf<TextRange>()
        for (index in path.indices) {
            val isSeparator = path[index] == '/'
            val hasNext = index < path.lastIndex
            if (isSeparator && index > 0 && hasNext) {
                prefixes += TextRange(startOffset, startOffset + index)
            }
        }

        prefixes += TextRange(startOffset, startOffset + path.length)

        return prefixes.asSequence()
    }
}

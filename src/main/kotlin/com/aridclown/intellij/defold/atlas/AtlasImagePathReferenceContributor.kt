package com.aridclown.intellij.defold.atlas

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.StandardPatterns.instanceOf
import com.intellij.psi.*
import com.intellij.psi.PsiReference.EMPTY_ARRAY
import com.intellij.util.ProcessingContext

class AtlasImagePathReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val atlasFilePattern = psiFile(PsiPlainTextFile::class.java)
            .withFileType(instanceOf(AtlasFileType::class.java))

        registrar.registerReferenceProvider(atlasFilePattern, AtlasImagePathReferenceProvider())
    }
}

private class AtlasImagePathReferenceProvider : PsiReferenceProvider() {

    private val imageDirective = Regex("""image\s*:\s*"([^"\n]+)"""")

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val file = element as? PsiPlainTextFile ?: return EMPTY_ARRAY
        if (file.text.isEmpty()) return EMPTY_ARRAY

        val virtual = file.virtualFile ?: file.originalFile.virtualFile
        val document = file.viewProvider.document
            ?: virtual?.let(FileDocumentManager.getInstance()::getDocument)

        val documentLength = document?.textLength ?: file.text.length

        return imageDirective.findAll(file.text)
            .flatMap { match -> createReferences(match, file, document, documentLength) }
            .toList()
            .toTypedArray()
    }

    private fun createReferences(
        match: MatchResult,
        file: PsiPlainTextFile,
        document: com.intellij.openapi.editor.Document?,
        documentLength: Int
    ): List<PsiReference> {
        val group = match.groups[1] ?: return emptyList()
        return ranges(group.value, group.range.first)
            .filter { range -> range.endOffset <= documentLength }
            .map { range -> AtlasImagePathReference(file, range, document) }
    }

    private fun ranges(path: String, startOffset: Int) = when {
        path.isEmpty() -> emptyList()
        else -> path.indices
            .filter { index -> path[index] == '/' && index > 0 && index < path.lastIndex }
            .map { index -> TextRange(startOffset, startOffset + index) }
            .plus(TextRange(startOffset, startOffset + path.length))
    }
}

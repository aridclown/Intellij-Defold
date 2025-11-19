package com.aridclown.intellij.defold.atlas

import com.intellij.openapi.editor.Document
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

internal class AtlasImagePathReference(
    element: PsiPlainTextFile,
    initialRange: TextRange,
    document: Document?
) : PsiReferenceBase<PsiPlainTextFile>(element, initialRange, false) {

    private val rangeMarker = document?.createRangeMarker(initialRange.startOffset, initialRange.endOffset)
    private val documentManager = PsiDocumentManager.getInstance(element.project)

    override fun getRangeInElement(): TextRange {
        val marker = rangeMarker
        if (marker != null && marker.isValid) {
            val elementStart = element.textRange.startOffset
            return TextRange(marker.startOffset - elementStart, marker.endOffset - elementStart)
        }
        return super.getRangeInElement()
    }

    override fun resolve(): PsiElement? {
        val path = currentPath()
        if (path.isBlank()) return null
        val psiManager = PsiManager.getInstance(element.project)

        return projectRootDirectories()
            .mapNotNull { context ->
                val virtual = findRelative(path, context) ?: return@mapNotNull null
                psiManager.findFile(virtual) ?: psiManager.findDirectory(virtual)
            }
            .firstOrNull()
    }

    override fun bindToElement(element: PsiElement): PsiElement {
        val item = element as? PsiFileSystemItem ?: return super.bindToElement(element)
        val file = item.virtualFile ?: return this.element

        val newPath = pathFromProjectRoot(file) ?: return this.element
        return rewritePath(newPath)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val current = currentPath()
        val separatorIndex = current.lastIndexOf('/')
        val updated = if (separatorIndex >= 0) {
            current.take(separatorIndex + 1) + newElementName
        } else {
            newElementName
        }
        return rewritePath(updated)
    }

    private fun currentPath(): String {
        val marker = rangeMarker
        val document = documentManager.getDocument(element)
        if (marker != null && marker.isValid && document != null) {
            return document.getText(TextRange(marker.startOffset, marker.endOffset))
        }
        val range = super.getRangeInElement()
        return element.text.substring(range.startOffset, range.endOffset)
    }

    private fun rewritePath(newPath: String): PsiElement {
        val normalized = normalizeSeparators(newPath)
        return ElementManipulators.handleContentChange(element, getRangeInElement(), normalized)
    }

    private fun findRelative(path: String, context: VirtualFile): VirtualFile? {
        val relativePath = path.removePrefix("/")
        return VfsUtilCore.findRelativeFile(relativePath, context)
    }

    private fun referencedVirtualFile(): VirtualFile? = element.originalFile.virtualFile ?: element.virtualFile

    private fun projectRootDirectories(): List<VirtualFile> {
        val project = element.project
        val roots = LinkedHashSet<VirtualFile>()

        referencedVirtualFile()?.let { file ->
            ProjectFileIndex.getInstance(project).getContentRootForFile(file)?.let(roots::add)
        }

        ProjectRootManager.getInstance(project).contentRoots.forEach(roots::add)

        val basePath = project.basePath
        if (basePath != null) {
            LocalFileSystem.getInstance().findFileByPath(basePath)?.let(roots::add)
        }

        return roots.toList()
    }

    private fun pathFromProjectRoot(file: VirtualFile): String? {
        projectRootDirectories().forEach { root ->
            val relative = VfsUtilCore.getRelativePath(file, root, '/')
            if (relative != null) {
                return "/$relative"
            }
        }
        return null
    }

    private fun normalizeSeparators(path: String): String = path.replace('\\', '/')
}

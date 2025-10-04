/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
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

package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.ui.DefoldIcons.defoldIcon
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator.naturalOrder
import javax.swing.Icon
import kotlin.io.path.notExists

class DefoldStdLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<DefoldStdLibrary> {
        val version = project.defoldVersion
        val annotationsDir = Path.of(System.getProperty("user.home"), ".defold", "annotations")

        val actualVersion = version ?: getHighestAvailableVersion(annotationsDir)

        if (actualVersion == null) return emptyList()

        val base = annotationsDir.resolve(actualVersion)
        val defoldApiDir = base.resolve("defold_api")
        val dir = LocalFileSystem.getInstance()
            .findFileByNioFile(defoldApiDir)
            ?: return emptyList()

        return listOf(DefoldStdLibrary(actualVersion, dir))
    }

    private fun getHighestAvailableVersion(annotationsDir: Path): String? {
        if (annotationsDir.notExists() || !Files.isDirectory(annotationsDir)) return null

        return Files.list(annotationsDir)
            .filter(Files::isDirectory)
            .map { it.fileName.toString() }
            .max(naturalOrder())
            .orElse(null)
    }

    class DefoldStdLibrary(
        private val version: String,
        private val root: VirtualFile
    ) : SyntheticLibrary(), ItemPresentation {
        private val roots = listOf(root)
        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean {
            return other is DefoldStdLibrary && other.root == root
        }

        override fun getSourceRoots() = roots

        override fun getLocationString() = "Defold std library"

        override fun getIcon(p0: Boolean): Icon = defoldIcon

        override fun getPresentableText() = "Defold $version"

    }
}
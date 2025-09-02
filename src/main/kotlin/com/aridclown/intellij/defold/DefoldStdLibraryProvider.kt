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

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.StubIndex
import java.nio.file.Path
import javax.swing.Icon

class DefoldStdLibraryProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<DefoldStdLibrary> {
        val service = DefoldProjectService.getInstance(project)
        val base = Path.of(System.getProperty("user.home"), ".defold", "annotations", service.getDefoldVersion())
        val dir = VfsUtil.findFileByIoFile(base.toFile(), true) ?: return emptyList()

        return listOf(DefoldStdLibrary(dir))
    }

    class DefoldStdLibrary(
        private val root: VirtualFile
    ) : SyntheticLibrary(), ItemPresentation {
        private val roots = listOf(root)
        override fun hashCode() = root.hashCode()

        override fun equals(other: Any?): Boolean {
            return other is DefoldStdLibrary && other.root == root
        }

        override fun getSourceRoots() = roots

        override fun getLocationString() = "Defold std library"

        override fun getIcon(p0: Boolean): Icon? = null

        override fun getPresentableText() = "Defold"

    }
}

fun reload() {
    WriteAction.run<RuntimeException> {
        val projects = ProjectManagerEx.getInstanceEx().openProjects
        for (project in projects) {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
                EmptyRunnable.getInstance(),
                RESCAN_DEPENDENCIES_IF_NEEDED
            )
        }

        StubIndex.getInstance()
            .forceRebuild(Throwable("Lua language level changed."))
    }
}
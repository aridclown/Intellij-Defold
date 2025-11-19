package com.aridclown.intellij.defold.atlas

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat

class AtlasImageReferenceContributorTest : BasePlatformTestCase() {

    fun `test image path resolves to project file`() {
        myFixture.addFileToProject("assets/images/hero.png", "")

        myFixture.configureByText(
            "main.atlas",
            """
                images {
                  image: "/assets/images/hero<caret>.png"
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion("main.atlas")
        val target = reference.resolve()

        assertThat(target).isNotNull
            .isInstanceOf(PsiFile::class.java)
    }

    fun `test moving referenced image rewrites atlas path`() {
        myFixture.addFileToProject("assets/images/hero.png", "")
        val atlasFile = myFixture.configureByText(
            "main.atlas",
            """
                images {
                  image: "/assets/images/hero.png"
                }
            """.trimIndent()
        )

        myFixture.tempDirFixture.findOrCreateDir("assets/moved")
        myFixture.moveFile("assets/images/hero.png", "assets/moved")
        FileDocumentManager.getInstance().saveAllDocuments()

        val atlasText = VfsUtilCore.loadText(atlasFile.virtualFile)

        assertThat(atlasText).contains("/assets/moved/hero.png")
    }

    fun `test moving folder updates all image paths`() {
        myFixture.addFileToProject("assets/sprites/bg.png", "")
        myFixture.addFileToProject("assets/sprites/hero.png", "")
        val atlasFile = myFixture.configureByText(
            "main.atlas",
            """
                images {
                  image: "/assets/sprites/bg.png"
                }
                images {
                  image: "/assets/sprites/hero.png"
                }
            """.trimIndent()
        )

        val movedDir = myFixture.tempDirFixture.findOrCreateDir("assets/moved")
        val spritesDir = myFixture.findFileInTempDir("assets/sprites")
        val spritesPsi = myFixture.psiManager.findDirectory(spritesDir)
        val movedPsi = myFixture.psiManager.findDirectory(movedDir)

        MoveFilesOrDirectoriesProcessor(
            project,
            arrayOf(spritesPsi),
            movedPsi!!,
            false,
            false,
            null,
            null
        ).run()
        FileDocumentManager.getInstance().saveAllDocuments()

        val atlasText = VfsUtilCore.loadText(atlasFile.virtualFile)

        assertThat(atlasText)
            .contains("/assets/moved/sprites/bg.png", "/assets/moved/sprites/hero.png")
    }

    fun `test renaming image file updates atlas path`() {
        val imgFile = myFixture.addFileToProject("assets/hero.png", "")
        val atlasFile = myFixture.configureByText(
            "main.atlas",
            """
                images {
                  image: "/assets/hero.png"
                }
            """.trimIndent()
        )

        myFixture.renameElement(imgFile, "villain.png")
        FileDocumentManager.getInstance().saveAllDocuments()

        val atlasText = VfsUtilCore.loadText(atlasFile.virtualFile)

        assertThat(atlasText).contains("/assets/villain.png")
    }

    fun `test resolves multiple image paths in same atlas`() {
        myFixture.addFileToProject("assets/bg.png", "")
        myFixture.addFileToProject("assets/sprites/hero.png", "")

        myFixture.configureByText(
            "main.atlas",
            """
                images {
                  image: "/assets/bg<caret>.png"
                }
                images {
                  image: "/assets/sprites/hero.png"
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertThat(reference.resolve()).isNotNull
    }

    fun `test folder segment reference resolves to directory`() {
        myFixture.addFileToProject("assets/sprites/hero.png", "")

        myFixture.configureByText(
            "main.atlas",
            """
                images {
                  image: "/assets/spr<caret>ites/hero.png"
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()
        val target = reference.resolve()

        assertThat(target).isInstanceOf(PsiDirectory::class.java)
    }

    fun `test non-existent path returns null`() {
        myFixture.configureByText(
            "main.atlas",
            """
                images {
                  image: "/assets/missing<caret>.png"
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPosition()
        assertThat(reference?.resolve()).isNull()
    }
}

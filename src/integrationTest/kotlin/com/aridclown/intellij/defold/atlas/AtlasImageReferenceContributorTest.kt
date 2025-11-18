package com.aridclown.intellij.defold.atlas

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat

class AtlasImageReferenceContributorTest : BasePlatformTestCase() {

    fun `image path resolves to project file`() {
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

        assertThat(target)
            .describedAs("expected atlas image reference to resolve to hero.png")
            .isNotNull
    }

    fun `moving referenced image rewrites atlas path`() {
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

        assertThat(atlasText)
            .contains("/assets/moved/hero.png")
    }
}

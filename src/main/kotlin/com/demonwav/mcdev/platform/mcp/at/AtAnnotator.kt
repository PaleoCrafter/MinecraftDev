/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.platform.mcp.at

import com.demonwav.mcdev.facet.MinecraftFacet
import com.demonwav.mcdev.platform.mcp.McpModuleType
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtEntry
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtFieldName
import com.demonwav.mcdev.platform.mcp.at.gen.psi.AtFunction
import com.demonwav.mcdev.platform.mcp.at.psi.AtElement
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import java.awt.Font

class AtAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AtEntry) {
            return
        }

        val member = element.function ?: element.fieldName ?: return

        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
        val facet = MinecraftFacet.getInstance(module) ?: return
        val mcpModule = facet.getModuleOfType(McpModuleType) ?: return
        val srgMap = mcpModule.srgManager?.srgMapNow ?: return

        val reference = AtMemberReference.get(element, member) ?: return

        // We can resolve this without checking the srg map
        if (reference.resolveMember(element.project) != null) {
            if (member is AtFunction) {
                underline(member.funcName, holder)
            } else {
                underline(member, holder)
            }
            return
        }

        // We need to check the srg map, or it can't be resolved (and no underline)
        if (member is AtFieldName) {
            srgMap.getMcpField(reference)?.resolveMember(element.project) ?: return
            underline(member, holder)
        } else if (member is AtFunction) {
            srgMap.getMcpMethod(reference)?.resolveMember(element.project) ?: return
            underline(member.funcName, holder)
        } else {
            return
        }
    }

    private fun underline(element: AtElement, holder: AnnotationHolder) {
        val annotation = Annotation(
            element.textRange.startOffset,
            element.textRange.endOffset,
            HighlightSeverity.INFORMATION,
            null,
            null
        )
        annotation.textAttributes = key

        (holder as AnnotationHolderImpl).add(annotation)
    }

    companion object {
        @Suppress("DEPRECATION")
        val key = TextAttributesKey.createTextAttributesKey("AT_UNDERLINE", TextAttributes(
            null,
            null,
            AtSyntaxHighlighter.ELEMENT_NAME.defaultAttributes.foregroundColor,
            EffectType.LINE_UNDERSCORE,
            Font.PLAIN
        ))
    }
}

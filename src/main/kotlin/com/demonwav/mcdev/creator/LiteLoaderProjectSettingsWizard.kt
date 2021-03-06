/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.creator

import com.demonwav.mcdev.exception.MinecraftSetupException
import com.demonwav.mcdev.platform.PlatformType
import com.demonwav.mcdev.platform.liteloader.LiteLoaderProjectConfiguration
import com.demonwav.mcdev.platform.liteloader.version.LiteLoaderVersion
import com.demonwav.mcdev.platform.mcp.version.McpVersion
import com.demonwav.mcdev.platform.mcp.version.McpVersionEntry
import com.demonwav.mcdev.util.invokeLater
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.awt.RelativePoint
import org.apache.commons.lang.WordUtils
import org.jetbrains.concurrency.runAsync
import java.awt.event.ActionListener
import java.util.regex.Pattern
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.SwingWorker
import javax.swing.event.DocumentEvent
import javax.swing.text.AbstractDocument

class LiteLoaderProjectSettingsWizard(private val creator: MinecraftProjectCreator) : MinecraftModuleWizardStep() {

    private lateinit var panel: JPanel
    private lateinit var mcpWarning: JLabel
    private lateinit var pluginNameField: JTextField
    private lateinit var pluginVersionField: JTextField
    private lateinit var mainClassField: JTextField
    private lateinit var minecraftVersionBox: JComboBox<String>
    private lateinit var mcpVersionBox: JComboBox<McpVersionEntry>
    private lateinit var loadingBar: JProgressBar

    private var settings: LiteLoaderProjectConfiguration? = null

    private var mcpVersion: McpVersion? = null
    private var liteloaderVersion: LiteLoaderVersion? = null

    private var mainClassModified = false

    private val mcpBoxActionListener = ActionListener {
        mcpWarning.isVisible = (mcpVersionBox.selectedItem as McpVersionEntry).isRed
    }

    private var apiWorker = LiteLoaderWorker(null)

    private val listener = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            // Make sure they don't try to add spaces or whatever
            if (javaClassPattern.matcher(mainClassField.text).find()) {
                invokeLater {
                    mainClassField.document.removeDocumentListener(this)
                    (e as AbstractDocument.DefaultDocumentEvent).undo()
                    mainClassField.document.addDocumentListener(this)
                }
                return
            }

            // We just need to make sure they aren't messing up the LiteMod text
            val words = mainClassField.text.split(".").dropLastWhile(String::isEmpty).toTypedArray()
            if (!words[words.size - 1].startsWith(LITEMOD)) {
                invokeLater {
                    mainClassField.document.removeDocumentListener(this)

                    mainClassModified = true
                    (e as AbstractDocument.DefaultDocumentEvent).undo()
                    mainClassField.document.addDocumentListener(this)
                }
            }
        }
    }

    init {
        mcpWarning.isVisible = false

        minecraftVersionBox.addActionListener {
            val version = minecraftVersionBox.selectedItem as String
            runAsync {
                getData(version)
            }.done { (mcpVersions) ->
                invokeLater {
                    mcpVersionBox.removeActionListener(mcpBoxActionListener)
                    mcpVersionBox.removeAllItems()
                    mcpVersions.forEach { mcpVersionBox.addItem(it) }
                    mcpVersionBox.addActionListener(mcpBoxActionListener)
                    mcpBoxActionListener.actionPerformed(null)
                }
            }
        }

        pluginNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (mainClassModified) {
                    return
                }

                val words = pluginNameField.text.split("\\s+".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
                val word = words.joinToString { WordUtils.capitalize(it) }

                val mainClassWords = mainClassField.text.split(".").toTypedArray()
                mainClassWords[mainClassWords.size - 1] = LITEMOD + word

                mainClassField.document.removeDocumentListener(listener)
                mainClassField.text = mainClassWords.joinToString(".")
                mainClassField.document.addDocumentListener(listener)
            }
        })

        mainClassField.document.addDocumentListener(listener)

        apiWorker.execute()
    }

    override fun getComponent(): JComponent {
        settings = creator.settings[PlatformType.LITELOADER] as? LiteLoaderProjectConfiguration
        if (settings == null) {
            return panel
        }

        pluginNameField.text = WordUtils.capitalizeFully(creator.artifactId)
        pluginVersionField.text = creator.version

        if (settings != null && !settings!!.isFirst) {
            pluginNameField.isEditable = false
            pluginVersionField.isEditable = false
        }

        mainClassField.document.removeDocumentListener(listener)
        mainClassField.text = "${this.creator.groupId}.${this.creator.artifactId}.$LITEMOD${WordUtils.capitalizeFully(creator.artifactId)}"
        mainClassField.document.addDocumentListener(listener)

        loadingBar.isIndeterminate = true

        return panel
    }

    override fun validate(): Boolean {
        try {
            if (pluginNameField.text.trim { it <= ' ' }.isEmpty()) {
                throw MinecraftSetupException("empty", pluginNameField)
            }

            if (pluginVersionField.text.trim { it <= ' ' }.isEmpty()) {
                throw MinecraftSetupException("empty", pluginVersionField)
            }

            if (mainClassField.text.trim { it <= ' ' }.isEmpty()) {
                throw MinecraftSetupException("empty", mainClassField)
            }
        } catch (e: MinecraftSetupException) {
            val message = e.error
            JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, MessageType.ERROR, null)
                .setFadeoutTime(4000)
                .createBalloon()
                .show(RelativePoint.getSouthWestOf(e.j), Balloon.Position.below)
            return false
        }

        return !loadingBar.isVisible
    }

    override fun isStepVisible(): Boolean {
        settings = creator.settings[PlatformType.LITELOADER] as? LiteLoaderProjectConfiguration
        return settings != null
    }

    override fun updateStep() {
        if ((liteloaderVersion == null || mcpVersion == null) && (apiWorker.isCancelled || apiWorker.isDone)) {
            apiWorker = LiteLoaderWorker(minecraftVersionBox.selectedItem as String)
            apiWorker.execute()
        }
    }

    override fun onStepLeaving() {
        settings!!.apply {
            pluginName = pluginNameField.text
            pluginVersion = pluginVersionField.text
            mainClass = mainClassField.text

            mcVersion = minecraftVersionBox.selectedItem as String
            mcpVersion = (mcpVersionBox.selectedItem as McpVersionEntry).text
        }
    }

    override fun updateDataModel() {}

    private fun getData(version: String): Data {
        return Data(mcpVersion!!.getMcpVersionList(version))
    }

    private inner class LiteLoaderWorker(val version: String?) : SwingWorker<Data, Any>() {
        override fun doInBackground(): Data {
            mcpVersion = McpVersion.downloadData()
            liteloaderVersion = LiteLoaderVersion.downloadData()
            return getData(version ?: liteloaderVersion!!.sortedMcVersions[0])
        }

        override fun done() {
            if (mcpVersion == null || liteloaderVersion == null) {
                return
            }

            minecraftVersionBox.removeAllItems()

            liteloaderVersion!!.sortedMcVersions.forEach { minecraftVersionBox.addItem(it) }
            // Always select most recent
            minecraftVersionBox.selectedIndex = 0

            mcpVersionBox.removeActionListener(mcpBoxActionListener)
            mcpVersionBox.removeAllItems()
            get().mcpVersions.forEach { mcpVersionBox.addItem(it) }
            mcpVersionBox.addActionListener(mcpBoxActionListener)
            mcpBoxActionListener.actionPerformed(null)

            loadingBar.isIndeterminate = false
            loadingBar.isVisible = false
        }
    }

    private data class Data(val mcpVersions: List<McpVersionEntry>)

    companion object {
        private val LITEMOD = "LiteMod"
        private val javaClassPattern = Pattern.compile("\\s+|-|\\$")
    }
}

/* SPDX-License-Identifier: GPL-3.0-only
 * Player fingerprints adapted from ReVanced Patches v6.1.0 (GPL-3.0).
 * 2026-09-21: independent webhook extension and player button.
 */
package net.permissionbrick.ha

import app.revanced.patcher.*
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.*
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.w3c.dom.Element

private const val BRIDGE = "Lnet/permissionbrick/ha/Playback;"

private val controlsPatch = resourcePatch {
    apply {
        document("res/layout/youtube_controls_bottom_ui_container.xml").use { doc ->
            val root = doc.documentElement
            check(root.tagName.endsWith("ConstraintLayout")) { "Unsupported YouTube player layout" }
            check(doc.getElementsByTagName("net.permissionbrick.ha.TvButton").length == 0) {
                "Home Assistant is already patched into this APK. Start from the original APK."
            }
            // Run before the regular player-controls patch opens this document.
            // Anchor to bottom_end_container, which ReVanced moves with its button chain.
            val children = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
            val anchor = children.firstOrNull {
                it.getAttribute("android:id").substringAfter('/') == "bottom_end_container"
            } ?: error("YouTube bottom player controls not found")
            val chapter = children.firstOrNull {
                it.getAttribute("android:id").substringAfter('/') == "time_bar_chapter_title_container"
            } ?: error("YouTube chapter title container not found")
            chapter.setAttribute("yt:layout_constraintRight_toLeftOf", "@id/ha_send_to_tv")
            val button = doc.createElement("net.permissionbrick.ha.TvButton")
            mapOf("id" to "@+id/ha_send_to_tv", "layout_width" to "48dp", "layout_height" to "48dp",
                "contentDescription" to "Send to TV. Long press for Home Assistant settings",
                "padding" to "12dp", "background" to "@android:color/transparent").forEach { (key, value) ->
                button.setAttribute("android:$key", value)
            }
            button.setAttribute("yt:layout_constraintRight_toLeftOf", "@id/bottom_end_container")
            button.setAttribute("yt:layout_constraintBottom_toTopOf", "@id/quick_actions_container")
            root.insertBefore(button, anchor)
        }
        document("AndroidManifest.xml").use { doc ->
            val app = doc.getElementsByTagName("application").item(0) as Element
            app.setAttribute("android:usesCleartextTraffic", "true")
            val config = app.getAttribute("android:networkSecurityConfig")
            if (config.startsWith("@xml/")) {
                document("res/xml/${config.substringAfter('/')}.xml").use { network ->
                    var base = network.getElementsByTagName("base-config").item(0) as? Element
                    if (base == null) {
                        base = network.createElement("base-config")
                        network.documentElement.appendChild(base)
                    }
                    base.setAttribute("cleartextTrafficPermitted", "true")
                }
            }
        }
    }
}

@Suppress("unused")
val homeAssistantPatch = bytecodePatch(
    name = "Add Home Assistant TV button",
    description = "Adds a player button that pauses locally and sends the video and timestamp to your Home Assistant webhook. Long press to configure.",
) {
    compatibleWith("com.google.android.youtube"("20.40.45"))
    dependsOn(controlsPatch)
    extendWith("extensions/extension.rve")
    apply {
        val parent = firstMethodComposite {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returnType("[L")
            parameterTypes("L")
            instructions(524288L())
        }
        val idHook = parent.immutableClassDef.firstMethodComposite {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returnType("V")
            parameterTypes("L")
            instructions(
                allOf(Opcode.INVOKE_INTERFACE(), method { returnType == "Ljava/lang/String;" }),
                after(Opcode.MOVE_RESULT_OBJECT()),
                afterAtMost(6, method { toString() == "Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;" }),
                after(Opcode.RETURN_VOID())
            )
        }
        val register = idHook.method.getInstruction<OneRegisterInstruction>(idHook[1]).registerA
        idHook.method.addInstruction(idHook[1] + 1, "invoke-static {v$register}, $BRIDGE->setVideoId(Ljava/lang/String;)V")
        val timeReference = firstMethodComposite("Media progress reported outside media playback: ") {
            opcodes(Opcode.INVOKE_DIRECT_RANGE, Opcode.IGET_OBJECT)
        }
        val timeMethod = navigate(timeReference.immutableMethod).to(timeReference[0]).stop()
        check(timeMethod.parameterTypes.firstOrNull() == "J") { "Unexpected YouTube progress callback" }
        timeMethod.addInstruction(0, "invoke-static {p1, p2}, $BRIDGE->setVideoTime(J)V")
    }
}

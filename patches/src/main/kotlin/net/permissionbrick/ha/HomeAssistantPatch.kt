/* SPDX-License-Identifier: GPL-3.0-only
 * Player fingerprints adapted from ReVanced Patches v6.1.0 (GPL-3.0).
 * 2026-09-21: independent webhook extension and device-picker entry.
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
        // Both device-picker entry points use a RecyclerView inside a scroll view.
        // Keep the native list and its listeners intact; append our row in the same scroll area.
        listOf("mdx_device_picker_main", "send_to_tv_device_picker").forEach { layout ->
            document("res/layout/$layout.xml").use { doc ->
                check(doc.getElementsByTagName("net.permissionbrick.ha.HomeAssistantEntry").length == 0) {
                    "Home Assistant is already patched into this APK. Start from the original APK."
                }
                val list = doc.getElementsByTagName("android.support.v7.widget.RecyclerView").item(0) as? Element
                    ?: error("YouTube device list not found: $layout")
                check(list.getAttribute("android:id") == "@id/device_picker_recycler_view")
                val scroll = list.parentNode as Element
                check(scroll.tagName.endsWith("NestedScrollView")) { "Unexpected device picker container" }
                val column = doc.createElement("LinearLayout")
                column.setAttribute("android:orientation", "vertical")
                column.setAttribute("android:layout_width", "match_parent")
                column.setAttribute("android:layout_height", "wrap_content")
                scroll.replaceChild(column, list)
                column.appendChild(list)
                document("res/layout/mdx_device_picker_link_with_tv_code.xml").use { template ->
                    val row = doc.createElement("net.permissionbrick.ha.HomeAssistantEntry")
                    val original = template.documentElement
                    for (i in 0 until original.attributes.length) {
                        val attr = original.attributes.item(i)
                        row.setAttribute(attr.nodeName, attr.nodeValue)
                    }
                    row.setAttribute("android:contentDescription", "Home Assistant. Long press for settings")
                    val icon = doc.importNode(original.getElementsByTagName("ImageView").item(0), true) as Element
                    icon.removeAttribute("android:id")
                    icon.setAttribute("android:src", "@drawable/yt_outline_tv_vd_theme_24")
                    icon.setAttribute("android:importantForAccessibility", "no")
                    row.appendChild(icon)
                    val text = doc.createElement("TextView")
                    mapOf("text" to "Home Assistant", "textAppearance" to "?ytTextAppearanceTitle2",
                        "textColor" to "?ytTextPrimary", "gravity" to "center_vertical",
                        "layout_width" to "match_parent", "layout_height" to "wrap_content",
                        "minHeight" to "@dimen/devicepicker_route_container_min_height",
                        "importantForAccessibility" to "no").forEach { (key, value) -> text.setAttribute("android:$key", value) }
                    row.appendChild(text)
                    column.appendChild(row)
                }
            }
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
    name = "Add Home Assistant to device picker",
    description = "Adds Home Assistant to Select a device. Sends the video and timestamp to your webhook; local pausing is best effort. Long press the entry to configure.",
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

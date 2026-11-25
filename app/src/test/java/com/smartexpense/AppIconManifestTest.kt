package com.smartexpense

import com.smartexpense.data.AppIcon
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Every choosable icon must have exactly one launcher alias, and only the default may start enabled. */
class AppIconManifestTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private fun elements(root: Element, tag: String) = root.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }
    private fun manifest(): Element = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(main, "AndroidManifest.xml")).documentElement
    private fun isLauncher(element: Element) = elements(element, "category").any { it.getAttributeNS(ns, "name") == "android.intent.category.LAUNCHER" }

    @Test fun everyIconHasOneLauncherAliasAndOnlyTheDefaultStartsEnabled() {
        val aliases = elements(manifest(), "activity-alias").associateBy { "com.smartexpense" + it.getAttributeNS(ns, "name") }
        assertEquals(AppIcon.entries.map { it.alias }.toSet(), aliases.keys)
        AppIcon.entries.forEach { icon ->
            val alias = aliases.getValue(icon.alias)
            assertEquals(".MainActivity", alias.getAttributeNS(ns, "targetActivity"))
            assertTrue("${icon.label} must be a launcher entry", isLauncher(alias))
            assertEquals("${icon.label} enabled state", (icon == AppIcon.Default).toString(), alias.getAttributeNS(ns, "enabled"))
            val mipmap = alias.getAttributeNS(ns, "icon").removePrefix("@mipmap/")
            assertTrue("${icon.label} mipmap $mipmap", File(main, "res/mipmap-anydpi/$mipmap.xml").isFile)
        }
    }

    @Test fun mainActivityIsNotALauncherEntryOfItsOwn() {
        val activity = elements(manifest(), "activity").single { it.getAttributeNS(ns, "name") == ".MainActivity" }
        assertTrue("A launcher filter on MainActivity would show a second icon", !isLauncher(activity))
    }

    @Test fun iconKeysAndLabelsAreUnique() {
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { it.key }.toSet().size)
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { it.label }.toSet().size)
    }
}

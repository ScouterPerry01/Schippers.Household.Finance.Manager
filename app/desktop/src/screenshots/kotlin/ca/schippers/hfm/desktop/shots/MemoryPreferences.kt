package ca.schippers.hfm.desktop.shots

import java.util.prefs.AbstractPreferences

/**
 * Settings kept in memory only, so taking the manual's pictures never touches the real app's or
 * the demo's settings on this computer.
 */
class MemoryPreferences(parent: MemoryPreferences? = null, name: String = "") : AbstractPreferences(parent, name) {
    private val values = HashMap<String, String>()
    private val children = HashMap<String, MemoryPreferences>()

    override fun putSpi(key: String, value: String) {
        values[key] = value
    }

    override fun getSpi(key: String): String? = values[key]

    override fun removeSpi(key: String) {
        values.remove(key)
    }

    override fun removeNodeSpi() = Unit

    override fun keysSpi(): Array<String> = values.keys.toTypedArray()

    override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()

    override fun childSpi(name: String): AbstractPreferences = children.getOrPut(name) { MemoryPreferences(this, name) }

    override fun syncSpi() = Unit

    override fun flushSpi() = Unit
}

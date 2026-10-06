package ca.schippers.hfm.importers

/**
 * Something an import could not take as it was, for the import summary: [key] names it in the
 * message files (importNote.*), with its [args]; [english] is the same in English, for logs and tests.
 */
data class ImportNote(val key: String, val args: List<String>, val english: String) {
    override fun toString(): String = english

    companion object {
        fun of(key: String, english: String, vararg args: Any?): ImportNote = ImportNote("importNote.$key", args.map { it.toString() }, english)
    }
}

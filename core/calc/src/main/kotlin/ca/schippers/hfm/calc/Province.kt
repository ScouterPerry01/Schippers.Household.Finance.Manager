package ca.schippers.hfm.calc

/**
 * Canada's provinces and territories (PROV-01), by their postal abbreviation. Rules that differ
 * between them (bank holidays, provincial grants, locked-in plans, tax forms) key on this.
 */
enum class Province {
    AB, BC, MB, NB, NL, NS, NT, NU, ON, PE, QC, SK, YT;

    val isQuebec: Boolean get() = this == QC

    companion object {
        fun of(code: String?): Province? = entries.firstOrNull { it.name == code?.trim()?.uppercase() }
    }
}

/**
 * The law a locked-in plan (LIRA, LIF) answers to: the province of the employment the money came
 * from, or the federal Pension Benefits Standards Act for federally regulated employers.
 */
sealed interface PensionJurisdiction {
    val code: String

    data class Provincial(val province: Province) : PensionJurisdiction {
        override val code: String get() = province.name
    }

    data object Federal : PensionJurisdiction {
        override val code: String = "FEDERAL"
    }

    companion object {
        fun of(code: String?): PensionJurisdiction? =
            if (code == Federal.code) Federal else Province.of(code)?.let(::Provincial)

        val all: List<PensionJurisdiction> = listOf<PensionJurisdiction>(Federal) + Province.entries.map(::Provincial)
    }
}

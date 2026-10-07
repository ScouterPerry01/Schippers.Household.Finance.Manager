@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package ca.schippers.hfm.desktop.shots

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasScrollToNodeAction
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.DesktopComposeUiTest
import ca.schippers.hfm.books.StatementStatus
import ca.schippers.hfm.books.TemplateLine
import ca.schippers.hfm.books.TxnTemplate
import ca.schippers.hfm.books.AlertSettings
import ca.schippers.hfm.importers.ImportedLine
import ca.schippers.hfm.importers.ImportedStatement
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ai.SecretStore
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ca.schippers.hfm.desktop.App
import ca.schippers.hfm.desktop.AppTheme
import ca.schippers.hfm.desktop.ManualContent
import ca.schippers.hfm.desktop.AppState
import ca.schippers.hfm.desktop.BooksModel
import ca.schippers.hfm.desktop.DemoHousehold
import ca.schippers.hfm.desktop.DesktopAi
import ca.schippers.hfm.desktop.ReportKind
import ca.schippers.hfm.desktop.Screen
import ca.schippers.hfm.desktop.Section
import ca.schippers.hfm.desktop.ThemeChoice
import ca.schippers.hfm.desktop.helpId
import ca.schippers.hfm.i18n.Language
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The pictures' size, in pixels at the normal text size: for the manual, a window a little smaller
 * than the app's default; the store pictures ask for their own (1440 x 900) with `hfm.shots.width` and `.height`.
 */
private val WIDTH = Integer.getInteger("hfm.shots.width", 1280)
private val HEIGHT = Integer.getInteger("hfm.shots.height", 860)

/** One picture: its file name (without .png) and how to bring the screen to the state shown. */
private class Shot(val name: String, val prepare: ShotScope.() -> Unit)

/** What a [Shot] can do: open a screen, change the app's state, click a tab or button by its label. */
private class ShotScope(val app: AppState, val model: BooksModel, val test: DesktopComposeUiTest) {
    fun t(key: String, vararg args: Any) = model.t(key, *args)

    fun section(s: Section) {
        model.section = s
    }

    /**
     * Clicks the tab showing [label], or else a button showing it; the last one found, since the menu on
     * the left comes first and its items are tabs too (the Bills screen has a Calendar tab, the menu a Calendar screen).
     */
    fun click(label: String) {
        settle(test)
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val nodes: SemanticsNodeInteractionCollection = listOf(
            hasText(label) and tab,
            hasText(label, substring = true) and tab,
            hasText(label) and hasClickAction(),
            hasText(label, substring = true) and hasClickAction(),
        ).map { test.onAllNodes(it) }.firstOrNull { it.fetchSemanticsNodes().isNotEmpty() }
            ?: error("nothing to click labelled \"$label\"")
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
    }

    /**
     * Scrolls until a text showing [label] is at the top of its list: the first list (lazy or not)
     * that can bring it into view, since a lazy list composes only the rows shown.
     */
    fun scrollTo(label: String) {
        settle(test)
        val lists = test.onAllNodes(hasScrollToNodeAction())
        // Scrolling a lazy list waits for new frames, which the paused clock would never give.
        test.mainClock.autoAdvance = true
        val found = (0 until lists.fetchSemanticsNodes().size).any { i ->
            runCatching { lists[i].performScrollToNode(hasText(label, substring = true)) }.isSuccess
        }
        test.mainClock.autoAdvance = false
        if (!found) error("nothing to scroll to showing \"$label\"")
    }

    /** Brings the button showing [label] into view in its scrolling dialog, then clicks it. */
    fun press(label: String) {
        settle(test)
        val node = test.onAllNodes((hasText(label) or hasContentDescription(label)) and hasClickAction())[0]
        // Outside a scrolling list there is nothing to scroll.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** Types [text] into the [index]th field labelled [label], replacing what it shows. */
    fun type(label: String, text: String, index: Int = 0) {
        settle(test)
        test.onAllNodes(hasSetTextAction() and hasText(label))[index].performTextReplacement(text)
    }

    /** Ticks the [index]th chip labelled [label]. */
    fun tick(label: String, index: Int = 0) {
        settle(test)
        test.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))[index].performClick()
    }

    /** Picks [option] in the [index]th drop-down labelled [label], by typing the start of it. */
    fun choose(label: String, option: String, index: Int = 0) {
        type(label, option.take(6), index)
        settle(test)
        val items = test.onAllNodes(hasText(option) and hasClickAction() and !hasSetTextAction())
        items[items.fetchSemanticsNodes().size - 1].performClick()
    }

    /** The account the register pictures show: the household's credit card. */
    fun creditCard(): String? = model.books.accounts.list().firstOrNull { it.account.name in CARD_NAMES }?.account?.id

    fun l(fr: String, en: String) = if (model.language == Language.FRENCH) fr else en

    /** CAL-11: the next date of a child's activity from today, for the calendar's Week and Day pictures. */
    fun activityDay(): kotlinx.datetime.LocalDate {
        val today = ca.schippers.hfm.desktop.today()
        return model.books.calendar.occurrences(today, today.plus(kotlinx.datetime.DatePeriod(days = 14)))
            .first { it.event.category == ca.schippers.hfm.books.EventCategory.ACTIVITY && it.event.cost != null }.date
    }

    /** MAN-05: the templates the Templates window shows, added once to the credit card's account group. */
    fun addTemplates(cardId: String) {
        val books = model.books
        if (books.templates.forAccount(cardId).isNotEmpty()) return
        val group = books.accounts.list().first { it.account.id == cardId }.account.groupId
        fun cat(key: String) = books.categories.list().first { it.systemKey == key }.id
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        books.templates.save(TxnTemplate("", group, l("Épicerie de la semaine", "Weekly groceries"), l("IGA", "Loblaws"), lines = listOf(TemplateLine(cat("food.groceries")))))
        books.templates.save(TxnTemplate("", group, l("Essence", "Gas"), "Petro-Canada", lines = listOf(TemplateLine(cat("transport.fuel")))))
        books.templates.save(
            TxnTemplate(
                "", group, "Costco", "Costco", accountId = cardId,
                lines = listOf(TemplateLine(cat("food.groceries"), cad("-120.00")), TemplateLine(cat("housing.furnishings"), cad("-40.00"))),
            ),
        )
        books.templates.save(TxnTemplate("", group, l("Nourriture de Rex", "Rex's food"), l("Mondou", "Pet Valu"), amount = cad("-74.99"), lines = listOf(TemplateLine(cat("pets.food")))))
    }

    /**
     * CAT-03: a short card statement downloaded this week, whose new lines take their categories
     * from the payees' habits and so wait in Categories to review. Imported once.
     */
    fun importCardStatement(cardId: String) {
        val books = model.books
        if (books.transactions.suggestedCategories(cardId).isNotEmpty()) return
        val today = ca.schippers.hfm.desktop.today()
        fun day(n: Int) = today.minus(kotlinx.datetime.DatePeriod(days = n))
        fun cad(s: String) = Money.parse(s, Currency.CAD)
        val lines = listOf(
            ImportedLine("S1", day(5), cad("-96.41"), l("IGA", "LOBLAWS"), null, null),
            ImportedLine("S2", day(4), cad("-58.30"), "PETRO-CANADA", null, null),
            ImportedLine("S3", day(3), cad("-42.75"), l("RESTAURANT CHEZ MIMI", "RIVERSIDE DINER"), null, null),
            ImportedLine("S4", day(2), cad("-52.49"), l("MONDOU", "PET VALU"), null, null),
        )
        books.statements.import(cardId, ImportedStatement("OFX", null, Currency.CAD, day(6), day(1), null, null, lines), "visa.ofx")
    }
}

private val CARD_NAMES = setOf("TD Visa", "Visa Desjardins")

/** Whether the picture shows the manual's window instead of the app's. */
private var manualShown by mutableStateOf(false)

/** Every picture in the manual, in the order taken. Each file name is the same in English and French. */
private val SHOTS: List<Shot> = buildList {
    // One picture per screen, named after the screen's chapter.
    for (s in Section.entries) {
        add(
            Shot(s.helpId) {
                if (s == Section.ACCOUNTS) model.selectedAccountId = creditCard()
                section(s)
            },
        )
    }
    // The main tabs, dialogs and views where a picture helps.
    add(Shot("accounts-list") { section(Section.ACCOUNTS) })
    add(Shot("accounts-add") { section(Section.ACCOUNTS); click(t("accounts.add")) })
    add(
        Shot("accounts-reconcile") {
            // The joint account's statement, downloaded and waiting to be checked.
            val open = model.books.accounts.list().firstNotNullOfOrNull { a ->
                model.books.statements.statements(a.account.id).firstOrNull { it.status == StatementStatus.OPEN }?.let { a.account.id to it.id }
            }
            model.selectedAccountId = open?.first
            model.reconcilingStatementId = open?.second
            section(Section.ACCOUNTS)
        },
    )
    add(Shot("documents-all") { section(Section.DOCUMENTS); click(t("documents.tab.ALL")) })
    add(Shot("bills-calendar") { section(Section.BILLS); click(t("bills.tab.CALENDAR")) })
    add(Shot("bills-forecast") { section(Section.BILLS); click(t("bills.tab.FORECAST")) })
    add(Shot("calendar-month") { model.calendarView = "AGENDA"; model.calendarDate = ca.schippers.hfm.desktop.today(); section(Section.CALENDAR); click(t("calendar.tab.MONTH")) })
    // CAL-07 to CAL-11: the other views, on the day of the child's next swimming lesson (an activity with its carpool).
    add(Shot("calendar-week") { model.calendarDate = activityDay(); section(Section.CALENDAR); click(t("calendar.tab.WEEK")) })
    add(Shot("calendar-day") { model.calendarDate = activityDay(); section(Section.CALENDAR); click(t("calendar.tab.DAY")) })
    add(Shot("calendar-year") { model.calendarDate = ca.schippers.hfm.desktop.today(); section(Section.CALENDAR); click(t("calendar.tab.YEAR")) })
    add(Shot("calendar-activity") { model.calendarDate = activityDay(); section(Section.CALENDAR); click(t("calendar.tab.DAY")); click(l("Cours de natation", "Swimming lessons")) })
    add(Shot("calendar-schedules") { model.calendarView = "AGENDA"; section(Section.CALENDAR); click(t("schedule.title")) })
    add(Shot("calendar-schedule-edit") { model.calendarView = "AGENDA"; section(Section.CALENDAR); click(t("schedule.title")); click(t("common.edit")) })
    // CSY-01 to CSY-05: items brought in from Alex's phone, the list of those calendars, and the .ics import.
    add(Shot("calendar-brought-in") { model.calendarView = "AGENDA"; section(Section.CALENDAR); scrollTo(l("Planification trimestrielle", "Quarterly planning")) })
    add(Shot("calendar-phone-calendars") { section(Section.CALENDAR); click(t("calendar.broughtIn.manage")) })
    add(Shot("calendar-ics") { section(Section.CALENDAR); click(t("calendar.ics.import")) })
    add(Shot("reports-spending") { model.reportState.kind = ReportKind.SPENDING_BY_CATEGORY; section(Section.REPORTS) })
    add(Shot("reports-net-worth") { model.reportState.kind = ReportKind.NET_WORTH; section(Section.REPORTS) })
    add(Shot("reports-portfolio") { model.reportState.kind = ReportKind.PORTFOLIO; section(Section.REPORTS) })
    add(Shot("taxes-year-end") { section(Section.TAXES); click(t("taxes.tab.YEAR_END")) })
    add(Shot("taxes-estimate") { section(Section.TAXES); click(t("taxes.tab.ESTIMATE")) })
    add(Shot("health-appointments") { section(Section.HEALTH); click(t("health.tab.APPOINTMENTS")) })
    add(Shot("medical-coverage") { section(Section.MEDICAL); click(t("medical.tab.COVERAGE")) })
    add(Shot("vehicles-costs") { section(Section.VEHICLES); click(t("vehicles.tab.COSTS")) })
    // TRP-05, TRP-08, TRP-10: the plug-in hybrid's fuel and charging, and its forecast.
    add(Shot("vehicles-fuel") { section(Section.VEHICLES); click("Civic"); click("RAV4"); click(t("vehicles.tab.FUEL")) })
    add(Shot("vehicles-forecast") { section(Section.VEHICLES); click("Civic"); click("RAV4"); click(t("vehicles.tab.FORECAST")) })
    // TRP-02, TRP-09: the saved places and a vehicle's logbook.
    add(Shot("trips-places") { section(Section.TRIPS); click(t("places.title")) })
    add(Shot("trips-logbook") { section(Section.TRIPS); click(t("trips.logbook")) })
    add(Shot("assets-insurance") { section(Section.ASSETS); click(t("assets.tab.INSURANCE")) })
    // SEA-02, SEA-05: the seasonal checklist, and an energy upgrade's costs with its rebates.
    add(Shot("assets-seasonal") { section(Section.ASSETS); click(t("assets.tab.SEASONAL")) })
    add(
        Shot("assets-rebates") {
            section(Section.ASSETS)
            click(t("assets.tab.PROJECTS"))
            click(l("Isolation de l’entretoit", "Attic insulation and air sealing"))
        },
    )
    add(Shot("users-access") { section(Section.USERS); click(t("users.tab.ACCESS")) })
    // The basics: search, help, the menu at the top, dark colours.
    add(
        Shot("search") {
            val q = if (model.language == Language.FRENCH) "IGA" else "Costco"
            model.search = q to model.books.search.search(q, model.language.locale)
            section(Section.DASHBOARD)
        },
    )
    add(Shot("help") { section(Section.BILLS); app.openHelp() })
    add(Shot("menu-top") { app.setMenuOnTop(model.books.userId, true); section(Section.BUDGETS) })
    add(Shot("display-dark") { app.chooseTheme(ThemeChoice.DARK); section(Section.DASHBOARD) })
    // Dialogs added before 1.0. These change the sample household (templates, a card statement,
    // alerts), so they come after the pictures above, which show it as it starts.
    add(
        Shot("accounts-templates") {
            val card = creditCard()!!
            addTemplates(card)
            model.selectedAccountId = card
            section(Section.ACCOUNTS)
            click(t("templates.button"))
        },
    )
    add(
        Shot("accounts-categories-review") {
            val card = creditCard()!!
            importCardStatement(card)
            model.selectedAccountId = card
            section(Section.ACCOUNTS)
            click(t("suggested.button", model.books.transactions.suggestedCategories(card).size))
        },
    )
    add(
        Shot("accounts-alerts") {
            val card = creditCard()!!
            val alerts = model.books.accountAlerts
            if (!alerts.settings(card).anyOn) {
                alerts.save(AlertSettings(card, nearLimitPercent = 90, overLimit = true, largeMultiple = java.math.BigDecimal("3"), newPayeeAbove = Money.parse("250.00", Currency.CAD)))
            }
            model.selectedAccountId = card
            section(Section.ACCOUNTS)
            click(t("alert.button"))
        },
    )
    add(
        Shot("accounts-match-several") {
            // The joint account's statement, as in accounts-reconcile.
            val card = creditCard()
            val open = model.books.accounts.list().filter { it.account.id != card }.firstNotNullOfOrNull { a ->
                model.books.statements.statements(a.account.id).firstOrNull { it.status == StatementStatus.OPEN }?.let { a.account.id to it.id }
            }
            model.selectedAccountId = open?.first
            model.reconcilingStatementId = open?.second
            section(Section.ACCOUNTS)
            click(t("reconcile.matchSeveral"))
        },
    )
    add(Shot("reports-cash-flow") { model.reportState.kind = ReportKind.CASH_FLOW; section(Section.REPORTS) })
    add(Shot("taxes-estimate-carry-forward") { section(Section.TAXES); click(t("taxes.tab.ESTIMATE")); scrollTo(t("taxEstimateGroup.CARRY_FORWARD")) })
    // UTL-02, HRS-01, CHO-01: the fuel tanks, hours worked and chores tabs.
    add(Shot("utilities-tanks") { section(Section.UTILITIES); click(t("utilities.tab.TANKS")) })
    add(Shot("side-hours") { section(Section.SIDE); click(t("side.tab.HOURS")) })
    add(Shot("family-chores") { section(Section.FAMILY); click(t("family.tab.CHORES")) })
    // DOC-02: the Canadian Tire receipt itemized by hand, its taxes as read on this computer.
    add(
        Shot("documents-itemize") {
            model.focusDocumentId = model.books.documents.inbox().first { it.fileName == "scan-0031.jpg" }.id
            section(Section.DOCUMENTS)
            press(t("documents.itemize"))
            type(t("itemize.item"), l("Lave-glace -40", "Washer fluid -40"), 0)
            type(t("register.amount"), l("5,99", "5.99"), 0)
            type(t("itemize.item"), l("Ampoule H11", "H11 headlight bulb"), 1)
            type(t("register.amount"), l("24,99", "24.99"), 1)
            choose(t("register.category"), l("Entretien du véhicule", "Vehicle maintenance"), 0)
            for (code in if (model.language == Language.FRENCH) listOf("GST", "QST") else listOf("HST")) {
                tick(t("taxName.$code"), 0)
                tick(t("taxName.$code"), 1)
            }
        },
    )
    // Before a household is open: last, since leaving the household's screens stops its phone listener.
    // The manual's own window, on the Bills chapter with its first picture.
    add(Shot("manual-window") { section(Section.BILLS); app.openManual("bills"); manualShown = true })
    add(Shot("welcome-screen") { app.screen = Screen.Welcome })
    add(Shot("create-household") { app.screen = Screen.Create })
}

/** The pictures with no dark twin: the one of the dark colours, which looks the same either way. */
private val LIGHT_ONLY = setOf("display-dark")

/** A store picture (DIST-07): its file name in English and in French, and the screen it shows. */
private class StoreShot(val en: String, val fr: String, val prepare: ShotScope.() -> Unit)

/**
 * The Microsoft Store pictures, in the order the listing shows them: `./gradlew :app:desktop:storeScreenshots -Plang=en`
 * (or fr) writes them at 1440 x 900 in full colour into docs/store/screenshots/desktop-<lang>.
 */
private val STORE_SHOTS: List<StoreShot> = listOf(
    StoreShot("1-dashboard", "1-tableau-de-bord") { section(Section.DASHBOARD) },
    StoreShot("2-accounts", "2-comptes") { model.selectedAccountId = creditCard(); section(Section.ACCOUNTS) },
    StoreShot("3-reports", "3-rapports") { model.reportState.kind = ReportKind.SPENDING_BY_CATEGORY; section(Section.REPORTS) },
    StoreShot("4-taxes", "4-impots") { section(Section.TAXES); click(t("taxes.tab.ESTIMATE")) },
    StoreShot("5-investments", "5-placements") { section(Section.INVESTMENTS) },
    StoreShot("6-contacts", "6-contacts") {
        // The family doctor, with the people they serve and their links.
        model.focusContactId = model.books.contacts.list().firstOrNull { it.name.startsWith(l("Dre Gagnon", "Dr. Patel")) }?.id
        section(Section.CONTACTS)
    },
    StoreShot("7-medical", "7-reclamations-medicales") { section(Section.MEDICAL) },
    StoreShot("8-budgets", "8-budgets") { section(Section.BUDGETS) },
)

/**
 * The rann.ca home page's pictures, named as `website/rann-roost-en.md` and `-fr.md` show them:
 * `storeScreenshots -Plang=en -Pweb` writes them at the store's size into build/web-images, to be
 * scaled to 1080 x 675 JPEG for `website/images`.
 */
private val WEB_SHOTS: List<StoreShot> = listOf(
    StoreShot("desktop-en-dashboard", "desktop-fr-tableau-de-bord") { section(Section.DASHBOARD) },
    StoreShot("desktop-en-accounts", "desktop-fr-comptes") { model.selectedAccountId = creditCard(); section(Section.ACCOUNTS) },
    StoreShot("desktop-en-reports", "desktop-fr-rapports") { model.reportState.kind = ReportKind.INCOME_EXPENSE; section(Section.REPORTS) },
)

/** Lets the screen read its data and draw: a few frames, with time for background reads. */
private fun settle(test: DesktopComposeUiTest) {
    repeat(4) {
        test.waitForIdle()
        test.mainClock.advanceTimeBy(250)
        Thread.sleep(120)
    }
    test.waitForIdle()
}

/**
 * Draws the manual's pictures of the sample household offscreen: nothing is shown on the screen
 * and nothing else on the computer can appear in them. `./gradlew :app:desktop:manualScreenshots -Plang=en`
 * (or fr) writes them into the manual's images folder for that language; `-Pshot=name` takes only that one.
 */
fun main() {
    // The demo's ways: keys in memory only, no update check, settings kept apart (here, in memory).
    System.setProperty("hfm.demo", "true")
    // Nothing from this computer in the pictures: a made-up network address, and the household in a
    // folder whose path names no user (on Windows, the Public Documents folder).
    System.setProperty("hfm.demo.address", "192.168.1.20")
    val home = householdFolder()
    System.setProperty("hfm.demo.dir", home.absolutePath)
    keysAsInTheRealApp()
    val language = Language.entries.first { it.tag == (System.getProperty("hfm.shots.lang") ?: "en") }
    val out = File(System.getProperty("hfm.shots.out") ?: "build/manual-images/${language.tag}").apply { mkdirs() }
    val only = System.getProperty("hfm.shots.only")?.split(',')?.map { it.trim() }?.toSet()
    // The store's pictures instead of the manual's, kept in full colour: they are not shipped in the app.
    val set = System.getProperty("hfm.shots.set")
    val store = set == "store" || set == "web"
    val shots = if (store) {
        (if (set == "web") WEB_SHOTS else STORE_SHOTS).map { Shot(if (language == Language.FRENCH) it.fr else it.en, it.prepare) }
    } else {
        SHOTS
    }
    // -Ptheme=dark: the same pictures in the app's dark colours, for the manual in dark mode. The
    // picture of the dark colours themselves is dark already and serves both.
    val dark = System.getProperty("hfm.shots.theme") == "dark"
    val app = AppState(prefs = MemoryPreferences())
    app.switchLanguage(language, remember = false)
    val model = BooksModel(DemoHousehold.create(app.store, language), app)
    app.screen = Screen.Main(model)
    var taken = 0
    runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
        mainClock.autoAdvance = false
        setContent {
            if (manualShown) AppTheme(app) { Surface(Modifier.fillMaxSize()) { ManualContent(app) } } else App(app)
        }
        val scope = ShotScope(app, model, this)
        for (shot in shots) {
            if (only != null && shot.name !in only) continue
            if (dark && shot.name in LIGHT_ONLY) continue
            // A fresh start for each picture: light (or dark) colours, menu on the left, nothing open,
            // and another screen first so the screen's tabs start from their first one.
            app.chooseTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT)
            app.setMenuOnTop(model.books.userId, false)
            app.helpTopic = null
            model.search = null
            model.selectedAccountId = null
            model.reconcilingStatementId = null
            model.reportState.kind = ReportKind.INCOME_EXPENSE
            if (app.screen !is Screen.Main) app.screen = Screen.Main(model)
            manualShown = false
            app.manualPage = null
            model.section = if (model.section == Section.CATEGORIES) Section.PAYEES else Section.CATEGORIES
            settle(this)
            shot.prepare(scope)
            settle(this)
            val image = onAllNodes(isRoot())[0].captureToImage().toAwtImage()
            if (store) writeFullColour(image, File(out, shot.name + ".png")) else write(image, File(out, shot.name + ".png"))
            taken++
            println("${shot.name}.png")
        }
    }
    model.session.close()
    clean(home)
    println("$taken pictures in $out")
    System.exit(0)
}

/**
 * Saves [image] as a PNG of 256 colours, about half the size of a full-colour one: the pictures ship
 * inside the app. A screen has few colours besides the shades at the edges of letters, which 256
 * keep well enough to read.
 */
private fun write(image: BufferedImage, file: File) {
    val writer = ImageIO.getImageWritersByFormatName("png").next()
    val param = writer.defaultWriteParam.apply {
        // Quality 0 is the strongest compression; PNG loses nothing either way.
        compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
        compressionQuality = 0f
    }
    file.delete()
    ImageIO.createImageOutputStream(file).use { stream ->
        writer.output = stream
        writer.write(null, javax.imageio.IIOImage(Palette.reduce(image), null, null), param)
    }
    writer.dispose()
}

/** Saves [image] as a full-colour PNG, without transparency, as the stores ask. */
private fun writeFullColour(image: BufferedImage, file: File) {
    val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    rgb.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
    file.delete()
    ImageIO.write(rgb, "png", file)
}

/** Median cut: the colours used are split into 256 boxes, each drawn in the average colour of its box. */
internal object Palette {
    private class Box(val colours: List<Int>, val counts: Map<Int, Int>) {
        val weight = colours.sumOf { counts.getValue(it).toLong() }

        fun channel(c: Int, shift: Int) = (c shr shift) and 0xFF

        /** The channel (as its bit shift) with the widest range, and that range. */
        fun widest(): Pair<Int, Int> = listOf(16, 8, 0).map { s ->
            s to (colours.maxOf { channel(it, s) } - colours.minOf { channel(it, s) })
        }.maxBy { it.second }

        fun split(): Pair<Box, Box> {
            val (shift, _) = widest()
            val sorted = colours.sortedBy { channel(it, shift) }
            var seen = 0L
            var cut = 1
            for ((i, c) in sorted.withIndex()) {
                seen += counts.getValue(c)
                if (seen * 2 >= weight) { cut = (i + 1).coerceIn(1, sorted.size - 1); break }
            }
            return Box(sorted.subList(0, cut), counts) to Box(sorted.subList(cut, sorted.size), counts)
        }

        fun average(): Int {
            var r = 0L; var g = 0L; var b = 0L
            for (c in colours) {
                val n = counts.getValue(c)
                r += channel(c, 16).toLong() * n; g += channel(c, 8).toLong() * n; b += channel(c, 0).toLong() * n
            }
            return ((r / weight).toInt() shl 16) or ((g / weight).toInt() shl 8) or (b / weight).toInt()
        }
    }

    fun reduce(image: BufferedImage): BufferedImage {
        val w = image.width
        val h = image.height
        val pixels = image.getRGB(0, 0, w, h, null, 0, w).map { it and 0xFFFFFF }
        val counts = HashMap<Int, Int>()
        for (p in pixels) counts.merge(p, 1, Int::plus)
        val boxes = mutableListOf(Box(counts.keys.toList(), counts))
        while (boxes.size < 256) {
            val box = boxes.filter { it.colours.size > 1 }.maxByOrNull { it.widest().second.toLong() * it.weight } ?: break
            boxes.remove(box)
            val (a, b) = box.split()
            boxes += a
            boxes += b
        }
        val palette = boxes.map { it.average() }
        val index = HashMap<Int, Int>()
        boxes.forEachIndexed { i, box -> for (c in box.colours) index[c] = i }
        val model = java.awt.image.IndexColorModel(
            8, palette.size,
            ByteArray(palette.size) { (palette[it] shr 16).toByte() },
            ByteArray(palette.size) { (palette[it] shr 8).toByte() },
            ByteArray(palette.size) { palette[it].toByte() },
        )
        val out = BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, model)
        val raster = out.raster
        for (y in 0 until h) for (x in 0 until w) raster.setSample(x, y, 0, index.getValue(pixels[y * w + x]))
        return out
    }
}

/** Where the sample household goes while the pictures are taken; emptied before and after. */
private fun householdFolder(): File {
    val windows = System.getProperty("os.name").lowercase().startsWith("windows")
    val public = System.getenv("PUBLIC")?.let { File(it, "Documents") }?.takeIf { windows && it.isDirectory }
    val dir = File(public ?: File(System.getProperty("java.io.tmpdir")), "RANN's Roost")
    clean(dir)
    dir.mkdirs()
    return dir
}

/** Deletes the sample household and its backups from [dir], and [dir] itself if nothing else is in it. */
private fun clean(dir: File) {
    File(dir, "Demo.hfm").deleteRecursively()
    File(dir, "Demo - backups").deleteRecursively()
    if (dir.list()?.isEmpty() == true) dir.delete()
}

/**
 * The AI screen as on Windows: the demo keeps keys in memory and says so under the key, which the
 * real app does not. A store kept in memory that calls itself the Windows one takes its place.
 */
private fun keysAsInTheRealApp() {
    val store = object : SecretStore {
        private val keys = HashMap<String, String>()
        override val kind = SecretStore.Kind.WINDOWS
        override fun get(name: String) = keys[name]
        override fun put(name: String, secret: String) { keys[name] = secret }
        override fun delete(name: String) { keys.remove(name) }
    }
    runCatching {
        val delegate = DesktopAi::class.java.getDeclaredField("secrets\$delegate").apply { isAccessible = true }.get(null)
        delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(delegate, store)
    }.onFailure { println("The AI screen keeps the demo's note: ${it.message}") }
}

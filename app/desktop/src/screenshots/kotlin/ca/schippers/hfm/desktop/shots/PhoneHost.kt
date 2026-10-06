package ca.schippers.hfm.desktop.shots

import ca.schippers.hfm.desktop.AppState
import ca.schippers.hfm.desktop.BooksModel
import ca.schippers.hfm.desktop.DemoHousehold
import ca.schippers.hfm.desktop.SyncServer
import ca.schippers.hfm.i18n.Language
import java.io.File

/**
 * The computer side for the manual's phone pictures, with no window: the sample household listening
 * for phones, as the Phones screen does. It writes the pairing text (the one "Copy as text" gives)
 * to the file named by hfm.phone.invitation, for `adb shell am start -d`, and stops when the file
 * hfm.phone.stop appears (or after an hour). The computer's name on the phone is a made-up one.
 * `./gradlew :app:desktop:manualPhoneHost -Plang=en|fr`; see tools/dev/README.md.
 */
fun main() {
    System.setProperty("hfm.demo", "true")
    val language = Language.entries.first { it.tag == (System.getProperty("hfm.shots.lang") ?: "en") }
    val invitationFile = File(System.getProperty("hfm.phone.invitation") ?: "build/phone-invitation.txt")
    val stop = File(System.getProperty("hfm.phone.stop") ?: "build/phone-stop")
    stop.delete()
    val app = AppState(prefs = MemoryPreferences())
    app.switchLanguage(language, remember = false)
    val model = BooksModel(DemoHousehold.create(app.store, language), app)
    model.books.language = language
    // CAL-03 check on the emulator: an event starting in N minutes, reminded 2 minutes before (-PeventIn=N).
    System.getProperty("hfm.phone.eventIn")?.toLongOrNull()?.let { minutes ->
        val start = java.time.LocalDateTime.now().plusMinutes(minutes)
        model.books.calendar.create(
            ca.schippers.hfm.books.EventDraft(
                model.books.groups().first { !it.isPrivate }.id, if (language == Language.FRENCH) "Rencontre de parents" else "Parent-teacher meeting",
                ca.schippers.hfm.books.EventCategory.PERSONAL, kotlinx.datetime.LocalDate(start.year, start.monthValue, start.dayOfMonth),
                kotlinx.datetime.LocalTime(start.hour, start.minute), 30, if (language == Language.FRENCH) "École Sainte-Anne" else "Riverside School", reminderMinutes = listOf(2),
            ),
        )
        // A medical one too: its notification names no appointment and no place.
        model.books.calendar.create(
            ca.schippers.hfm.books.EventDraft(
                model.books.groups().first { !it.isPrivate }.id, if (language == Language.FRENCH) "Physiothérapie" else "Physiotherapy",
                ca.schippers.hfm.books.EventCategory.MEDICAL, kotlinx.datetime.LocalDate(start.year, start.monthValue, start.dayOfMonth),
                kotlinx.datetime.LocalTime(start.hour, start.minute), 45, if (language == Language.FRENCH) "Clinique du Parc" else "Park Clinic", reminderMinutes = listOf(2),
            ),
        )
    }
    model.syncServer.start()
    val address = checkNotNull(SyncServer.localAddress()) { "no network address for the phone to reach" }
    val name = if (language == Language.FRENCH) "Ordinateur familial" else "Family computer"
    val invitation = model.books.sync.invitation(name, address, model.syncServer.port, System.currentTimeMillis())
    invitationFile.parentFile?.mkdirs()
    invitationFile.writeText(invitation.toQrText())
    println("Listening on $address:${model.syncServer.port}; pairing text in $invitationFile; create $stop to stop.")
    // An hour by default; -Pminutes=N keeps the sample household listening longer, to try the phone at leisure.
    val end = System.currentTimeMillis() + (System.getProperty("hfm.phone.minutes")?.toLongOrNull() ?: 60L) * 60_000L
    while (!stop.exists() && System.currentTimeMillis() < end) Thread.sleep(500)
    model.syncServer.close()
    model.session.close()
    stop.delete()
    invitationFile.delete()
    println("Stopped.")
    System.exit(0)
}

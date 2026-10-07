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
    report(model)
    model.syncServer.close()
    model.session.close()
    stop.delete()
    invitationFile.delete()
    println("Stopped.")
    System.exit(0)
}

/**
 * What the phone sent, printed when the household stops, to check a run on the emulator without a
 * window: the trips from the phone with their legs, breaks, addresses and documents, the places saved
 * there, the fill-ups, and the documents waiting in the inbox.
 */
private fun report(model: BooksModel) {
    val books = model.books
    val year = java.time.LocalDate.now().year
    for (t in books.trips.list(year).filter { it.deviceId != null }) {
        println("Trip ${t.date} ${t.startAt?.time}-${t.endAt?.time} ${t.origin} -> ${t.destination}, ${t.km} km, ${t.purpose}, driving ${t.drivingMinutes} min, breaks ${t.breakMinutes} min")
        println("  from ${t.startAddress} (${t.startLatitude}, ${t.startLongitude}) to ${t.endAddress} (${t.endLatitude}, ${t.endLongitude})")
        for (leg in books.trips.legs(t)) println("  leg ${leg.from} -> ${leg.to}: ${leg.km} km, ${leg.purpose}, odometer ${leg.startOdometer}-${leg.endOdometer}")
        for (s in t.stops) println("  ${s.kind} ${s.at.time}-${s.endAt?.time} ${s.place} ${s.address} (${s.latitude}, ${s.longitude}) ${s.purpose}")
        val stops = books.trips.attachmentStops(t)
        for (d in books.trips.attachments(t)) println("  document ${d.fileName} ${d.mimeType} ${d.status} stop=${stops[d.id]} note=${d.notes} voice=${books.documents.voiceNotes(d.id).size}")
    }
    for (p in books.places.list().filter { it.deviceId != null }) println("Place ${p.name} ${p.category} ${p.address} (${p.latitude}, ${p.longitude})")
    for (v in books.vehicles.list()) for (f in books.vehicles.fuel(v.id).filter { it.deviceId != null }) println("Fuel ${v.name} ${f.date} ${f.quantity} ${f.station} place=${f.placeId}")
    for (d in books.documents.inbox()) println("Inbox ${d.fileName} ${d.mimeType} links=${d.links}")
}

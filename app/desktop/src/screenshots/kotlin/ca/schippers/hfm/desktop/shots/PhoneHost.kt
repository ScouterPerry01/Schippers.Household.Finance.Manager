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
    model.syncServer.start()
    val address = checkNotNull(SyncServer.localAddress()) { "no network address for the phone to reach" }
    val name = if (language == Language.FRENCH) "Ordinateur familial" else "Family computer"
    val invitation = model.books.sync.invitation(name, address, model.syncServer.port, System.currentTimeMillis())
    invitationFile.parentFile?.mkdirs()
    invitationFile.writeText(invitation.toQrText())
    println("Listening on $address:${model.syncServer.port}; pairing text in $invitationFile; create $stop to stop.")
    val end = System.currentTimeMillis() + 60 * 60_000L
    while (!stop.exists() && System.currentTimeMillis() < end) Thread.sleep(500)
    model.syncServer.close()
    model.session.close()
    stop.delete()
    invitationFile.delete()
    println("Stopped.")
    System.exit(0)
}

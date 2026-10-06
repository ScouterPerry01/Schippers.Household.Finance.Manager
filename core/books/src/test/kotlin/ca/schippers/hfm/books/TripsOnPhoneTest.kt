package ca.schippers.hfm.books

import ca.schippers.hfm.data.HouseholdStore
import ca.schippers.hfm.data.jdbc.SqlCipherJdbcDriverFactory
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.OcrResult
import ca.schippers.hfm.security.KdfParams
import ca.schippers.hfm.security.KeyPair
import ca.schippers.hfm.security.PairKey
import ca.schippers.hfm.sync.Direction
import ca.schippers.hfm.sync.PairRequest
import ca.schippers.hfm.sync.PhoneFuel
import ca.schippers.hfm.sync.PhonePlace
import ca.schippers.hfm.sync.PhoneTrip
import ca.schippers.hfm.sync.SyncCrypto
import ca.schippers.hfm.sync.SyncRequest
import ca.schippers.hfm.sync.SyncResponse
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRP-01 to TRP-10: trips, places and fill-ups from the phone, consumption by kind of driving, the forecast and the logbook. */
class TripsOnPhoneTest {

    @TempDir
    lateinit var temp: Path

    private val store = HouseholdStore(SqlCipherJdbcDriverFactory(), KdfParams.TESTING)
    private val today = LocalDate(2026, 10, 5)
    private val now = 1_790_000_000_000L
    private val noConversion = object : CaptureConverter {
        override fun pagesToPdf(pages: List<ByteArray>) = ByteArray(0)
        override fun recognize(content: ByteArray): OcrResult? = null
    }

    private fun household(): Books = Books(store.create(temp.resolve("T.hfm"), "T", "perry", "Perry", "password1".toCharArray()).session)

    private fun cad(s: String) = Money.parse(s, Currency.CAD)

    private fun pairPhone(books: Books, deviceId: String): ByteArray {
        val phone = KeyPair.generate()
        val invitation = books.sync.invitation("Bureau", "127.0.0.1", 47311, now)
        val desktopKey = SyncCrypto.unb64(invitation.desktopPublicKey)
        books.sync.pair(PairRequest(deviceId, "Pixel", SyncCrypto.b64(phone.publicKey), SyncCrypto.phoneProof(invitation.oneTimeCode, phone.publicKey, desktopKey)), now)
        return PairKey.derive(phone, desktopKey, desktopKey, phone.publicKey)
    }

    private fun send(books: Books, deviceId: String, key: ByteArray, request: SyncRequest): SyncResponse {
        val sealed = SyncCrypto.seal(SyncRequest.serializer(), request, key, books.sync.desktopId, deviceId, Direction.TO_DESKTOP)
        return SyncCrypto.open(SyncResponse.serializer(), books.sync.handle(deviceId, sealed, noConversion, now, today), key, books.sync.desktopId, deviceId, Direction.TO_PHONE)
    }

    @Test
    fun `a trip, a new place and a fill-up go from the phone to the trip log, the odometer and the fuel log`() {
        val books = household()
        try {
            val group = books.groups().first().id
            val alex = books.members.create("Alex", MemberKind.ADULT, LocalDate(1984, 5, 14))
            books.users.update(books.userId, "Perry", alex.id)
            val civic = books.vehicles.save(Vehicle("", group, "Civic", purchaseDate = LocalDate(2023, 4, 12), purchaseOdometer = 38_200, usage = VehicleUsage.MIXED))
            val trailer = books.assets.save(Asset("", group, AssetKind.TRAILER, "Boat trailer"))
            val home = books.places.save(Place("", group, "Home", PlaceCategory.HOME, latitude = 45.401, longitude = -75.703, province = "ON"))
            val key = pairPhone(books, "phone-1")

            // The phone first receives the places, the trailers, the vehicle's fuel and use, and who its user is.
            val first = send(books, "phone-1", key, SyncRequest(now, emptyList()))
            val reference = assertNotNull(first.reference)
            assertEquals(listOf("Home"), reference.places.map { it.name })
            assertEquals(45.401, reference.places.single().latitude)
            assertEquals(listOf("Boat trailer"), reference.trailers.map { it.name })
            assertEquals("GASOLINE" to "MIXED", reference.vehicles.single().let { it.fuelType to it.use })
            assertEquals(alex.id, reference.userMemberId)

            val cottage = PhonePlace("place-1", "change-1", now, "Cottage", "OTHER", 44.77, -76.69, 300)
            val trip = PhoneTrip(
                "trip-1", now, civic.id, "2026-10-03T16:40", "2026-10-03T18:55", 61_500, 61_678, "PERSONAL", alex.id,
                startPlaceId = home.id, endPlaceId = "place-1", endPlace = "Cottage", load = "TOWING", trailerId = trailer.id, passengers = listOf("Sam", "Maya"),
            )
            val fill = PhoneFuel("fuel-1", now, civic.id, "2026-10-04", "62.9", 61_700, "103.20", true, placeId = "place-1")
            val answer = send(books, "phone-1", key, SyncRequest(now, emptyList(), first.referenceVersion, places = listOf(cottage), trips = listOf(trip), fuel = listOf(fill)))
            assertEquals(setOf("change-1", "trip-1", "fuel-1"), answer.imported.toSet())
            assertTrue(answer.failed.isEmpty())

            val logged = books.trips.list(2026).single()
            assertEquals(BigDecimal("178.0"), logged.km, "the distance is the odometers' difference")
            assertEquals("Home" to "Cottage", logged.origin to logged.destination)
            assertEquals(135L, logged.minutes)
            assertEquals(TripLoad.TOWING, logged.load)
            assertEquals(trailer.id, logged.trailerId)
            assertEquals("Sam, Maya", logged.passengers)
            assertEquals("ON", logged.province, "the start place's province")
            assertEquals("phone-1", logged.deviceId)
            assertEquals(LocalDate(2026, 10, 3), logged.date)
            // TRP-06: the trip's odometers are readings of the vehicle.
            val readings = books.vehicles.readings(civic.id).filter { it.source == ReadingSource.TRIP }
            assertEquals(listOf(61_500, 61_678), readings.map { it.odometer })
            val fuel = books.vehicles.fuel(civic.id).single()
            assertEquals("Cottage" to "place-1", fuel.station to fuel.placeId)
            assertEquals(cad("103.20"), fuel.cost)
            assertEquals("phone-1", books.places.find("place-1")?.deviceId)

            // Sent again (the phone did not hear the answer): nothing doubles. A rename comes as a new change.
            val again = send(books, "phone-1", key, SyncRequest(now, emptyList(), places = listOf(cottage), trips = listOf(trip), fuel = listOf(fill)))
            assertEquals(setOf("change-1", "trip-1", "fuel-1"), again.imported.toSet())
            assertEquals(1, books.trips.list(2026).size)
            assertEquals(1, books.vehicles.fuel(civic.id).size)
            send(books, "phone-1", key, SyncRequest(now, emptyList(), places = listOf(cottage.copy(changeId = "change-2", name = "Cottage (Bon Echo)"))))
            assertEquals("Cottage (Bon Echo)", books.places.find("place-1")?.name)
            assertEquals(44.77, books.places.find("place-1")?.latitude)

            // An arrival odometer below the start is refused, with the reason in both languages.
            val bad = send(books, "phone-1", key, SyncRequest(now, emptyList(), trips = listOf(trip.copy(id = "trip-2", endOdometer = 61_400))))
            assertEquals(listOf("trip-2"), bad.failed.map { it.id })
            assertTrue(bad.failed.single().reasonFr != null)
        } finally {
            books.session.close()
        }
    }

    @Test
    fun `consumption is shown apart for towing, and the forecast follows the last 90 days`() {
        val books = household()
        try {
            val group = books.groups().first().id
            val civic = books.vehicles.save(Vehicle("", group, "Civic", purchaseDate = today.minus(DatePeriod(years = 2)), purchaseOdometer = 40_000))
            val maint = books.categories.list().first { it.systemKey == "transport.maintenance" }
            // Every 1 000 km a full tank: 65 L (6.5 L/100) for normal driving; 120 L in the interval spent mostly towing.
            var odo = 50_000
            for (i in 0 until 9) {
                val day = today.minus(DatePeriod(days = 90 - i * 10))
                val towing = i == 5
                books.vehicles.saveFuel(FuelEntry("", civic.id, day, odo, BigDecimal(if (towing) "120" else "65"), cad(if (towing) "180.00" else "97.50")))
                odo += 1_000
            }
            val towStart = 50_000 + 4 * 1_000 + 100
            books.trips.save(
                Trip(
                    "", group, today.minus(DatePeriod(days = 48)), "Cottage", BigDecimal.ZERO, false, TripPurpose.PERSONAL, civic.id,
                    startOdometer = towStart, endOdometer = towStart + 700, load = TripLoad.TOWING,
                ),
            )
            val byLoad = books.vehicles.fuelByLoad(civic.id, today.minus(DatePeriod(years = 1)), today)
            assertEquals(BigDecimal("6.5"), byLoad[TripLoad.NONE]?.per100km)
            assertEquals(BigDecimal("12.0"), byLoad[TripLoad.TOWING]?.per100km)
            assertEquals(cad("0.18"), byLoad[TripLoad.TOWING]?.costPerKm)

            // An oil change every 8 000 km, last done at 50 000 for 80 $.
            val oil = books.vehicles.saveTask(MaintenanceTask("", civic.id, "Oil", intervalKm = 8_000, startDate = today.minus(DatePeriod(days = 90)), startOdometer = 50_000))
            books.vehicles.saveService(ServiceRecord("", civic.id, today.minus(DatePeriod(days = 90)), 50_000, cost = cad("80.00"), taskIds = setOf(oil.id)))
            val f = books.vehicles.forecast(civic.id, today)
            // 8 000 km over 80 days of readings: 100 km a day.
            assertEquals(100.0, f.kmPerDay!!, 0.5)
            assertEquals(mapOf(TripLoad.TOWING to 7), f.shares, "700 of about 9 000 km in 90 days")
            assertEquals(BigDecimal("1.500"), f.unitPrice)
            val three = f.periods.first { it.months == 3 }
            assertEquals(9_200, three.distanceKm)
            // 93 % at 6.5 and 7 % at 12.0 L/100 km.
            val litres = three.quantity!!.toInt()
            assertTrue(litres in 620..650, "litres $litres")
            assertEquals(Money.of(three.quantity * BigDecimal("1.5"), Currency.CAD), three.energyCost)
            // The oil change came due at 58 000 (already passed): today, then every 80 days.
            assertEquals(listOf("Oil" to 2), three.tasks)
            assertEquals(cad("160.00"), three.maintenance)
            assertEquals(three.energyCost!! + cad("160.00"), three.total)
            assertTrue(f.periods.first { it.months == 12 }.distanceKm > three.distanceKm * 3)

            // The Transport budget: a month of fuel and of maintenance, from the next 12 months.
            val lines = books.vehicles.budgetLines(today)
            val fuelCategory = books.categories.list().first { it.systemKey == "transport.fuel" }.id
            assertEquals(setOf(fuelCategory, maint.id), lines.map { it.categoryId }.toSet())
            assertNull(lines.first().current)
            val year = f.periods.first { it.months == 12 }
            val monthlyFuel = lines.first { it.categoryId == fuelCategory }.monthly
            assertEquals(year.energyCost!!.toBigDecimal().divide(BigDecimal(12), 0, java.math.RoundingMode.UP), monthlyFuel.toBigDecimal().setScale(0))
        } finally {
            books.session.close()
        }
    }

    @Test
    fun `a plug-in hybrid's forecast and budget count its charging beside its fuel`() {
        val books = household()
        try {
            val group = books.groups().first().id
            val rav = books.vehicles.save(Vehicle("", group, "RAV4 Prime", fuelType = FuelType.PLUG_IN_HYBRID, purchaseDate = today.minus(DatePeriod(years = 2))))
            // Full tanks every 1 000 km (50 L at 1.50 $), and home charges with no odometer (20 kWh at 0.12 $).
            for (i in 0 until 9) {
                books.vehicles.saveFuel(FuelEntry("", rav.id, today.minus(DatePeriod(days = 90 - i * 10)), 50_000 + i * 1_000, BigDecimal("50"), cad("75.00")))
            }
            for (i in 0 until 18) {
                books.vehicles.saveFuel(FuelEntry("", rav.id, today.minus(DatePeriod(days = 88 - i * 5)), null, BigDecimal("20"), cad("2.40"), energy = Energy.ELECTRICITY, charging = Charging.HOME))
            }
            val f = books.vehicles.forecast(rav.id, today)
            assertEquals(Energy.FUEL, f.energy)
            // 360 kWh over the 8 000 km the readings show.
            assertEquals(ChargingForecast(BigDecimal("4.5"), BigDecimal("0.120")), f.charging)
            val three = f.periods.first { it.months == 3 }
            assertEquals(9_200, three.distanceKm)
            assertEquals(BigDecimal("414"), three.electricityKwh)
            assertEquals(cad("49.68"), three.electricityCost)
            assertEquals(three.energyCost!! + cad("49.68"), three.total)
            val ev = books.categories.list().first { it.systemKey == "transport.ev_charging" }.id
            val fuel = books.categories.list().first { it.systemKey == "transport.fuel" }.id
            val lines = books.vehicles.budgetLines(today).associate { it.categoryId to it.monthly }
            val year = f.periods.first { it.months == 12 }
            assertEquals(year.electricityCost!!.toBigDecimal().divide(BigDecimal(12), 0, java.math.RoundingMode.UP), lines.getValue(ev).toBigDecimal().setScale(0))
            assertTrue(fuel in lines)
            // Its costs: charges as EV charging, fill-ups as fuel.
            val costs = books.vehicles.costs(rav.id, today.minus(DatePeriod(years = 1)), today)
            assertEquals(cad("43.20"), costs.costs.byCategory.single { it.first == ev }.second)
        } finally {
            books.session.close()
        }
    }

    @Test
    fun `charging at home and in public, and electricity against fuel`() {
        val books = household()
        try {
            val group = books.groups().first().id
            val kona = books.vehicles.save(Vehicle("", group, "Kona", fuelType = FuelType.ELECTRIC))
            val civic = books.vehicles.save(Vehicle("", group, "Civic"))
            books.vehicles.saveFuel(FuelEntry("", kona.id, today.minus(DatePeriod(days = 20)), 10_000, BigDecimal("40"), cad("4.40"), charging = Charging.HOME))
            books.vehicles.saveFuel(FuelEntry("", kona.id, today.minus(DatePeriod(days = 10)), 10_250, BigDecimal("40"), cad("4.40"), charging = Charging.HOME))
            books.vehicles.saveFuel(FuelEntry("", kona.id, today.minus(DatePeriod(days = 5)), 10_400, BigDecimal("30"), cad("15.00"), charging = Charging.PUBLIC))
            books.vehicles.saveFuel(FuelEntry("", civic.id, today.minus(DatePeriod(days = 20)), 5_000, BigDecimal("40"), cad("60.00")))
            books.vehicles.saveFuel(FuelEntry("", civic.id, today.minus(DatePeriod(days = 10)), 5_500, BigDecimal("35"), cad("52.50")))
            val charging = books.vehicles.chargingCosts(kona.id, today.minus(DatePeriod(years = 1)), today)
            assertEquals(listOf(Charging.HOME, Charging.PUBLIC), charging.map { it.charging })
            assertEquals(BigDecimal("0.110"), charging[0].perKwh)
            assertEquals(BigDecimal("0.500"), charging[1].perKwh)
            val electric = books.vehicles.fuelStats(kona.id, today.minus(DatePeriod(years = 1)), today)
            assertEquals(BigDecimal("17.5"), electric.per100km)
            assertEquals(cad("0.05"), electric.costPerKm, "(4.40 + 15.00) over 400 km, from the first full charge")
            assertEquals(cad("0.11"), books.vehicles.householdCostPerKm(Energy.FUEL, today.minus(DatePeriod(years = 1)), today, kona.id, Currency.CAD))
            // A charge's place is kept for electricity only.
            val gas = books.vehicles.saveFuel(FuelEntry("", civic.id, today, 5_900, BigDecimal("30"), cad("45.00"), charging = Charging.PUBLIC, energy = Energy.ELECTRICITY))
            assertNull(gas.charging)
            assertNull(gas.energy, "only a plug-in hybrid takes both")
        } finally {
            books.session.close()
        }
    }

    @Test
    fun `details, inspection reminders, the logbook and kilometres per province`() {
        val books = household()
        try {
            val group = books.groups().first().id
            val van = books.vehicles.save(
                Vehicle(
                    "", group, "Transit", plate = "AB 123", usage = VehicleUsage.COMMERCIAL, inspectionDue = today.plus(DatePeriod(days = 20)),
                    operatorRenewal = today.plus(DatePeriod(days = 200)),
                    details = VehicleDetails("3.5 L V6", Transmission.AUTOMATIC, DriveKind.RWD, BigDecimal("95"), null, "235/65R16C", "235/65R16C", "5W-30", BigDecimal("5.7"), 3_400, 4_309),
                ),
            )
            books.vehicles.saveFuel(FuelEntry("", van.id, today, 12_000, BigDecimal("50")))
            // Saving again keeps what belongs to the vehicle.
            books.vehicles.save(books.vehicles.get(van.id).copy(name = "Transit 250"))
            val back = books.vehicles.get(van.id)
            assertEquals("Transit 250", back.name)
            assertEquals(4_309, back.details.gvwrKg)
            assertEquals(BigDecimal("5.7"), back.details.oilLitres)
            assertEquals(Transmission.AUTOMATIC, back.details.transmission)
            assertEquals(1, books.vehicles.fuel(van.id).size)
            val renewals = books.vehicles.renewals(today).filter { it.subjectId == van.id }.map { it.kind }
            assertEquals(listOf(RenewalKind.SAFETY_INSPECTION), renewals, "the operator renewal is further than the lead time")

            val office = books.places.save(Place("", group, "Office", PlaceCategory.WORK, province = "ON"))
            val gatineau = books.places.save(Place("", group, "Client in Gatineau", PlaceCategory.CLIENT, province = "QC"))
            fun trip(day: Int, from: Place, to: Place, start: Int, end: Int, purpose: TripPurpose, province: String? = null) = books.trips.save(
                Trip(
                    "", group, LocalDate(2026, 3, day), "", BigDecimal.ZERO, false, purpose, van.id, startPlaceId = from.id, endPlaceId = to.id,
                    startOdometer = start, endOdometer = end, startAt = LocalDateTime(2026, 3, day, 8, 0), endAt = LocalDateTime(2026, 3, day, 8, 40), province = province,
                ),
            )
            trip(2, office, gatineau, 10_000, 10_032, TripPurpose.BUSINESS)
            trip(2, gatineau, office, 10_032, 10_064, TripPurpose.BUSINESS, province = "QC")
            trip(3, office, office, 10_100, 10_110, TripPurpose.PERSONAL)
            val book = books.trips.logbook(van.id, 2026)
            assertEquals(listOf("Office" to "Client in Gatineau", "Client in Gatineau" to "Office", "Office" to "Office"), book.lines.map { it.from to it.to })
            assertEquals(listOf(10_000, 10_032, 10_100), book.lines.map { it.startOdometer })
            assertEquals(BigDecimal("64.0"), book.use?.workKm)
            assertEquals(mapOf("ON" to BigDecimal("42.0"), "QC" to BigDecimal("32.0")), books.trips.kmByProvince(2026, van.id))
            // A trip's places keep their names in the log once the place is gone.
            books.places.delete(gatineau)
            assertEquals("Client in Gatineau", books.trips.logbook(van.id, 2026).lines.first().to)
        } finally {
            books.session.close()
        }
    }

    @Test
    fun `a phone that may only add cannot rename a place, and what it sends is checked`() {
        val books = household()
        var civicId = ""
        var homeId = ""
        try {
            val group = books.groups().first().id
            civicId = books.vehicles.save(Vehicle("", group, "Civic", purchaseOdometer = 38_200)).id
            homeId = books.places.save(Place("", group, "Home", PlaceCategory.HOME, latitude = 45.401, longitude = -75.703)).id
            val sam = books.users.add("sam", "Sam", ca.schippers.hfm.domain.Role.MEMBER, "password2-long".toCharArray()).userId
            books.session.setPermission(group, sam, ca.schippers.hfm.domain.PermissionLevel.CAPTURE_ONLY)
        } finally {
            books.session.close()
        }
        val sam = Books(store.unlock(temp.resolve("T.hfm"), "sam", "password2-long".toCharArray()))
        try {
            val key = pairPhone(sam, "phone-2")
            val answer = send(
                sam, "phone-2", key,
                SyncRequest(
                    now, emptyList(),
                    places = listOf(PhonePlace("place-new", "c1", now, "Corner store", "STORE", 45.41, -75.70), PhonePlace(homeId, "c2", now, "Renamed by a phone")),
                    fuel = listOf(PhoneFuel("f1", now, civicId, "2026-10-04", "1E999999999"), PhoneFuel("f2", now, civicId, "2026-10-04", "40", cost = "1e-999999999")),
                    trips = listOf(
                        PhoneTrip("t1", now, civicId, "2026-10-03T16:40", "2026-10-03T17:00", 61_500, 61_520, trailerId = "not-a-trailer"),
                        PhoneTrip("t2", now, civicId, "2026-10-03T16:40", "2026-10-03T17:00", 61_500, 61_520, driverId = "not-a-member"),
                        PhoneTrip("t3", now, civicId, "2026-10-03T18:40", "2026-10-03T19:00", 61_520, 61_540, endPlace = "x".repeat(100_000), notes = "n".repeat(100_000)),
                    ),
                ),
            )
            assertEquals(setOf("c1", "t3"), answer.imported.toSet(), "a new place, and the trip with its texts cut")
            assertEquals(setOf("c2", "f1", "f2", "t1", "t2"), answer.failed.map { it.id }.toSet())
            assertEquals("Home", sam.places.find(homeId)?.name, "renaming needs the right to change")
            assertEquals("Corner store", sam.places.find("place-new")?.name)
            val trip = sam.trips.list(2026).single()
            assertEquals(120, trip.destination.length)
            assertEquals(500, trip.notes?.length)
            assertTrue(sam.vehicles.fuel(civicId).isEmpty())
        } finally {
            sam.session.close()
        }
    }

    @Test
    fun `trips follow their vehicle's group, deleted places stay deleted, and what a user may only view is not ticked`() {
        val books = household()
        val shared = books.groups().single().id
        val private = books.session.createGroup("Perry private", private = true)
        val van = books.vehicles.save(Vehicle("", shared, "Van", purchaseOdometer = 1_000))
        val own = books.vehicles.save(Vehicle("", private, "Own car", purchaseOdometer = 1_000))
        books.vehicles.addStarterTasks(van.id, today) { it }
        val gone = books.places.save(Place("", shared, "Old office", latitude = 45.0, longitude = -75.0))
        val samId = books.users.add("sam", "Sam", ca.schippers.hfm.domain.Role.MEMBER, "password2-long".toCharArray()).userId
        books.session.setPermission(shared, samId, ca.schippers.hfm.domain.PermissionLevel.VIEW)
        try {
            // The administrator's phone stores in the shared group, but a trip in the private car stays private.
            val key = pairPhone(books, "phone-1")
            assertEquals(shared, books.sync.devices().single().groupId)
            val trip = PhoneTrip("trip-own", now, own.id, "2026-10-03T08:00", "2026-10-03T08:30", 1_100, 1_120, endPlace = "Clinic")
            assertEquals(listOf("trip-own"), send(books, "phone-1", key, SyncRequest(now, emptyList(), trips = listOf(trip))).imported)
            assertEquals(private, books.trips.list(2026).single().groupId)
            assertNull(books.ledger(books.group(shared)).extrasQueries.tripById("trip-own").executeAsOneOrNull())

            // A place deleted here is not brought back by a phone that still has it.
            books.places.delete(gone)
            val renamed = send(books, "phone-1", key, SyncRequest(now, emptyList(), places = listOf(PhonePlace(gone.id, "c-9", now, "Old office (closed)"))))
            assertEquals(listOf("c-9"), renamed.failed.map { it.id })
            assertNull(books.places.find(gone.id))

            // A fill-up stored under the phone's id before the computer could note it (it stopped in between) is not stored again.
            books.vehicles.saveFuel(FuelEntry("", van.id, LocalDate(2026, 10, 4), null, BigDecimal("40")), newId = "fuel-9")
            val again = send(books, "phone-1", key, SyncRequest(now, emptyList(), fuel = listOf(PhoneFuel("fuel-9", now, van.id, "2026-10-04", "40"))))
            assertEquals(listOf("fuel-9"), again.imported)
            assertEquals(1, books.vehicles.fuel(van.id).size)
        } finally {
            books.session.close()
        }
        val sam = Books(store.unlock(temp.resolve("T.hfm"), "sam", "password2-long".toCharArray()))
        try {
            sam.session.createGroup("Sam private", private = true)
            assertEquals(1, sam.vehicles.fuel(van.id).size)
            // Sam may only view the van: no trip in it, from the computer or the phone, and no seasonal tick.
            assertFailsWith<ca.schippers.hfm.data.AccessDeniedException> {
                sam.trips.save(Trip("", sam.groups().single { it.isPrivate }.id, today, "Store", BigDecimal("5"), false, TripPurpose.PERSONAL, van.id))
            }
            val key = pairPhone(sam, "phone-2")
            val first = send(sam, "phone-2", key, SyncRequest(now, emptyList()))
            assertTrue(assertNotNull(first.reference?.seasonal).tasks.none { it.subjectId == van.id }, "the van's tasks are not offered")
            val task = sam.vehicles.tasks(van.id).first()
            val answer = send(
                sam, "phone-2", key,
                SyncRequest(
                    now, emptyList(),
                    trips = listOf(PhoneTrip("trip-van", now, van.id, "2026-10-03T08:00", "2026-10-03T08:30", 1_100, 1_120)),
                    tasksDone = listOf(ca.schippers.hfm.sync.PhoneTaskDone("tick-van", now, task.id, van.id, true, "2026-10-05")),
                ),
            )
            assertEquals(setOf("trip-van", "tick-van"), answer.failed.map { it.id }.toSet())
            assertTrue(sam.trips.list(2026).isEmpty() && sam.vehicles.services(van.id).isEmpty())
        } finally {
            sam.session.close()
        }
    }
}

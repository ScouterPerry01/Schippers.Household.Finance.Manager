package ca.schippers.hfm.books

import ca.schippers.hfm.domain.Ids
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.sync.PhonePlace
import ca.schippers.hfm.sync.RefPlace
import ca.schippers.hfm.sync.TripRules
import ca.schippers.hfm.data.ledger.Trip_place as PlaceRow

/** TRP-02: what a saved place is, for the purpose a trip there suggests. */
enum class PlaceCategory { HOME, WORK, CLIENT, STORE, FUEL, CHARGING, GARAGE, MEDICAL, OTHER }

/**
 * TRP-02: a saved place. A location fix within [radiusM] metres of [latitude], [longitude] is this
 * place. Kept in the ledger of [groupId] and on the user's phone, never sent to a map service.
 * [province] (two letters, a province or a state) is where trips from here are counted (TRP-09).
 */
data class Place(
    val id: String,
    val groupId: String,
    val name: String,
    val category: PlaceCategory = PlaceCategory.OTHER,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusM: Int = TripRules.DEFAULT_RADIUS_M,
    val province: String? = null,
    val notes: String? = null,
    val archived: Boolean = false,
    /** The phone the place was saved on, if any. */
    val deviceId: String? = null,
)

/** TRP-02: saved places, managed on the desktop (Trip log, Places) and on the phone. */
class PlaceService internal constructor(private val books: Books) {

    fun list(includeArchived: Boolean = false): List<Place> = books.groups().flatMap { g ->
        books.ledger(g).placesQueries.places().executeAsList().map { it.toPlace(g.id) }
    }.filter { includeArchived || !it.archived }.sortedBy { it.name.lowercase() }

    /** The place with [id] in any group the user can see, or null. */
    fun find(id: String): Place? = books.groups().firstNotNullOfOrNull { g ->
        books.ledger(g).placesQueries.placeById(id).executeAsOneOrNull()?.toPlace(g.id)
    }

    fun save(p: Place): Place = store(p, PermissionLevel.EDIT)

    private fun store(p: Place, level: PermissionLevel): Place {
        validate(p.name.isNotBlank(), "error.nameRequired")
        validate((p.latitude == null) == (p.longitude == null), "error.placeCoordinates")
        validate(p.latitude == null || (p.latitude in -90.0..90.0 && p.longitude!! in -180.0..180.0), "error.placeCoordinates")
        validate(p.radiusM in 10..5_000, "error.placeRadius")
        validate(p.province == null || p.province.trim().length == 2, "error.placeProvince")
        val existing = if (p.id.isBlank()) null else find(p.id)
        val group = books.group(existing?.groupId ?: p.groupId).also { books.require(it, level) }
        val id = p.id.ifBlank { Ids.newId() }
        val now = books.now()
        val created = existing?.let { books.ledger(group).placesQueries.placeById(id).executeAsOneOrNull()?.created_at } ?: now
        books.ledger(group).placesQueries.upsertPlace(
            id, p.name.trim().take(MAX_NAME), p.category.name, p.address.blankToNull(), p.latitude, p.longitude, p.radiusM.toLong(),
            p.province?.trim()?.uppercase()?.ifEmpty { null }, p.notes.blankToNull(), if (p.archived) 1 else 0, existing?.deviceId ?: p.deviceId, created, now,
        )
        return p.copy(id = id, groupId = group.id)
    }

    /**
     * A place's trips keep its name; only the place goes. Its id is remembered, so a phone that still
     * has the place cannot bring it back by renaming it.
     */
    fun delete(p: Place) {
        val group = books.group(p.groupId).also { books.require(it, PermissionLevel.EDIT) }
        books.ledger(group).placesQueries.deletePlace(p.id)
        books.putSetting(DELETED_KEY + p.id, "1")
    }

    /**
     * TRP-02: a place saved on the phone, or renamed there. A new place goes to [groupId]; a known
     * one keeps its group and everything but its name (and its fix and category when it had none).
     * Saving a new place needs the right to add in the group; renaming one, the right to change it.
     */
    fun receive(groupId: String, deviceId: String, p: PhonePlace): Place {
        val existing = find(p.id)
        if (existing == null) validate(books.setting(DELETED_KEY + p.id) == null, "error.notFound")
        val category = PlaceCategory.entries.firstOrNull { it.name == p.category } ?: PlaceCategory.OTHER
        val place = existing?.copy(
            name = p.name,
            latitude = existing.latitude ?: p.latitude,
            longitude = existing.longitude ?: p.longitude,
            category = if (existing.category == PlaceCategory.OTHER) category else existing.category,
            // TRP-11, TRP-19: an address typed or looked up on the phone fills one the place did not have.
            address = existing.address ?: phoneText(p.address, MAX_ADDRESS),
        ) ?: Place(
            p.id, groupId, p.name, category, address = phoneText(p.address, MAX_ADDRESS), latitude = p.latitude, longitude = p.longitude,
            radiusM = p.radiusM.coerceIn(10, 5_000), province = books.province.name, deviceId = deviceId,
        )
        return store(place, if (existing == null) PermissionLevel.CAPTURE_ONLY else PermissionLevel.EDIT)
    }

    /** TRP-02: what the phone gets: the places of every group the user can see, not archived. */
    fun forPhone(): List<RefPlace> = list().map {
        RefPlace(it.id, it.name, it.category.name, it.latitude, it.longitude, it.radiusM, it.address, it.province)
    }

    private fun PlaceRow.toPlace(groupId: String) = Place(
        id, groupId, name, PlaceCategory.entries.firstOrNull { it.name == category } ?: PlaceCategory.OTHER, address, latitude, longitude, radius_m.toInt(),
        province, notes, archived == 1L, device_id,
    )

    private companion object {
        const val MAX_NAME = 120
        const val MAX_ADDRESS = 200

        /** `place.deleted.<id>`: a place deleted on the computer, which a phone may not make again. */
        const val DELETED_KEY = "place.deleted."
    }
}

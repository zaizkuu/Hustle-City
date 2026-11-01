package com.hustlecity.game

import com.hustlecity.game.data.Catalog
import com.hustlecity.game.data.PlaceKind
import com.hustlecity.game.world.BKind
import com.hustlecity.game.world.City
import com.hustlecity.game.world.Maps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Furniture is sold where you'd look for it: salon chairs at the beauty supplier (not next to the fryers), gym machines
 * at the fitness store, club and cinema gear at the sound and light store. Those stores open on every map, and an older
 * save keeps the buildings it was using.
 */
class FurnitureStoresTest {
    private fun storesSelling(id: String) = Catalog.places.filter { it.kind == PlaceKind.FURNITURE_STORE && id in it.stock }.map { it.id }

    @Test
    fun everythingIsSoldWhereItBelongs() {
        // props that are only scenery (office backdrops, the store's own cart rack) aside, everything is for sale somewhere
        val props = setOf("desk", "pallet", "cart_rack")
        for (f in Catalog.furniture) if (f.id !in props) assertTrue("${f.id} is sold somewhere", storesSelling(f.id).isNotEmpty())
        for (id in Catalog.places.flatMap { it.stock }) assertNotNull("$id is real furniture", Catalog.item(id))
        assertEquals(listOf("salonpro"), storesSelling("hair_chair"))
        assertEquals(listOf("salonpro"), storesSelling("hair_wash"))
        for (id in listOf("treadmill", "exercise_bike", "rowing_machine", "workout_bench", "weight_rack", "boxing_bag", "gym_locker", "shower", "fitness_board"))
            assertEquals(id, listOf("fitzone"), storesSelling(id))
        for (id in listOf("dance_floor", "dj_booth", "stage", "stage_lights", "cinema_screen", "cinema_seats", "speaker_large", "coat_check"))
            assertEquals(id, listOf("stagecraft"), storesSelling(id))
        for (id in listOf("fryer", "grill", "pizza_oven", "coffee_machine", "pastry_case", "ice_cream_freezer")) assertEquals(id, listOf("primekitchen"), storesSelling(id))
        assertFalse("the kitchen supplier sells kitchens", Catalog.place("primekitchen").stock.any { it.startsWith("hair") || it == "treadmill" || it == "bed" })
        // every business type can get all it needs
        for (t in Catalog.allTypes) for (req in t.requirements) assertTrue("${t.id} can buy a ${req.label}",
            Catalog.furniture.any { req.tag in it.tags && storesSelling(it.id).isNotEmpty() })
    }

    @Test
    fun theStoresOpenOnEveryMap() {
        for (map in Maps.all) {
            val city = City(1337L, map.id)
            val ids = HashSet<Int>()
            for (place in Catalog.places) {
                val b = city.placeBuilding(place.id)
                assertNotNull("${map.id}: ${place.name} is on the map", b)
                assertTrue("${map.id}: ${place.name} has its own building", ids.add(b!!.id))
            }
            for ((id, _) in Catalog.SPECIALTY_STORES) {
                val b = city.placeBuilding(id)!!
                assertEquals(BKind.SPECIAL, b.kind)
                assertTrue("${map.id}: $id faces a street", b.frontEdge >= 0)
            }
        }
    }

    @Test
    fun anOlderSaveKeepsItsBuildings() {
        for (map in Maps.all) {
            val city = City(1337L, map.id)
            val store = city.placeBuilding("salonpro")!!
            val was = City(1337L, map.id).let { fresh -> fresh.buildings[store.id] }
            // a save from before the store existed has your business in that building
            assertTrue(city.clearStoresFrom(setOf(store.id)))
            val moved = city.placeBuilding("salonpro")!!
            assertNotEquals("${map.id}: the store moved", store.id, moved.id)
            assertEquals(null, city.buildings[store.id].placeId)
            assertNotEquals("${map.id}: your building is a building again", BKind.SPECIAL, city.buildings[store.id].kind)
            assertEquals(BKind.SPECIAL, was.kind)
            assertFalse("nothing to move when nothing's in the way", city.clearStoresFrom(setOf(-1)))
        }
    }
}

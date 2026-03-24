package de.henosch.bibelvers

import org.junit.Assert.assertEquals
import org.junit.Test

class BibelVersRepositoryTest {

    @Test
    fun selectRandomEntryIndex_reusesStoredIndexForSameDay() {
        val selected = BibelVersRepository.selectRandomEntryIndex(
            storedIndex = 7,
            size = 20,
            usedVerses = setOf(7),
            year = 2026,
            currentYear = 2026
        ) {
            error("selector must not run when the day already has a stored verse")
        }

        assertEquals(7, selected)
    }

    @Test
    fun selectRandomEntryIndex_usesAvailableVersesWhenNoDayEntryExists() {
        val selected = BibelVersRepository.selectRandomEntryIndex(
            storedIndex = null,
            size = 5,
            usedVerses = setOf(1, 3),
            year = 2026,
            currentYear = 2026
        ) { candidates ->
            assertEquals(listOf(0, 2, 4), candidates)
            candidates.first()
        }

        assertEquals(0, selected)
    }
}

package com.hustlecity.game.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hustlecity.game.model.DayRecord

enum class Screen { MENU, NEW_GAME, GAME }

enum class AppId(val title: String) {
    PERSONA("Persona"), CONTACTS("Contacts"), RIVALS("Rivals"), STAFF("Staff"),
    BIZHUB("BizHub"), FINANCE("Finance"), REALTY("Realty"), MAPS("Maps"),
}

sealed class Panel {
    data class Place(val buildingId: Int, val placeId: String) : Panel()
    data class StartBusiness(val buildingId: Int) : Panel()
    data class RivalShop(val buildingId: Int) : Panel()
    data class Listing(val buildingId: Int) : Panel()
    data object Settings : Panel()
    data object Help : Panel()
    data object Sleep : Panel()
    data class Warehouse(val businessId: Int) : Panel()
    data class Trunk(val vehicleId: Int) : Panel()
}

enum class ToastKind { INFO, GOOD, BAD, SAVE }

data class Toast(val id: Long, val text: String, val kind: ToastKind, val bornMs: Long, val icon: String? = null)

data class Confirm(val title: String, val text: String, val confirmLabel: String, val action: () -> Unit)

class UiState {
    /** Bumped a few times per second and after every player action; composables read it to refresh. */
    var tick by mutableIntStateOf(0)
    var screen by mutableStateOf(Screen.MENU)
    var app by mutableStateOf<AppId?>(null)
    var panel by mutableStateOf<Panel?>(null)
    var selected by mutableIntStateOf(-1)
    var phoneOpen by mutableStateOf(true)
    var objectivesOpen by mutableStateOf(true)
    var designer by mutableStateOf(false)
    var bizHubSel by mutableIntStateOf(-1)
    var bizHubTab by mutableIntStateOf(0)
    val mapFilters = mutableStateMapOf<String, Boolean>()
    val toasts = mutableStateListOf<Toast>()
    var summary by mutableStateOf<DayRecord?>(null)
    var skipping by mutableStateOf(false)
    var confirm by mutableStateOf<Confirm?>(null)
    var info by mutableStateOf<Pair<String, String>?>(null)
    /** "bankrupt" or "victory" while an ending screen is shown. */
    var ending by mutableStateOf<String?>(null)
    /** Time of the last rendered frame, for widgets that redraw every frame (minimap). */
    var frameTime by mutableStateOf(0L)
    /** One-time tip (title, text). */
    var tip by mutableStateOf<Pair<String, String>?>(null)
}

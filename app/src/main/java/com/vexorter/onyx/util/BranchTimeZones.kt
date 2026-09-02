package com.vexorter.onyx.util

import java.time.ZoneId

/**
 * Часовой пояс филиала.
 *
 * Сервер отдаёт время пар в местном времени филиала, а телефон живёт в своём поясе.
 * Пока студент физически в своём городе, разницы нет — но заочник из Калининграда,
 * учащийся в Головном вузе, иначе получал бы «идёт сейчас» и напоминания
 * со сдвигом в час, а студент камчатского филиала — в девять.
 */
object BranchTimeZones {

    private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

    private val byGuid: Map<String, ZoneId> = mapOf(
        // Арзамасский филиал — Арзамас, Нижегородская область
        "2004515e-26ef-11e1-a5da-005056bd3ce5" to MOSCOW,
        // Башкирский филиал — Уфа, UTC+5
        "20045169-26ef-11e1-a5da-005056bd3ce5" to ZoneId.of("Asia/Yekaterinburg"),
        // Владимирский филиал — Владимир
        "844a7034-7a9a-11df-b9d8-005056bd3ce5" to MOSCOW,
        // Волгоградский филиал — Волгоград
        "4935b401-0858-11e0-8be3-005056bd3ce5" to ZoneId.of("Europe/Volgograd"),
        // Головной вуз — Мытищи
        "844a7033-7a9a-11df-b9d8-005056bd3ce5" to MOSCOW,
        // Ижевский филиал — Ижевск, UTC+4
        "20045161-26ef-11e1-a5da-005056bd3ce5" to ZoneId.of("Europe/Samara"),
        // Казанский филиал — Казань
        "4935b3fd-0858-11e0-8be3-005056bd3ce5" to MOSCOW,
        // Калининградский филиал — Калининград, UTC+2
        "20045162-26ef-11e1-a5da-005056bd3ce5" to ZoneId.of("Europe/Kaliningrad"),
        // Камчатский филиал — Петропавловск-Камчатский, UTC+12
        "20045163-26ef-11e1-a5da-005056bd3ce5" to ZoneId.of("Asia/Kamchatka"),
        // Краснодарский филиал — Краснодар
        "4935b400-0858-11e0-8be3-005056bd3ce5" to MOSCOW,
        // Крымский кооперативный институт — Симферополь
        "f9e01ce6-b8e1-11ed-b169-3cecef02455b" to ZoneId.of("Europe/Simferopol"),
        // Поволжский филиал — Энгельс, Саратовская область, UTC+4
        "4935b3fe-0858-11e0-8be3-005056bd3ce5" to ZoneId.of("Europe/Saratov"),
        // Саранский филиал — Саранск
        "844a7035-7a9a-11df-b9d8-005056bd3ce5" to MOSCOW,
        // Смоленский филиал — Смоленск
        "20045167-26ef-11e1-a5da-005056bd3ce5" to MOSCOW,
        // Чебоксарский филиал — Чебоксары
        "4935b3ff-0858-11e0-8be3-005056bd3ce5" to MOSCOW,
    )

    /**
     * Пояс филиала; для неизвестного GUID — Москва: большинство филиалов в ней,
     * и это заведомо лучше, чем пояс телефона где-нибудь в отпуске.
     */
    fun zoneOf(branchGuid: String): ZoneId = byGuid[branchGuid] ?: MOSCOW

    /** Отличается ли пояс филиала от пояса телефона прямо сейчас. */
    fun differsFromDevice(branchGuid: String): Boolean {
        val now = java.time.Instant.now()
        val branch = zoneOf(branchGuid).rules.getOffset(now)
        val device = ZoneId.systemDefault().rules.getOffset(now)
        return branch != device
    }
}

package com.onlinechallenges.app.data

/**
 * В карточке челленджа баллы показываются всегда, когда они есть.
 * Плашка режима «Каждый день» скрывается: она вместе с баллами не влезает
 * в одну строку и мешает длинным названиям. Сам режим остаётся доступен
 * на экране челленджа.
 */
fun shouldShowChallengeCardScore(points: Int): Boolean = points > 0

fun shouldShowModeBadge(mode: ChallengeMode): Boolean = mode != ChallengeMode.DAILY

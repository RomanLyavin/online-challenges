package com.onlinechallenges.app.data

/**
 * В карточке челленджа баллы показываются только там, где они не заданы
 * режимом по умолчанию. Для режима «Каждый день» плашка с баллами лишняя и
 * мешает длинным названиям и бейджам.
 */
fun shouldShowChallengeCardScore(mode: ChallengeMode, points: Int): Boolean =
    mode != ChallengeMode.DAILY && points > 0

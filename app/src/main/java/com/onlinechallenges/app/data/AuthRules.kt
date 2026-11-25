package com.onlinechallenges.app.data

/** Общие правила проверки email до обращения к серверу. */
object AuthRules {
    fun emailAlreadyKnown(
        email: String,
        accounts: List<UserAccount>,
        profiles: List<Profile>,
        exceptCode: String? = null
    ): Boolean {
        val normalized = EmailHasher.normalize(email)
        if (normalized.isBlank()) return false
        val hash = EmailHasher.hash(normalized)
        return accounts.any {
            it.code != exceptCode && it.email.equals(normalized, ignoreCase = true)
        } || profiles.any {
            it.code != exceptCode && (
                it.email.equals(normalized, ignoreCase = true) ||
                    it.emailHash.equals(hash, ignoreCase = true)
                )
        }
    }
}

package com.ridevibe.core.domain.model

data class WalletTransaction(
    val id: String,
    val title: String, // e.g. "Ticket RV-1234 • PITX → Baguio"
    /** Positive = credit in, negative = payment out. */
    val amountPhp: Double,
    val timestampEpochMillis: Long,
)

data class Wallet(
    val balancePhp: Double,
    val transactions: List<WalletTransaction>,
)

/** A message in the support conversation. */
data class SupportMessage(
    val id: String,
    val text: String,
    val fromUser: Boolean,
    val timestampEpochMillis: Long,
)

package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.SupportMessage
import com.ridevibe.core.domain.model.Wallet

interface WalletRepository {
    suspend fun getWallet(): Wallet
}

interface SupportRepository {
    suspend fun getMessages(): List<SupportMessage>

    /** Sends [text] and returns the updated conversation (including any reply). */
    suspend fun sendMessage(text: String): List<SupportMessage>
}

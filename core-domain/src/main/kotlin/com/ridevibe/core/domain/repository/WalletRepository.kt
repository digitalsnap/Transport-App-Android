package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.Wallet

interface WalletRepository {
    suspend fun getWallet(): Wallet
}

package com.ridevibe.core.domain.repository

import com.ridevibe.core.domain.model.SupportMessage

interface SupportRepository {
    suspend fun getMessages(): List<SupportMessage>

    /** Sends [text] and returns the updated conversation (including any reply). */
    suspend fun sendMessage(text: String): List<SupportMessage>
}

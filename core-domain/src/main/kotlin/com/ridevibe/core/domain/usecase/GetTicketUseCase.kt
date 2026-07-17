package com.ridevibe.core.domain.usecase

import com.ridevibe.core.domain.model.Ticket
import com.ridevibe.core.domain.repository.CheckoutRepository
import javax.inject.Inject

class GetTicketUseCase @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
) {
    suspend operator fun invoke(ticketId: String): Result<Ticket> =
        checkoutRepository.getTicket(ticketId)
}

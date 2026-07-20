package com.ridevibe.app.navigation

import androidx.lifecycle.ViewModel
import com.ridevibe.core.domain.session.BookingCart
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Exposes the shared [BookingCart] to the nav graph for round-trip routing. */
@HiltViewModel
class CartViewModel @Inject constructor(
    val cart: BookingCart,
) : ViewModel()

package com.moneyplanner.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a category to an icon.
 *
 * Matching is done on the name so that categories the user creates themselves still get a
 * sensible icon rather than a generic placeholder. The comparison is case insensitive and
 * looks for the word anywhere in the name, so "Kids school fees" still finds the school
 * icon.
 */
fun categoryIcon(categoryName: String): ImageVector {
    val name = categoryName.lowercase()
    return when {
        name.contains("grocer") || name.contains("vegetable") -> Icons.Default.LocalGroceryStore
        name.contains("food") || name.contains("restaurant") ||
            name.contains("dining") || name.contains("tiffin") -> Icons.Default.Restaurant
        name.contains("fuel") || name.contains("petrol") || name.contains("diesel") ->
            Icons.Default.LocalGasStation
        name.contains("travel") || name.contains("trip") || name.contains("flight") ->
            Icons.Default.Flight
        name.contains("shop") || name.contains("cloth") -> Icons.Default.ShoppingBag
        name.contains("dress") || name.contains("wardrobe") -> Icons.Default.Checkroom
        name.contains("rent") || name.contains("house") || name.contains("home") ->
            Icons.Default.House
        name.contains("utilit") || name.contains("electric") || name.contains("water") ||
            name.contains("gas bill") -> Icons.Default.Bolt
        name.contains("school") || name.contains("educat") || name.contains("tuition") ||
            name.contains("college") -> Icons.Default.School
        name.contains("medical") || name.contains("health") || name.contains("doctor") ||
            name.contains("hospital") -> Icons.Default.LocalHospital
        name.contains("entertain") || name.contains("movie") || name.contains("ott") ->
            Icons.Default.Movie
        name.contains("family") -> Icons.Default.People
        name.contains("emi") || name.contains("loan") -> Icons.Default.AccountBalance
        name.contains("insur") -> Icons.Default.Shield
        name.contains("invest") || name.contains("mutual") || name.contains("sip") ->
            Icons.Default.TrendingUp
        name.contains("bike") || name.contains("scooter") -> Icons.AutoMirrored.Filled.DirectionsBike
        name.contains("car") || name.contains("vehicle") -> Icons.Default.DirectionsCar
        name.contains("gift") -> Icons.Default.CardGiftcard
        name.contains("salary") || name.contains("business") || name.contains("freelance") ->
            Icons.Default.Work
        else -> Icons.Default.Payments
    }
}

package com.moneyplanner.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.moneyplanner.ui.components.softShadow
import com.moneyplanner.ui.theme.Elevation
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * The bottom bar, as a floating dock.
 *
 * A pill suspended above the gesture bar, with the active tab carried in a marigold capsule
 * that slides between tabs as its colour fades across. It is the one piece of chrome that
 * floats, so it is the one thing drawn as raised glass: near-opaque card tone, a hairline
 * and a soft shadow. A true backdrop blur would need a library this offline app does not
 * carry, and near-opaque reads better over figures than a blur would anyway.
 *
 * Every tab keeps its label. A row of bare icons is quicker to draw than to learn.
 */
@Composable
fun FloatingDock(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors
    val shape = RoundedCornerShape(32.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
            .softShadow(shape, elevation = Elevation.level2)
            .background(colors.card.copy(alpha = 0.96f), shape)
            .border(1.dp, colors.cardBorder, shape)
            .padding(6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy
                ?.any { it.route == destination.route } == true
            DockItem(
                destination = destination,
                selected = selected,
                onClick = { onSelect(destination) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DockItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MoneyTheme.colors
    val container by animateColorAsState(
        if (selected) colors.accent else Color.Transparent,
        label = "dock-tab"
    )
    val ink = if (selected) colors.onAccent else colors.textSecondary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = ink,
            maxLines = 1
        )
    }
}

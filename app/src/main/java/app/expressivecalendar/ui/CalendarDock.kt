package app.expressivecalendar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.expressivecalendar.data.CalendarView

enum class Tab(val label: String, val icon: ImageVector, val view: CalendarView) {
    MONTH("Month", Icons.Outlined.CalendarMonth, CalendarView.MONTH),
    AGENDA("Agenda", Icons.Outlined.ViewAgenda, CalendarView.AGENDA),
    DAY("Day", Icons.Outlined.CalendarViewDay, CalendarView.DAY),
    CLOCK("Clock", Icons.Outlined.Schedule, CalendarView.CLOCK);

    companion object {
        fun of(v: CalendarView) = entries.firstOrNull { it.view == v } ?: MONTH
    }
}

/**
 * The floating glass dock with a detached add button, as in Expressive Clock. Here the add button
 * is always present, since every view can create an event.
 */
@Composable
fun CalendarDock(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isAppDark()
    val blue = MaterialTheme.colorScheme.primary
    val dockTint = if (dark) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .88f)
    else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .84f)
    val dockShape = RoundedCornerShape(DOCK_HEIGHT / 2)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FAB_GAP),
    ) {
        Box(
            modifier = Modifier
                .width(DOCK_WIDTH)
                .height(DOCK_HEIGHT)
                .luminousElasticClickable(shape = dockShape, maxStretchPercent = 0.06f, maxTranslateDp = 1.8.dp)
                .glass(dockShape, dockTint, .88f, shadowElevation = 18.dp)
                .blueGlow(dockShape),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val tabs = Tab.entries
                val itemWidth = maxWidth / tabs.size
                val indicatorSize = 50.dp
                val selectedOffset by animateDpAsState(
                    targetValue = itemWidth * tabs.indexOf(selected) + (itemWidth - indicatorSize) / 2,
                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 600f),
                    label = "dockIndicator",
                )
                Box(
                    Modifier
                        .offset(x = selectedOffset, y = (DOCK_HEIGHT - indicatorSize) / 2)
                        .size(indicatorSize)
                        .clip(CircleShape)
                        .background(if (dark) Color(0xFF24303B) else Color.White.copy(alpha = .99f)),
                )
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { t ->
                        DockItem(
                            tab = t,
                            selected = t == selected,
                            iconColor = if (dark) Color.White.copy(alpha = .94f) else blue,
                            onClick = { onSelect(t) },
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(DOCK_HEIGHT)
                .luminousElasticClickable(shape = CircleShape, maxStretchPercent = 0.08f, maxTranslateDp = 2.dp, onClick = onAdd)
                .clip(CircleShape)
                .glass(
                    CircleShape,
                    if (dark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f),
                    .92f,
                    shadowElevation = 18.dp,
                )
                .blueGlow(CircleShape)
                .clearAndSetSemantics { contentDescription = "New event" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(31.dp))
        }
    }
}

@Composable
private fun RowScope.DockItem(tab: Tab, selected: Boolean, iconColor: Color, onClick: () -> Unit) {
    var tapCount by remember { mutableIntStateOf(0) }
    val rotation = remember { Animatable(0f) }
    val selectedScale by animateFloatAsState(if (selected) 1f else .94f, tween(180), label = "dockScale")

    LaunchedEffect(tapCount) {
        if (tapCount == 0) return@LaunchedEffect
        rotation.snapTo(0f)
        when (tab) {
            // A page flip for the month, a shuffle for the list, a quarter-turn for the day.
            Tab.MONTH -> rotation.animateTo(360f, tween(420))
            Tab.AGENDA -> {
                rotation.animateTo(-6f, tween(65)); rotation.animateTo(6f, tween(85))
                rotation.animateTo(-3f, tween(65)); rotation.animateTo(0f, tween(65))
            }
            Tab.DAY -> rotation.animateTo(180f, tween(320))
            Tab.CLOCK -> rotation.animateTo(360f, tween(380))
        }
        rotation.snapTo(0f)
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(DOCK_HEIGHT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { tapCount++; onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) MaterialTheme.colorScheme.onSurface else iconColor,
            modifier = Modifier
                .size(27.dp)
                .graphicsLayer {
                    scaleX = selectedScale
                    scaleY = selectedScale
                    if (tab == Tab.MONTH) rotationY = rotation.value else rotationZ = rotation.value
                    cameraDistance = 18f * density
                },
        )
    }
}

val DOCK_HEIGHT = 72.dp
private val DOCK_WIDTH = 256.dp
private val FAB_GAP = 10.dp

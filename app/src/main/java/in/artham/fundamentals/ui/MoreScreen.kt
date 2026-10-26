package `in`.artham.fundamentals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class HubEntry(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val action: () -> Unit
)

@Composable
fun MoreScreen(
    onOpenWatchlist: () -> Unit,
    onOpenBacktest: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenData: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val entries = listOf(
        HubEntry("Watchlist", "Companies you track, with live ratio snapshots", Icons.Outlined.Star, onOpenWatchlist),
        HubEntry("Backtest", "Replay a screen year by year on point-in-time data", Icons.Outlined.Refresh, onOpenBacktest),
        HubEntry("Documents", "Search annual reports and transcripts on the device", Icons.Outlined.Search, onOpenDocuments),
        HubEntry("Compare", "Four companies side by side on twenty metrics", Icons.Outlined.List, onOpenCompare),
        HubEntry("Alerts", "Screen rules checked whenever you open the app", Icons.Outlined.Notifications, onOpenAlerts),
        HubEntry("Data", "Import spreadsheets, manage the dataset, read the activity log", Icons.Outlined.List, onOpenData),
        HubEntry("Models", "On-device model and local server configuration", Icons.Outlined.Person, onOpenModels),
        HubEntry("Settings", "Theme, generation settings, disclaimer", Icons.Outlined.Settings, onOpenSettings)
    )

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("Everything else", style = MaterialTheme.typography.displaySmall)
                Text(
                    "The tools that do not need a tab of their own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        entries.forEach { entry ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clickable { entry.action() }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                entry.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Artham is an educational research tool, not investment advice.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

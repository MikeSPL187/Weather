package app.aura.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aura.ui.theme.AuraColors
import app.aura.ui.theme.AuraType

/** Placeholder for tabs whose screens are designed but not built yet. */
@Composable
fun ComingSoonScreen(title: String, description: String, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = AuraType.Title, color = AuraColors.TextPrimary)
        Text(description, style = AuraType.Body, color = AuraColors.TextSecondary)
    }
}

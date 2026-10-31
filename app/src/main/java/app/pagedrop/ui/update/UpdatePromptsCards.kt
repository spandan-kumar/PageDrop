package app.pagedrop.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.pagedrop.ui.components.HairlineCard
import app.pagedrop.update.UpdatePrompts
import app.pagedrop.update.allowInstallsIntent

/** The update cards above the Library's search field. Call only in builds with BuildConfig.SELF_UPDATE. */
@Composable
internal fun UpdatePromptCards(viewModel: UpdatePromptsViewModel = hiltViewModel()) {
    val allowVersion by viewModel.allowCardVersion.collectAsState()
    val updatedTo by viewModel.updatedTo.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    allowVersion?.let { version ->
        HairlineCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PageDrop $version is available", style = MaterialTheme.typography.titleSmall)
                Text(UpdatePrompts.EXPLANATION, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { context.startActivity(allowInstallsIntent(context)) }) { Text("Allow updates") }
                    TextButton(onClick = { viewModel.notNow(version) }) { Text("Not now") }
                }
                TextButton(onClick = viewModel::turnOffAutomaticUpdates) {
                    Text("Turn off automatic updates", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    updatedTo?.let { version ->
        HairlineCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Updated to $version", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    runCatching { uriHandler.openUri(UpdatePrompts.releaseNotesUrl(version)) }
                    viewModel.dismissUpdatedTo()
                }) { Text("What's new") }
                TextButton(onClick = viewModel::dismissUpdatedTo) { Text("Dismiss") }
            }
        }
    }
}

package com.krafttools.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * One gate for every permission tool. Asked only when its tool opens,
 * with a plain-language reason first. States:
 * granted -> tool; first ask -> rationale card + grant button;
 * permanently denied -> explainer + system settings shortcut.
 * Nothing here touches the network, ever.
 */
@Composable
fun PermissionGate(
    permission: String,
    tool: String,
    reason: String,
    onBack: (() -> Unit)? = null,
    /** An alternative permission that also satisfies the need. For
     *  location this is COARSE: from Android 12 the user may grant
     *  "approximate", which denies FINE while granting COARSE, and an
     *  app that declares only FINE then locks the user out of a
     *  setting they have already granted. */
    alsoAccepts: String? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    // True when EITHER permission is held. For location this matters:
    // a user who picks "approximate" has granted something, and
    // telling them the permission is off in Settings when it is on is
    // both wrong and unactionable.
    fun holds(): Boolean {
        val primary = ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
        val alt = alsoAccepts != null &&
            ContextCompat.checkSelfPermission(
                context,
                alsoAccepts,
            ) == PackageManager.PERMISSION_GRANTED
        return primary || alt
    }
    var granted by remember { mutableStateOf(holds()) }
    // Request both, so the system chooser appears; a single FINE
    // request is ignored outright on some Android 12 releases.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        asked = true
        granted = result.values.any { it } || holds()
    }
    // Re-check on every resume: one-time grants expire and users can
    // revoke in Settings while away. A stale `granted=true` would show
    // the tool only to crash (or silently empty) on first API call.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = holds()
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val toRequest = remember(permission, alsoAccepts) {
        listOfNotNull(permission, alsoAccepts).distinct()
    }
    if (granted) {
        content()
        return
    }
    val activity = context as? Activity
    val blocked = asked && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    ToolScaffold(tool, onBack ?: {}) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(
                12.dp,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (blocked) {
                Text(
                    text = "It is turned off in system settings, so this " +
                        "tool stays locked until you allow it there.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { openAppSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Open settings")
                }
            } else {
                Button(
                    onClick = { launcher.launch(toRequest.toTypedArray()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Allow")
                }
            }
            // A gate is a door, not a wall. Without this, denying a
            // permission left the user with no way back to the tools
            // except the system gesture.
            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text("Not now")
                }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

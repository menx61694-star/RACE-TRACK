package com.racetrack.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun GoogleAuthScreen(
    profile: ProfileStore,
    workoutStore: WorkoutStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuthManager(context) }
    val sync = remember { FirestoreSyncManager() }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(auth.currentUser) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val clientId = remember {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id != 0) context.getString(id) else ""
    }

    LaunchedEffect(Unit) {
        user = auth.currentUser
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Account & Sync", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(
            if (user == null) "Sign in to back up your profile and workout history." else "Your Race Track account is connected.",
            color = Muted
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Card)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user != null) {
                    Text(user?.displayName ?: "Google account", color = Color.White)
                    Text(user?.email ?: "", color = Muted)
                } else {
                    Text("Google Sign-In", color = Color.White)
                    Text(
                        if (clientId.isBlank()) "Firebase Google Sign-In needs the updated google-services.json with OAuth client information." else "Use your Google account to enable cloud sync.",
                        color = Muted
                    )
                }
            }
        }

        if (user == null) {
            Button(
                enabled = clientId.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    status = "Opening Google sign-in…"
                    scope.launch {
                        auth.signInWithGoogle(clientId) { success, message ->
                            busy = false
                            status = message
                            user = auth.currentUser
                            if (success && user != null) {
                                sync.syncAll(profile, workoutStore) { syncSuccess, syncMessage ->
                                    status = if (syncSuccess) "Signed in and synced" else "Signed in, but sync failed: $syncMessage"
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(if (busy) "Signing in…" else "Continue with Google")
            }
        } else {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    status = "Syncing…"
                    sync.syncAll(profile, workoutStore) { success, message ->
                        busy = false
                        status = message
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(if (busy) "Syncing…" else "Sync now")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    auth.signOut()
                    user = null
                    status = "Signed out"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign out") }
        }

        if (status.isNotBlank()) Text(status, color = if (status.contains("failed", true)) Coral else Green)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

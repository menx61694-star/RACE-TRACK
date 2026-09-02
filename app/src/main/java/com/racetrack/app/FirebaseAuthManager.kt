package com.racetrack.app

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Phase 2 authentication boundary.
 *
 * Google sign-in is intentionally kept separate from the local profile/history
 * stores so Phase 1 remains usable when Firebase is not configured or offline.
 */
class FirebaseAuthManager(context: Context) {
    private val context = context
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(context)

    val isConfigured: Boolean
        get() = FirebaseApp.getApps(appContext).isNotEmpty()

    val currentUser
        get() = if (isConfigured) FirebaseAuth.getInstance().currentUser else null

    suspend fun signInWithGoogle(
        serverClientId: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (!isConfigured) {
            onResult(false, "Firebase is not configured yet")
            return
        }
        if (serverClientId.isBlank()) {
            onResult(false, "Google sign-in configuration is incomplete")
            return
        }

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                onResult(false, "Google account credential was not returned")
                return
            }

            val googleCredential = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (_: GoogleIdTokenParsingException) {
                onResult(false, "Could not read the Google sign-in credential")
                return
            }

            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onResult(true, "Signed in with Google")
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Google sign-in failed")
                    }
                }
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "Google sign-in was cancelled or failed")
        }
    }

    fun signOut() {
        if (isConfigured) FirebaseAuth.getInstance().signOut()
    }
}

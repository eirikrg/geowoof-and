package com.geowoof.core.ui

import android.app.Activity
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialException

/**
 * Offers to store an email and password in the user's password manager (Credential Manager).
 * Declining, or a device without a password provider, is not an error.
 */
suspend fun offerToSavePassword(activity: Activity, email: String, password: String) {
    try {
        CredentialManager.create(activity).createCredential(activity, CreatePasswordRequest(email, password))
    } catch (@Suppress("SwallowedException") exception: CreateCredentialException) {
        Log.i(TAG, "Password not saved: ${exception.type}")
    }
}

private const val TAG = "PasswordCredentials"

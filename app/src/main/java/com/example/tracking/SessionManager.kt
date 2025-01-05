package com.example.tracking

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = sharedPreferences.edit()

    companion object {
        private const val PREF_NAME = "UserSession"
        private const val KEY_USER_ID = "UserId"
    }

    /**
     * Save the user's ID in the session.
     *
     * @param userId The unique ID of the user fetched from Firestore.
     */
    fun setUserId(userId: String) {
        editor.putString(KEY_USER_ID, userId)
        editor.apply()
    }

    /**
     * Retrieve the logged-in user's ID.
     *
     * @return The user's ID or null if not available.
     */
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }

    /**
     * Check if a user is logged in based on the presence of User ID.
     *
     * @return True if User ID exists, otherwise false.
     */
    fun isLoggedIn(): Boolean {
        return getUserId() != null
    }

    /**
     * Clear the session and logout the user.
     */
    fun logout() {
        editor.clear()
        editor.apply()
    }
}

/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package andihasan7.kartikaide.common

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

object Analytics {

    private var isAnalyticsCollectionEnabled = true
    private var firebaseAnalytics: Any? = null

    @JvmStatic
    fun init(context: Context) {
        try {
            FirebaseApp.initializeApp(context)
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logEvent(event: String, value: Any) {
        if (!isAnalyticsCollectionEnabled) return
        Log.d("Analytics", "Logging event: $event")

        try {
            val bundle = Bundle().apply {
                putString("value", value.toString())
            }
            (firebaseAnalytics as? FirebaseAnalytics)?.logEvent(event, bundle)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logEvent(event: String, bundle: Bundle) {
        if (!isAnalyticsCollectionEnabled) return
        Log.d("Analytics", "Logging event: $event")

        try {
            (firebaseAnalytics as? FirebaseAnalytics)?.logEvent(event, bundle)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun logEvent(event: String, vararg pairs: Pair<String, String>) {
        if (!isAnalyticsCollectionEnabled) return

        try {
            val bundle = Bundle()
            for (pair in pairs) {
                bundle.putString(pair.first, pair.second)
            }
            logEvent(event, bundle)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        isAnalyticsCollectionEnabled = enabled
        try {
            (firebaseAnalytics as? FirebaseAnalytics)?.setAnalyticsCollectionEnabled(enabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package io.soult.baromi.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationsTest {

    private lateinit var app: Application
    private lateinit var nm: NotificationManager

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        nm = app.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun posting_creates_channel_and_notification() {
        Notifications.postCheckinPrompt(app, promptTime = 123L)

        assertNotNull(
            "reminder channel must exist",
            nm.getNotificationChannel(Notifications.CHANNEL_ID),
        )
        assertEquals(1, shadowOf(nm).size())
    }

    @Test
    fun notification_has_the_checkin_prompt_title() {
        Notifications.postCheckinPrompt(app, promptTime = 123L)
        val posted = shadowOf(nm).getNotification(Notifications.NOTIFICATION_ID)
        assertEquals("How are you right now?", posted.extras.getString(Notification.EXTRA_TITLE))
    }
}

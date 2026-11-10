package ir.guardianlink.child

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * تست اجباری «Child = صفر Notification»:
 * سورس‌های app-child و ماژول‌های وابسته‌اش نباید هیچ API سازنده notification داشته باشند.
 */
class ZeroNotificationTest {
    private val forbidden = listOf(
        "NotificationManager",
        "startForeground",
        "POST_NOTIFICATIONS",
        "androidx.core.app.NotificationCompat",
        "FOREGROUND_SERVICE",
    )

    private fun sources(): List<File> {
        val roots = listOf(
            File("src/main"),
            File("../core/common/src/main"),
            File("../core/storage/src/main"),
            File("../core/network/src/main"),
            File("../core/designsystem/src/main"),
            File("../feature/pairing/src/main"),
            File("../feature/childstatus/src/main"),
        )
        return roots.filter { it.exists() }.flatMap { r ->
            r.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()
        }
    }

    @Test
    fun childNeverCreatesNotifications() {
        val files = sources()
        assertTrue("source roots not found (run from app-child module dir)", files.isNotEmpty())
        val violations = mutableListOf<String>()
        for (f in files) {
            val text = f.readText()
            for (b in forbidden) {
                if (text.contains(b)) violations.add("${f.path}: $b")
            }
        }
        assertTrue("zero-notification violations:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}

package com.einkphoto.app.feature.settings.appupdate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun updateUrlsAllowHttpsAndOnlyTheKnownHttpRepository() {
        assertTrue(isTrustedUpdateUrl("https://updates.example.com/stable.json"))
        assertTrue(isTrustedUpdateUrl("http://107.173.157.41:3000/yj/E-ink-APP/raw/branch/main/release-manifest/stable.json"))
        assertTrue(isTrustedUpdateUrl("http://107.173.157.41:3000/yj/E-ink-APP/releases/download/v2.0.1/eink-photo-v2.0.1.apk"))
        assertFalse(isTrustedUpdateUrl("http://107.173.157.41:3000/other/release.apk"))
        assertFalse(isTrustedUpdateUrl("http://example.com/yj/E-ink-APP/releases/download/v2.0.1/eink-photo-v2.0.1.apk"))
    }
}

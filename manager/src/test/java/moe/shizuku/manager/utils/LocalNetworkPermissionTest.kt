package moe.shizuku.manager.utils

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkPermissionTest {

    @Test
    fun `Android 16 uses nearby Wi-Fi devices`() {
        assertEquals(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            LocalNetworkPermission.permissionForSdk(36),
        )
        assertTrue(LocalNetworkPermission.isRequired(36))
    }

    @Test
    fun `Android 17 uses local network access`() {
        assertEquals(
            LocalNetworkPermission.ACCESS_LOCAL_NETWORK,
            LocalNetworkPermission.permissionForSdk(37),
        )
        assertTrue(LocalNetworkPermission.isRequired(37))
    }

    @Test
    fun `older Android releases do not require the permission`() {
        assertNull(LocalNetworkPermission.permissionForSdk(35))
        assertFalse(LocalNetworkPermission.isRequired(35))
    }
}

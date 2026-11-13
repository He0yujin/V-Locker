package com.example.Vlocker

import com.example.Vlocker.vehicle.PleosPermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosPermissionHelperTest {

    @Test
    fun `all required pleos permissions granted returns empty missing list`() {
        val missing = PleosPermissionHelper.findMissingPermissions { true }
        assertTrue(missing.isEmpty())
        assertTrue(PleosPermissionHelper.canReadBatteryTelemetry { true })
        assertTrue(PleosPermissionHelper.canControlHvacHeater { true })
    }

    @Test
    fun `missing control climate blocks hvac control while keeping battery telemetry`() {
        val granted = setOf(
            PleosPermissionHelper.PERMISSION_VEHICLE_BINDING,
            PleosPermissionHelper.PERMISSION_CAR_ENERGY,
            PleosPermissionHelper.PERMISSION_CAR_CLIMATE
        )
        val missing = PleosPermissionHelper.findMissingPermissions { it in granted }
        assertEquals(listOf(PleosPermissionHelper.PERMISSION_CONTROL_CAR_CLIMATE), missing)
        assertTrue(PleosPermissionHelper.canReadBatteryTelemetry { it in granted })
        assertFalse(PleosPermissionHelper.canControlHvacHeater { it in granted })
    }

    @Test
    fun `least privilege excludes unused CONTROL_CAR_ENERGY permission`() {
        assertFalse(
            PleosPermissionHelper.REQUIRED_SDK_PERMISSIONS.contains("pleos.car.permission.CONTROL_CAR_ENERGY")
        )
    }
}


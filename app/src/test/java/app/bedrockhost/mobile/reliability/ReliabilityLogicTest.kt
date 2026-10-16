package app.bedrockhost.mobile.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityLogicTest {
    @Test
    fun classifiesAddresses() {
        assertEquals(AddressKind.LAN, AddressClassifier.classify("192.168.1.5"))
        assertEquals(AddressKind.LAN, AddressClassifier.classify("10.0.0.2"))
        assertEquals(AddressKind.LAN, AddressClassifier.classify("172.16.5.1"))
        assertEquals(AddressKind.PUBLIC, AddressClassifier.classify("172.32.0.1"))
        assertEquals(AddressKind.TAILSCALE_OR_CGNAT, AddressClassifier.classify("100.100.50.2"))
        assertEquals(AddressKind.PUBLIC, AddressClassifier.classify("100.128.0.1"))
        assertEquals(AddressKind.IGNORED, AddressClassifier.classify("127.0.0.1"))
        assertEquals(AddressKind.IGNORED, AddressClassifier.classify("169.254.1.1"))
        assertEquals(AddressKind.IGNORED, AddressClassifier.classify("abc"))
        assertEquals(AddressKind.IGNORED, AddressClassifier.classify("1.2.3"))
        assertEquals(AddressKind.IGNORED, AddressClassifier.classify("256.1.1.1"))
    }

    @Test
    fun mapsThermalStatus() {
        assertEquals(ThermalLevel.UNKNOWN, HealthAdvisor.thermalLevel(null))
        assertEquals(ThermalLevel.NORMAL, HealthAdvisor.thermalLevel(0))
        assertEquals(ThermalLevel.WARM, HealthAdvisor.thermalLevel(2))
        assertEquals(ThermalLevel.HOT, HealthAdvisor.thermalLevel(3))
        assertEquals(ThermalLevel.CRITICAL, HealthAdvisor.thermalLevel(5))
        assertEquals(ThermalLevel.UNKNOWN, HealthAdvisor.thermalLevel(9))
    }

    @Test
    fun producesWarningsOnlyWhenNeeded() {
        assertTrue(HealthAdvisor.warnings(DeviceHealth(ThermalLevel.NORMAL, 2000, false, true)).isEmpty())
        assertEquals(3, HealthAdvisor.warnings(DeviceHealth(ThermalLevel.HOT, 150, true, false)).size)
    }
}

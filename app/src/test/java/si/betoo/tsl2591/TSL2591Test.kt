package si.betoo.tsl2591

import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import com.google.android.things.pio.I2cDevice
import kotlin.experimental.or

@RunWith(MockitoJUnitRunner::class)
class TSL2591Test {

    @Mock
    private lateinit var device: I2cDevice

    private lateinit var driver: TSL2591

    @Before
    fun setUp() {
        driver = TSL2591(device)
    }

    @Test
    fun checkForConnectionOnInit() {
        verify(device).readRegByte(0x00)
    }

    @Test
    fun itPowersOn() {
        driver.powerOn()
        verify(device).writeRegByte(TSL2591.TSL2591_COMMAND_BIT or TSL2591.TSL2591_REGISTER_ENABLE, TSL2591.TSL2591_ENABLE_POWERON or TSL2591.TSL2591_ENABLE_AEN or TSL2591.TSL2591_ENABLE_AIEN)
    }

    @Test
    fun itPowersOff() {
        driver.powerOff()
        verify(device).writeRegByte(TSL2591.TSL2591_COMMAND_BIT or TSL2591.TSL2591_REGISTER_ENABLE, TSL2591.TSL2591_ENABLE_POWEROFF)
    }

    @Test
    fun itSetupsTheDeviceWhenPoweredOn()
    {
        driver.powerOn()
        verify(device).writeRegByte(TSL2591.TSL2591_COMMAND_BIT or TSL2591.TSL2591_REGISTER_CONTROL, driver.time or driver.gain)
    }

    @Test
    fun itChangesGainValueOnTheDevice() {
        driver.gain = TSL2591.TSL2591_GAIN_MAX
        verify(device).writeRegByte(TSL2591.TSL2591_COMMAND_BIT or TSL2591.TSL2591_REGISTER_CONTROL, driver.gain or driver.time)
        assertSame(TSL2591.TSL2591_GAIN_MAX, driver.gain)
    }

    @Test(expected = IllegalArgumentException::class)
    fun itThrowsAnExceptionIfInvalidGain() {
        driver.gain = 0x40
    }

    @Test
    fun itChangesTimeValueOnTheDevice() {
        driver.gain = TSL2591.TSL2591_GAIN_MAX
        verify(device).writeRegByte(TSL2591.TSL2591_COMMAND_BIT or TSL2591.TSL2591_REGISTER_CONTROL, driver.gain or driver.time)
        assertSame(TSL2591.TSL2591_GAIN_MAX, driver.gain)
    }

    @Test(expected = IllegalArgumentException::class)
    fun itThrowsAnExceptionIfInvalidTime() {
        driver.time = 0x06
    }
}

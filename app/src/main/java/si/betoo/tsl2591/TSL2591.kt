package si.betoo.tsl2591

import android.os.Handler
import android.os.HandlerThread
import com.google.android.things.pio.I2cDevice
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.Closeable
import kotlin.experimental.or

class TSL2591(private var device: I2cDevice) : Closeable {

    private val observable: Subject<Int> = PublishSubject.create<Int>()

    private var isThreadRunning = false

    private var runnable: Runnable? = null
    private var handler: Handler? = null

    var gain: Byte = TSL2591_GAIN_MEDIUM
        set(value) {
            when (value) {
                TSL2591_GAIN_LOW, TSL2591_GAIN_MEDIUM, TSL2591_GAIN_HIGH, TSL2591_GAIN_MAX -> {
                    field = value
                    writeSettingsToDevice()
                }
                else -> throw IllegalArgumentException("$value is not a proper gain value! Please check the driver's documentation.")
            }
        }

    var time: Byte = TSL2591_INTEGRATIONTIME_100MS
        set(value) {
            when (value) {
                TSL2591_INTEGRATIONTIME_100MS, TSL2591_INTEGRATIONTIME_200MS, TSL2591_INTEGRATIONTIME_300MS, TSL2591_INTEGRATIONTIME_400MS, TSL2591_INTEGRATIONTIME_500MS, TSL2591_INTEGRATIONTIME_600MS -> {
                    field = value
                    writeSettingsToDevice()
                }
                else -> throw IllegalArgumentException("$value is not a proper time value! Please check the driver's documentation.")
            }
        }

    init {
        checkConnection()
    }

    fun powerOn() {
        device.writeRegByte(TSL2591_COMMAND_BIT or TSL2591_REGISTER_ENABLE, TSL2591_ENABLE_POWERON or TSL2591_ENABLE_AEN or TSL2591_ENABLE_AIEN)
        writeSettingsToDevice()
    }

    fun powerOff() {
        isThreadRunning = false
        device.writeRegByte(TSL2591_COMMAND_BIT or TSL2591_REGISTER_ENABLE, TSL2591_ENABLE_POWEROFF)
    }

    fun getLux(): Observable<Float> {
        return getFullLuminosity().map { fullLuminosity ->
            calculateLux(fullLuminosity)
        }.share()
    }

    override fun close() {
        device.close()
    }

    private fun calculateLux(fullLuminosity: Int): Float {
        val fullSpectrumLuminosity = getLuminosity(TSL2591_FULLSPECTRUM, fullLuminosity)
        val infraredLuminosity = getLuminosity(TSL2591_INFRARED, fullLuminosity)

        //  Check for overflow conditions
        if ((fullSpectrumLuminosity == 0xFFFF) or (infraredLuminosity == 0xFFFF)) {
            return 0.0F
        }

        val cpl = (getTimeInMS(time).toFloat() * getGainMultiplier(gain)) / TSL2591_LUX_DF
        val lux = (fullSpectrumLuminosity.toFloat() - infraredLuminosity.toFloat()) * (1.0f - infraredLuminosity.toFloat() / fullSpectrumLuminosity.toFloat()) / cpl

        return lux
    }

    private fun getGainMultiplier(gain: Byte): Float {
        return when (gain) {
            TSL2591_GAIN_LOW -> return 1f
            TSL2591_GAIN_MEDIUM -> return 25f
            TSL2591_GAIN_HIGH -> return 428f
            TSL2591_GAIN_MAX -> return 9876f
            else -> 1f
        }
    }

    private fun startBackgroundThread(observable: Subject<Int>) {
        if (handler == null) {
            val handlerThread = HandlerThread("TSL2591")
            handlerThread.start()

            handler = Handler(handlerThread.looper)
        }

        if (runnable == null) {
            runnable = Runnable {
                if (isThreadRunning) {
                    val fullLuminosity = readFullLuminosity()

                    observable.onNext(fullLuminosity)

                    handler!!.postDelayed(runnable, getTimeInMS(time))
                }
            }
        }

        handler!!.postDelayed(runnable, getTimeInMS(time))
    }

    private fun readFullLuminosity(): Int {
        var x: Int = device.readRegWord((TSL2591_COMMAND_BIT or TSL2591_REGISTER_CHAN1_LOW)).toInt()
        var y: Short = 0
        y = y or device.readRegWord(TSL2591_COMMAND_BIT or TSL2591_REGISTER_CHAN0_LOW)
        x = (x shl 16)
        x = (x or y.toInt())

        return x
    }

    private fun getFullLuminosity(): Subject<Int> {

        if (!isThreadRunning) {
            isThreadRunning = true
            startBackgroundThread(observable)
        }

        observable.share()

        return observable
    }

    private fun getLuminosity(channel: Int, fullLuminosity: Int): Int {
        return when (channel) {
            // Reads two byte value from channel 0 (visible + infrared)
            TSL2591_FULLSPECTRUM -> fullLuminosity and 0xFFFF

            // Reads two byte value from channel 1 (infrared)
            TSL2591_INFRARED -> fullLuminosity shr 16

            // Reads all and subtracts out just the visible!
            TSL2591_VISIBLE -> (fullLuminosity and 0xFFFF) - (fullLuminosity shr 16)

            else -> 0
        }
    }

    private fun getTimeInMS(time: Byte): Long {
        return when (time) {
            TSL2591_INTEGRATIONTIME_100MS -> return 100
            TSL2591_INTEGRATIONTIME_200MS -> return 200
            TSL2591_INTEGRATIONTIME_300MS -> return 300
            TSL2591_INTEGRATIONTIME_400MS -> return 400
            TSL2591_INTEGRATIONTIME_500MS -> return 500
            TSL2591_INTEGRATIONTIME_600MS -> return 600
            else -> 100
        }
    }

    private fun writeSettingsToDevice() {
        device.writeRegByte((TSL2591_COMMAND_BIT or TSL2591_REGISTER_CONTROL), time or gain)
    }

    private fun checkConnection() {
        device.readRegByte(TSL2591_REGISTER_ENABLE)
    }

    companion object {
        const val TSL2591_COMMAND_BIT = 0xA0

        const val TSL2591_VISIBLE = 2
        const val TSL2591_INFRARED = 1
        const val TSL2591_FULLSPECTRUM = 0

        const val TSL2591_LUX_DF = (408.0F) /// Lux coefficient

        const val TSL2591_REGISTER_ENABLE = 0x00 // Enable register
        const val TSL2591_REGISTER_CONTROL = 0x01 // Control register

        const val TSL2591_ENABLE_POWEROFF: Byte = 0x00  /// Flag for ENABLE register to disable
        const val TSL2591_ENABLE_POWERON: Byte = 0x01    /// Flag for ENABLE register to enable

        const val TSL2591_ENABLE_AEN: Byte = 0x02   /// ALS Enable. This field activates ALS function. Writing a one activates the ALS. Writing a zero disables the ALS.
        const val TSL2591_ENABLE_AIEN: Byte = 0x10    /// ALS Interrupt Enable. When asserted permits ALS interrupts to be generated, subject to the persist filter.

        const val TSL2591_INTEGRATIONTIME_100MS: Byte = 0x00  // 100 millis
        const val TSL2591_INTEGRATIONTIME_200MS: Byte = 0x01  // 200 millis
        const val TSL2591_INTEGRATIONTIME_300MS: Byte = 0x02  // 300 millis
        const val TSL2591_INTEGRATIONTIME_400MS: Byte = 0x03  // 400 millis
        const val TSL2591_INTEGRATIONTIME_500MS: Byte = 0x04  // 500 millis
        const val TSL2591_INTEGRATIONTIME_600MS: Byte = 0x05  // 600 millis

        const val TSL2591_GAIN_LOW: Byte = 0x00    /// low gain (1x)
        const val TSL2591_GAIN_MEDIUM: Byte = 0x10    /// medium gain (25x)
        const val TSL2591_GAIN_HIGH: Byte = 0x20    /// medium gain (428x)
        const val TSL2591_GAIN_MAX: Byte = 0x30   /// max gain (9876x)

        const val TSL2591_REGISTER_CHAN0_LOW = 0x14 // Channel 0 data low byte
        const val TSL2591_REGISTER_CHAN0_HIGH = 0x15 // Channel 0 data high byte
        const val TSL2591_REGISTER_CHAN1_LOW = 0x16 // Channel 1 data low byte
        const val TSL2591_REGISTER_CHAN1_HIGH = 0x17 // Channel 1 data high byte
    }
}
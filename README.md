@@ -0,0 +1,33 @@
# TSL2591 driver for Android things

This driver supports Adafruit [TSL2591](https://www.adafruit.com/product/1980) light sensor. It is based on the [TSL2591 Arduino library](https://github.com/adafruit/Adafruit_TSL2591_Library), but modified for Android Things and Kotlin.

NOTE: the driver is not production ready. There is no guarantee of correctness, completeness or robustness.

## How to use

### Sample usage



    //  Connecting to the sensor
    //  NOTE: the sensor is always at address 0x29
    val device = PeripheralManager.getInstance().openI2cDevice("I2C1", 0x29);
    val sensor = TSL2591(device)

    //  For low light conditions you can set the integration time as well as gain
    sensor.time = TSL.TSL2591_INTEGRATIONTIME_400MS
    sensor.gain = TSL.TSL2591_GAIN_HIGH

    //  Before using, remember to power on the sensor, because it starts off unpowered
    sensor.powerOn()

    //  To receive lux readings, just subscribe to the observable
    //  NOTE: the first few values might not be the most accurate
    sensor.getLux().subscribe { lux -> Log.d("TSL2591", "Lux:$lux") }

    //  Once you complete your readings, you have to power off the device and close the connection
    sensor.powerOff()
    sensor.close()
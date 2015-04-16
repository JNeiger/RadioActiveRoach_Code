FRC Driver Station
========

Displays camera and arbitrary information of the robot to the glass. 


Uses adb to forward a single port over the USB to allow for network TCP communication between the Glass and the Driverstation

## Running the sample on Glass

You can use your IDE to compile and install the sample or use
[`adb`](https://developer.android.com/tools/help/adb.html)
on the command line:

    $ adb install -r WaveformSample.apk

To start the sample, say "ok glass, start driver station" from the Glass clock
screen or use the touch menu.

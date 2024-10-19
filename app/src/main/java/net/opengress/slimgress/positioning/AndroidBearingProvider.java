package net.opengress.slimgress.positioning;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

public class AndroidBearingProvider implements BearingProvider, SensorEventListener {
    private Context context;
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private BearingCallback bearingCallback;

    public AndroidBearingProvider(Context context) {
        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationVectorSensor == null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        }
    }

    @Override
    public void startBearingUpdates() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    public void stopBearingUpdates() {
        if (rotationVectorSensor != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void setBearingCallback(BearingCallback callback) {
        this.bearingCallback = callback;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR || event.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Adjust rotation matrix based on the device's orientation
            float[] adjustedRotationMatrix = new float[9];
            int axisX = SensorManager.AXIS_X;
            int axisY = SensorManager.AXIS_Y;

            // Get the device's rotation
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int rotation = windowManager.getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                    axisX = SensorManager.AXIS_Y;
                    axisY = SensorManager.AXIS_MINUS_X;
                    break;
                case Surface.ROTATION_180:
                    axisX = SensorManager.AXIS_MINUS_X;
                    axisY = SensorManager.AXIS_MINUS_Y;
                    break;
                case Surface.ROTATION_270:
                    axisX = SensorManager.AXIS_MINUS_Y;
                    axisY = SensorManager.AXIS_X;
                    break;
                default:
                    break;
            }

            // Remap coordinate system
            SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedRotationMatrix);

            // Get orientation
            float[] orientation = new float[3];
            SensorManager.getOrientation(adjustedRotationMatrix, orientation);

            double azimuth = Math.toDegrees(orientation[0]);
            int compassBearing = (int) ((360 - azimuth + 360) % 360);

            if (bearingCallback != null) {
                bearingCallback.onBearingUpdated(compassBearing);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Implement if needed
    }
}

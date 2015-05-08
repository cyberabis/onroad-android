package io.logbase.onroad.models;

/**
 * Created by abishek on 08/05/15.
 */
public class OrientationEvent extends Event {

    private float azimuthAngle;
    private float pitchAngle;
    private float rollAngle;

    public OrientationEvent(String type, Long timestamp, String userId, String tripName, float azimuthAngle, float pitchAngle, float rollAngle) {
        super(type, timestamp, userId, tripName);
        this.azimuthAngle = azimuthAngle;
        this.pitchAngle = pitchAngle;
        this.rollAngle = rollAngle;
    }

    public float getAzimuthAngle() {
        return azimuthAngle;
    }

    public void setAzimuthAngle(float azimuthAngle) {
        this.azimuthAngle = azimuthAngle;
    }

    public float getPitchAngle() {
        return pitchAngle;
    }

    public void setPitchAngle(float pitchAngle) {
        this.pitchAngle = pitchAngle;
    }

    public float getRollAngle() {
        return rollAngle;
    }

    public void setRollAngle(float rollAngle) {
        this.rollAngle = rollAngle;
    }
}

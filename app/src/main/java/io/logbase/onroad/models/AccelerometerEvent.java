package io.logbase.onroad.models;

/**
 * Created by abishek on 02/05/15.
 */
public class AccelerometerEvent extends Event {

    public AccelerometerEvent(String type, Long timestamp, String userId, String tripName, double x, double y, double z) {
        super(type, timestamp, userId, tripName);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private double x;
    private double y;
    private double z;

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

}

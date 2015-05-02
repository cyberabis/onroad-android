package io.logbase.onroad.models;

/**
 * Created by abishek on 02/05/15.
 */
public class LocationEvent extends Event {

    private Double latitude;
    private Double longitude;
    private Double speed;

    public LocationEvent(String type, Long timestamp, String userId, String tripName, Double latitude, Double longitude, Double speed) {
        super(type, timestamp, userId, tripName);
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

}

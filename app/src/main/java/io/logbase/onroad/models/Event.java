package io.logbase.onroad.models;

/**
 * Created by abishek on 02/05/15.
 */
public class Event {

    private String type;
    private Long timestamp;
    private String userId;
    private String tripName;

    public Event(String type, Long timestamp, String userId, String tripName) {
        this.type = type;
        this.timestamp = timestamp;
        this.userId = userId;
        this.tripName = tripName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTripName() {
        return tripName;
    }

    public void setTripName(String tripName) {
        this.tripName = tripName;
    }

}

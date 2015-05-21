package io.logbase.onroad.models;

/**
 * Created by abishek on 21/05/15.
 */
public class TrainingEvent extends Event {

    private String label;

    public TrainingEvent(String type, Long timestamp, String userId, String tripName, String label) {
        super(type, timestamp, userId, tripName);
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

}

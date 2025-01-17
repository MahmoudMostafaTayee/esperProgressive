package com.espertech.esper.example.IOT.PersonView;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PersonView {
    private int personID;
    private int positionID;
    private List<View> views;

    // Default constructor
    public PersonView() {
    }

    // Constructor with annotations
    @JsonCreator
    public PersonView(
            @JsonProperty("personID") int personID,
            @JsonProperty("positionID") int positionID,
            @JsonProperty("views") List<View> views) {
        this.personID = personID;
        this.positionID = positionID;
        this.views = views;
    }

    // Getters
    public int getPersonID() {
        return personID;
    }

    public int getPositionID() {
        return positionID;
    }

    public List<View> getViews() {
        return views;
    }

    // Inner class for View
    public static class View {
        private int viewNum;
        private int xmax;
        private int xmin;
        private int ymax;
        private int ymin;

        // Constructor with annotations
        @JsonCreator
        public View(
                @JsonProperty("viewNum") int viewNum,
                @JsonProperty("xmax") int xmax,
                @JsonProperty("xmin") int xmin,
                @JsonProperty("ymax") int ymax,
                @JsonProperty("ymin") int ymin) {
            this.viewNum = viewNum;
            this.xmax = xmax;
            this.xmin = xmin;
            this.ymax = ymax;
            this.ymin = ymin;
        }

        // Getters
        public int getViewNum() {
            return viewNum;
        }

        public int getXmax() {
            return xmax;
        }

        public int getXmin() {
            return xmin;
        }

        public int getYmax() {
            return ymax;
        }

        public int getYmin() {
            return ymin;
        }

        // Override toString for meaningful output
        @Override
        public String toString() {
            return "View{" +
                    "viewNum=" + viewNum +
                    ", xmax=" + xmax +
                    ", xmin=" + xmin +
                    ", ymax=" + ymax +
                    ", ymin=" + ymin +
                    '}';
        }
    }
}

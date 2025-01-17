package com.espertech.esper.example.IOT.PersonView;

import java.util.List;

public class PersonView {
    private int personID;
    private int positionID;
    private List<View> views;

    // Constructor
    public PersonView(int personID, int positionID, List<View> views) {
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
    
        // Constructor
        public View(int viewNum, int xmax, int xmin, int ymax, int ymin) {
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

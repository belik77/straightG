package com.plutus.straightg;


public class AccData {
    public float x,y,z;

    public AccData(float x,float y,float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public String toString() {
        return "AccData{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }

    public float sum() {
        return x+y+z;
    }
}

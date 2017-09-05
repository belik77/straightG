package com.plutus.straightg;

public class AccResult {

    float deltaX;
    float deltaY;
    float deltaZ;

    float avgX;
    float avgY;
    float avgZ;

    float sumX;
    float sumY;
    float sumZ;

    float medianX;
    float medianY;
    float medianZ;

    public AccResult() {

    }

    @Override
    public String toString() {
        return String.format("dX=%.2f, dY=%.2f, dZ=%.2f,aX=%.2f, aY=%.2f, aZ=%.2f, sX=%.2f, sY=%.2f, sZ=%.2f,mX=%.2f, mY=%.2f, mZ=%.2f "
        ,deltaX,deltaY,deltaZ,avgX,avgY,avgZ,sumX,sumY,sumZ,medianX,medianY,medianZ);
    }
}

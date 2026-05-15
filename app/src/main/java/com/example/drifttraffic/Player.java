package com.example.drifttraffic;

public class Player {
    public float x, y;
    public float width = 40;
    public float height = 60;
    public float angle = 0;
    public float targetAngle = 0;
    public float driftPower = 0;
    public float lateralSpeed = 0;
    public float autoDriftDirection = 0;
    public float autoDriftTimer = 0;
    public float opacity = 1;
    public float shakeX = 0;
    public float shakeY = 0;

    public Player(float startX, float startY) {
        this.x = startX;
        this.y = startY;
    }

    public void reset(float startX, float startY) {
        this.x = startX;
        this.y = startY;
        this.angle = 0;
        this.targetAngle = 0;
        this.driftPower = 0;
        this.lateralSpeed = 0;
        this.autoDriftDirection = 0;
        this.autoDriftTimer = 0;
        this.opacity = 1;
        this.shakeX = 0;
        this.shakeY = 0;
    }

    public float getMinX() {
        return 10;
    }

    public float getMaxX(float canvasWidth) {
        return canvasWidth - width - 10;
    }

    public void clampX(float canvasWidth) {
        x = Math.max(getMinX(), Math.min(getMaxX(canvasWidth), x));
    }
}
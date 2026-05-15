package com.example.drifttraffic;

public class Enemy {
    public float x, y;
    public float width, height;
    public int color;
    public float speed;

    public Enemy(float x, float y, float width, float height, int color, float speed) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
        this.speed = speed;
    }
}
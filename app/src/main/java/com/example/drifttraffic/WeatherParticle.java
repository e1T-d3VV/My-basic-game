package com.example.drifttraffic;

import java.util.Random;

public class WeatherParticle {
    public float x, y;
    public float speed;
    public float wind;
    public float size;
    public float opacity;
    public float wobble;
    public int type; // 0: rain, 1: snow

    private static Random random = new Random();

    public WeatherParticle(float canvasWidth, float canvasHeight, int type) {
        this.type = type;
        this.x = random.nextFloat() * canvasWidth;
        this.y = random.nextFloat() * canvasHeight - 20;
        this.wobble = random.nextFloat() * (float) Math.PI * 2;

        if (type == 0) { // rain
            this.speed = 8 + random.nextFloat() * 12;
            this.wind = 0.5f + random.nextFloat() * 1.5f;
            this.size = 1 + random.nextFloat() * 2;
        } else { // snow
            this.speed = 1 + random.nextFloat() * 2;
            this.wind = -0.5f + random.nextFloat();
            this.size = 2 + random.nextFloat() * 3;
        }
        this.opacity = 0.3f + random.nextFloat() * 0.7f;
    }

    public void update(int currentWeather, float canvasWidth, float canvasHeight, float frameCounter) {
        if (currentWeather == 0) { // rainy
            y += speed;
            x += wind;
            x += Math.sin(frameCounter * 0.02 + wobble) * 0.3;
        } else if (currentWeather == 1) { // snowy
            y += speed;
            x += Math.sin(frameCounter * 0.03 + wobble) * 0.8;
            wobble += 0.02;
        }

        if (y > canvasHeight + 10 || x < -10 || x > canvasWidth + 10) {
            y = -10;
            x = random.nextFloat() * canvasWidth;
        }
    }
}
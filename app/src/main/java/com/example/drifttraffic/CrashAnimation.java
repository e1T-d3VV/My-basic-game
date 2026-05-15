package com.example.drifttraffic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrashAnimation {
    public float x, y;
    public float shockwave = 0;
    public int timer = 0;
    public int duration = 60;
    public List<CrashParticle> particles;
    public boolean active = false;

    private static Random random = new Random();
    private static int[] colors = {
        0xFFFF4444, 0xFFFF8800, 0xFFFFCC00, 0xFFFFFFFF, 0xFFFF6600
    };

    public CrashAnimation(float x, float y) {
        this.x = x;
        this.y = y;
        this.particles = new ArrayList<>();
        initParticles();
        this.active = true;
    }

    private void initParticles() {
        for (int i = 0; i < 40; i++) {
            double angle = random.nextFloat() * Math.PI * 2;
            float speed = 3 + random.nextFloat() * 8;
            CrashParticle p = new CrashParticle();
            p.x = x;
            p.y = y;
            p.vx = (float) (Math.cos(angle) * speed);
            p.vy = (float) (Math.sin(angle) * speed);
            p.size = 2 + random.nextFloat() * 4;
            p.color = colors[random.nextInt(colors.length)];
            p.life = 1;
            p.decay = 0.01f + random.nextFloat() * 0.03f;
            p.gravity = 0.1f + random.nextFloat() * 0.2f;
            particles.add(p);
        }
    }

    public void update() {
        if (!active) return;
        
        timer++;
        shockwave += 2;

        for (CrashParticle p : particles) {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += p.gravity;
            p.life -= p.decay;
            p.size *= 0.99;
        }

        if (timer > duration) {
            active = false;
        }
    }

    public static class CrashParticle {
        public float x, y;
        public float vx, vy;
        public float size;
        public int color;
        public float life;
        public float decay;
        public float gravity;
    }
}
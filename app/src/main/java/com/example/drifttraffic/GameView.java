package com.example.drifttraffic;

import android.content.Context;
import android.graphics.*;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends View {
    private Paint paint;
    private Paint textPaint;
    private Paint roadPaint;
    private Paint carPaint;
    private Paint particlePaint;
    
    private Player player;
    private List<Enemy> enemies;
    private List<WeatherParticle> weatherParticles;
    private CrashAnimation crashAnimation;
    
    private Random random;
    
    // Oyun durumu
    private boolean gameOver = false;
    private int score = 0;
    private float frameCounter = 0;
    private int spawnRate = 55;
    private float driftCombo = 0;
    private int driftScore = 0;
    private float roadOffset = 0;
    
    // Hava durumu
    private int currentWeather = 0; // 0: sunny, 1: rainy, 2: snowy
    private float weatherTimer = 0;
    private float weatherChangeInterval;
    private String weatherEmoji = "☀️";
    
    // Kontroller
    private boolean leftPressed = false;
    private boolean rightPressed = false;
    private boolean driftPressed = false;
    private float touchStartX = -1;
    private boolean touchActive = false;
    private float touchCurrentX;
    private boolean touchMoved = false;
    
    // Canvas boyutları
    private float canvasWidth;
    private float canvasHeight;
    
    // Renkler
    private int[] enemyColors = {
        0xFFE15554, 0xFFF9A03F, 0xFF4C9F70, 0xFF3B7CA9, 0xFFB185DB
    };
    
    // Oyun döngüsü
    private boolean running = false;
    private long lastFrameTime;
    
    // Ses ve titreşim
    private SoundPool soundPool;
    private int crashSoundId;      // 1.ogg
    private int driftSoundId;      // 2.ogg
    private int normalDriveSoundId; // 3.ogg
    private Vibrator vibrator;
    
    // Ses kontrolü için
    private boolean wasDrifting = false;
    private int driftSoundStreamId = 0;
    private int normalDriveStreamId = 0;
    private boolean normalDrivePlaying = false;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        paint = new Paint();
        textPaint = new Paint();
        roadPaint = new Paint();
        carPaint = new Paint();
        particlePaint = new Paint();
        random = new Random();
        
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        enemies = new ArrayList<>();
        weatherParticles = new ArrayList<>();
        
        weatherChangeInterval = 600 + random.nextFloat() * 400;
        
        player = new Player(0, 0);
        
        // Ses sistemini başlat
        initSoundPool(context);
        
        // Titreşim servisini başlat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }
    
    private void initSoundPool(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
                
            soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build();
        } else {
            soundPool = new SoundPool(3, AudioAttributes.USAGE_GAME, 0);
        }
        
        // Ses dosyalarını yükle
        crashSoundId = soundPool.load(context, R.raw.crash, 1);      // 1.ogg
        driftSoundId = soundPool.load(context, R.raw.drift, 1);      // 2.ogg
        normalDriveSoundId = soundPool.load(context, R.raw.drive, 1); // 3.ogg
        
        // Ses yükleme dinleyicisi
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    // Ses başarıyla yüklendi
                }
            }
        });
    }
    
    /**
     * Telefonu 1 saniye titreştirir
     */
    private void vibrateDevice() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+ için VibrationEffect kullan
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            // Eski API için
            vibrator.vibrate(1000);
        }
    }
    
    /**
     * Çarpışma sesini ve titreşimi çalıştırır
     */
    private void playCrashEffects() {
        // Çarpışma sesi (1.ogg)
        if (soundPool != null) {
            soundPool.play(crashSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
        }
        
        // Titreşim
        vibrateDevice();
        
        // Diğer sesleri durdur
        stopDriftSound();
        stopNormalDriveSound();
    }
    
    /**
     * Drift sesini başlatır (2.ogg - loop)
     */
    private void startDriftSound() {
        if (soundPool != null && !wasDrifting) {
            stopNormalDriveSound();
            driftSoundStreamId = soundPool.play(driftSoundId, 0.7f, 0.7f, 1, -1, 1.0f); // -1 = sonsuz döngü
            wasDrifting = true;
        }
    }
    
    /**
     * Drift sesini durdurur
     */
    private void stopDriftSound() {
        if (soundPool != null && wasDrifting) {
            soundPool.stop(driftSoundStreamId);
            wasDrifting = false;
        }
    }
    
    /**
     * Normal sürüş sesini başlatır (3.ogg - loop)
     */
    private void startNormalDriveSound() {
        if (soundPool != null && !normalDrivePlaying && !gameOver) {
            stopDriftSound();
            normalDriveStreamId = soundPool.play(normalDriveSoundId, 0.5f, 0.5f, 1, -1, 1.0f); // -1 = sonsuz döngü
            normalDrivePlaying = true;
        }
    }
    
    /**
     * Normal sürüş sesini durdurur
     */
    private void stopNormalDriveSound() {
        if (soundPool != null && normalDrivePlaying) {
            soundPool.stop(normalDriveStreamId);
            normalDrivePlaying = false;
        }
    }
    
    /**
     * Tüm sesleri durdurur
     */
    private void stopAllSounds() {
        stopDriftSound();
        stopNormalDriveSound();
    }
    
    /**
     * Ses ve titreşim kaynaklarını serbest bırakır
     */
    public void releaseResources() {
        stopAllSounds();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        canvasWidth = w;
        canvasHeight = h;
        resetGame();
    }

    public void resume() {
        running = true;
        lastFrameTime = System.nanoTime();
        startNormalDriveSound();
        invalidate();
    }

    public void pause() {
        running = false;
        stopAllSounds();
    }

    private void resetGame() {
        player.reset(canvasWidth / 2 - 20, canvasHeight - 110);
        enemies.clear();
        gameOver = false;
        score = 0;
        driftCombo = 0;
        driftScore = 0;
        frameCounter = 0;
        roadOffset = 0;
        crashAnimation = null;
        
        leftPressed = false;
        rightPressed = false;
        driftPressed = false;
        touchStartX = -1;
        touchActive = false;
        touchMoved = false;
        
        stopAllSounds();
        wasDrifting = false;
        
        changeWeather();
        
        // Başlangıç düşmanları
        for (int i = 0; i < 2; i++) {
            float enemyWidth = 40;
            float randX = random.nextFloat() * (canvasWidth - enemyWidth - 20) + 10;
            enemies.add(new Enemy(
                randX,
                -100 - i * 110,
                enemyWidth,
                60,
                enemyColors[i % 3],
                3.5f + i * 0.4f
            ));
        }
        
        startNormalDriveSound();
        invalidate();
    }

    private void changeWeather() {
        int newWeather;
        do {
            newWeather = random.nextInt(3);
        } while (newWeather == currentWeather);
        
        currentWeather = newWeather;
        
        switch (currentWeather) {
            case 0: weatherEmoji = "☀️"; break;
            case 1: weatherEmoji = "🌧️"; break;
            case 2: weatherEmoji = "❄️"; break;
        }
        
        weatherParticles.clear();
        
        if (currentWeather != 0) {
            for (int i = 0; i < 80; i++) {
                weatherParticles.add(new WeatherParticle(canvasWidth, canvasHeight, currentWeather == 1 ? 0 : 1));
            }
        }
        
        weatherTimer = 0;
        weatherChangeInterval = 500 + random.nextFloat() * 600;
    }

    private void triggerCrash(float x, float y) {
        crashAnimation = new CrashAnimation(x, y);
        player.shakeX = 15;
        player.shakeY = 10;
        
        // Çarpışma efektlerini çalıştır (ses + titreşim)
        playCrashEffects();
    }

    private boolean rectCollide(Player p, Enemy e) {
        float margin = 3;
        return !(e.x > p.x + p.width - margin ||
                e.x + e.width < p.x + margin ||
                e.y > p.y + p.height - margin ||
                e.y + e.height < p.y + margin);
    }

    private void spawnEnemy() {
        float enemyWidth = 38 + random.nextFloat() * 6;
        float laneMin = 15;
        float laneMax = canvasWidth - enemyWidth - 15;
        float enemyX = random.nextFloat() * (laneMax - laneMin) + laneMin;
        
        int color = enemyColors[random.nextInt(enemyColors.length)];
        
        float speedMultiplier = 1;
        if (currentWeather == 1) speedMultiplier = 1.2f;
        if (currentWeather == 2) speedMultiplier = 0.8f;
        
        enemies.add(new Enemy(
            enemyX,
            -70 - random.nextFloat() * 40,
            enemyWidth,
            60,
            color,
            (3.2f + random.nextFloat() * 2.8f) * speedMultiplier
        ));
    }

    private void updateAutoDrift() {
        if (!touchActive && !leftPressed && !rightPressed && !gameOver) {
            player.autoDriftTimer++;
            
            if (player.autoDriftTimer > 120 + random.nextFloat() * 120) {
                player.autoDriftTimer = 0;
                
                float rand = random.nextFloat();
                if (rand < 0.4) {
                    player.autoDriftDirection = -1;
                } else if (rand < 0.8) {
                    player.autoDriftDirection = 1;
                } else {
                    player.autoDriftDirection = 0;
                }
            }
            
            if (player.autoDriftDirection != 0) {
                float autoSpeed = 1.2f;
                player.x += player.autoDriftDirection * autoSpeed;
                player.clampX(canvasWidth);
                player.targetAngle = player.autoDriftDirection * 0.08f;
            } else {
                player.targetAngle *= 0.9;
            }
            
            if (player.x <= player.getMinX() + 15) {
                player.autoDriftDirection = 1;
                player.autoDriftTimer = 0;
            } else if (player.x >= player.getMaxX(canvasWidth) - 15) {
                player.autoDriftDirection = -1;
                player.autoDriftTimer = 0;
            }
        } else {
            player.autoDriftDirection = 0;
            player.autoDriftTimer = 0;
        }
    }

    private void updateDrift() {
        if (driftPressed && !gameOver) {
            player.driftPower = Math.min(1, player.driftPower + 0.08f);
            
            // Drift sesini başlat
            startDriftSound();
            
            if (leftPressed && !rightPressed) {
                player.targetAngle = -0.5f * player.driftPower;
            } else if (rightPressed && !leftPressed) {
                player.targetAngle = 0.5f * player.driftPower;
            } else if (touchMoved && touchStartX >= 0) {
                float deltaX = touchCurrentX - touchStartX;
                if (Math.abs(deltaX) > 10) {
                    player.targetAngle = Math.signum(deltaX) * 0.5f * player.driftPower;
                }
            } else {
                player.targetAngle = (float) Math.sin(frameCounter * 0.15) * 0.2f * player.driftPower;
            }
            
            if (player.driftPower > 0.3) {
                player.lateralSpeed += player.targetAngle * 2.5f;
                player.lateralSpeed *= 0.94f;
            }
            
            if (player.driftPower > 0.5 && Math.abs(player.angle) > 0.15) {
                driftCombo += 0.15f;
                if (driftCombo > 1 && frameCounter % 8 == 0) {
                    int bonusPoints = (int) (driftCombo * 2);
                    driftScore += bonusPoints;
                    score += bonusPoints;
                }
            }
        } else {
            player.driftPower = Math.max(0, player.driftPower - 0.06f);
            player.targetAngle *= 0.88f;
            player.lateralSpeed *= 0.82f;
            driftCombo = Math.max(0, driftCombo - 0.1f);
            driftScore = 0;
            
            // Drift bitti, normal sürüş sesine dön
            stopDriftSound();
            if (!gameOver) {
                startNormalDriveSound();
            }
        }
        
        player.angle += (player.targetAngle - player.angle) * 0.18f;
    }

    private void movePlayer(String direction) {
        if (gameOver) return;
        
        float baseStep = 5.5f;
        float step = baseStep;
        
        if (driftPressed && player.driftPower > 0.3f) {
            step = baseStep * 1.5f;
        }
        
        if (currentWeather == 2) step *= 0.7f;
        if (currentWeather == 1) step *= 1.1f;
        
        if ("left".equals(direction)) {
            player.x = Math.max(player.getMinX(), player.x - step);
            if (!driftPressed) player.targetAngle = -0.12f;
        } else if ("right".equals(direction)) {
            player.x = Math.min(player.getMaxX(canvasWidth), player.x + step);
            if (!driftPressed) player.targetAngle = 0.12f;
        }
        
        if (player.lateralSpeed != 0) {
            player.x += player.lateralSpeed;
            player.clampX(canvasWidth);
        }
    }

    private void update() {
        if (gameOver) {
            if (crashAnimation != null) {
                crashAnimation.update();
            }
            
            if (player.shakeX > 0) {
                player.shakeX *= 0.9f;
                player.shakeY *= 0.9f;
                if (player.shakeX < 0.5f) {
                    player.shakeX = 0;
                    player.shakeY = 0;
                }
            }
            
            // Game over durumunda sesleri durdur
            stopAllSounds();
            
            return;
        }

        // Hava durumu değişimi
        weatherTimer++;
        if (weatherTimer > weatherChangeInterval) {
            changeWeather();
        }

        // Partikül güncelleme
        if (currentWeather != 0) {
            for (WeatherParticle p : weatherParticles) {
                p.update(currentWeather, canvasWidth, canvasHeight, frameCounter);
            }
        }

        updateAutoDrift();

        if (leftPressed) movePlayer("left");
        if (rightPressed) movePlayer("right");
        
        if (touchActive && touchMoved && touchStartX >= 0) {
            float deltaX = touchCurrentX - touchStartX;
            if (Math.abs(deltaX) > 5) {
                float moveAmount = Math.signum(deltaX) * Math.min(Math.abs(deltaX) * 0.15f, 8);
                player.x += moveAmount;
                player.clampX(canvasWidth);
                if (!driftPressed) {
                    player.targetAngle = Math.signum(deltaX) * 0.1f;
                }
            }
        }

        updateDrift();

        roadOffset = (roadOffset + 4) % 40;

        // Düşman güncelleme
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.y += e.speed;

            if (!gameOver && rectCollide(player, e)) {
                gameOver = true;
                float crashX = player.x + player.width / 2;
                float crashY = player.y + player.height / 2;
                triggerCrash(crashX, crashY);
                break;
            }

            if (e.y > canvasHeight + 50) {
                enemies.remove(i);
                if (!gameOver) {
                    score += 10;
                }
            }
        }

        if (gameOver) return;

        frameCounter++;
        int dynamicSpawn = (int) Math.max(25, spawnRate - Math.floor(score / 150f));
        if (frameCounter % dynamicSpawn == 0) {
            spawnEnemy();
        }
        
        if (score > 250 && frameCounter % 65 == 0 && random.nextFloat() < 0.4) {
            spawnEnemy();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        update();
        drawGame(canvas);
        
        if (running) {
            long currentTime = System.nanoTime();
            long elapsedMillis = (currentTime - lastFrameTime) / 1000000;
            if (elapsedMillis < 16) {
                try {
                    Thread.sleep(16 - elapsedMillis);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            lastFrameTime = System.nanoTime();
            invalidate();
        }
    }

    private void drawGame(Canvas canvas) {
        drawRoad(canvas);
        drawWeatherParticles(canvas);
        drawEnemies(canvas);
        drawPlayer(canvas);
        
        if (crashAnimation != null && crashAnimation.active) {
            drawCrashAnimation(canvas);
        }
        
        if (gameOver) {
            drawGameOver(canvas);
        }
        
        drawUI(canvas);
    }

    private void drawRoad(Canvas canvas) {
        int roadColor, roadSurface;
        
        switch (currentWeather) {
            case 1: // rainy
                roadColor = 0xFF252E35;
                roadSurface = 0xFF1A2528;
                break;
            case 2: // snowy
                roadColor = 0xFF3A4549;
                roadSurface = 0xFF2A3538;
                break;
            default:
                roadColor = 0xFF2F3E46;
                roadSurface = 0xFF1E2C2A;
        }
        
        // Arkaplan
        canvas.drawColor(roadColor);
        
        // Kenar çizgileri
        roadPaint.setColor(0xFFCAD2C5);
        canvas.drawRect(5, 0, 13, canvasHeight, roadPaint);
        canvas.drawRect(canvasWidth - 13, 0, canvasWidth - 5, canvasHeight, roadPaint);
        
        // Orta çizgi
        roadPaint.setColor(0xFFF0F3E8);
        for (float i = -20 + (roadOffset % 40); i < canvasHeight; i += 40) {
            canvas.drawRect(canvasWidth / 2 - 4, i, canvasWidth / 2 + 4, i + 22, roadPaint);
        }
        
        // Yol yüzeyi
        roadPaint.setColor(roadSurface);
        canvas.drawRect(10, 0, canvasWidth - 10, canvasHeight, roadPaint);
        
        // Kar birikintisi
        if (currentWeather == 2) {
            roadPaint.setColor(0x26FFFFFF);
            canvas.drawRect(0, canvasHeight - 5, canvasWidth, canvasHeight, roadPaint);
        }
        
        // Drift izleri
        if (player.driftPower > 0.3) {
            roadPaint.setColor(0x66FF6B35);
            canvas.drawRect(player.x - 8, player.y + player.height - 2, 
                          player.x + player.width + 8, player.y + player.height + 3, roadPaint);
            canvas.drawRect(player.x - 8, player.y + player.height + 6,
                          player.x + player.width + 8, player.y + player.height + 10, roadPaint);
        }
    }

    private void drawWeatherParticles(Canvas canvas) {
        if (currentWeather == 0) return;
        
        for (WeatherParticle p : weatherParticles) {
            particlePaint.setAlpha((int) (p.opacity * 255));
            
            if (currentWeather == 1) { // rain
                particlePaint.setColor(0xFFA0C4FF);
                particlePaint.setStrokeWidth(1);
                canvas.drawLine(p.x, p.y, p.x - 1, p.y + 8, particlePaint);
            } else { // snow
                particlePaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(p.x, p.y, p.size, particlePaint);
                
                particlePaint.setColor(0xFFE0F0FF);
                canvas.drawCircle(p.x - 0.5f, p.y - 0.5f, p.size * 0.5f, particlePaint);
            }
        }
    }

    private void drawEnemies(Canvas canvas) {
        for (Enemy e : enemies) {
            drawCar(canvas, e.x, e.y, e.width, e.height, e.color, 0, false, 1);
        }
    }

    private void drawPlayer(Canvas canvas) {
        float opacity = gameOver ? 0.3f + (float) Math.sin(frameCounter * 0.3) * 0.3f : 1;
        drawCar(canvas, player.x, player.y, player.width, player.height, 
               0xFF3A86FF, player.angle, true, opacity);
    }

    private void drawCar(Canvas canvas, float x, float y, float w, float h, 
                        int color, float angle, boolean isPlayer, float opacity) {
        canvas.save();
        
        carPaint.setAlpha((int) (opacity * 255));
        
        float shakeX = 0, shakeY = 0;
        if (isPlayer && player.shakeX > 0) {
            shakeX = (random.nextFloat() - 0.5f) * player.shakeX;
            shakeY = (random.nextFloat() - 0.5f) * player.shakeY;
        }
        
        canvas.translate(x + w / 2 + shakeX, y + h / 2 + shakeY);
        canvas.rotate((float) Math.toDegrees(angle));
        
        // Araba gövdesi
        carPaint.setColor(color);
        carPaint.setShadowLayer(8, 0, 0, 0x80000000);
        RectF body = new RectF(-w / 2, -h / 2, w / 2, h / 2);
        canvas.drawRoundRect(body, 8, 8, carPaint);
        
        // Camlar
        carPaint.setColor(0xFF1E2B32);
        carPaint.setShadowLayer(4, 0, 0, 0x80000000);
        RectF windows = new RectF(-w / 2 + 5, -h / 2 + 5, w / 2 - 5, -h / 2 + 21);
        canvas.drawRoundRect(windows, 5, 5, carPaint);
        
        // Drift alevleri
        if (isPlayer && player.driftPower > 0.3 && !gameOver) {
            float flameHeight = 8 + player.driftPower * 12;
            carPaint.setColor(0xFFFF6B35);
            carPaint.setShadowLayer(15 + player.driftPower * 10, 0, 0, 0xFFFF4400);
            
            canvas.drawRect(-w / 2 + 6, -h / 2 - flameHeight, -w / 2 + 11, -h / 2, carPaint);
            canvas.drawRect(w / 2 - 11, -h / 2 - flameHeight, w / 2 - 6, -h / 2, carPaint);
        }
        
        // Tekerlekler
        carPaint.setColor(0xFF111111);
        carPaint.setShadowLayer(3, 0, 0, 0x80000000);
        canvas.drawRect(-w / 2 - 3, -h / 2 + 8, -w / 2 + 3, -h / 2 + 22, carPaint);
        canvas.drawRect(w / 2 - 3, -h / 2 + 8, w / 2 + 3, -h / 2 + 22, carPaint);
        canvas.drawRect(-w / 2 - 3, h / 2 - 22, -w / 2 + 3, h / 2 - 8, carPaint);
        canvas.drawRect(w / 2 - 3, h / 2 - 22, w / 2 + 3, h / 2 - 8, carPaint);
        
        // Farlar
        carPaint.setColor(0xFFF9E076);
        carPaint.setShadowLayer(8, 0, 0, 0x80000000);
        canvas.drawRect(-w / 2 + 4, h / 2 - 6, -w / 2 + 12, h / 2, carPaint);
        canvas.drawRect(w / 2 - 12, h / 2 - 6, w / 2 - 4, h / 2, carPaint);
        
        if (isPlayer) {
            carPaint.setColor(0xFFFFF7B0);
            canvas.drawRect(-w / 2 + 6, h / 2 - 4, -w / 2 + 12, h / 2 + 1, carPaint);
            canvas.drawRect(w / 2 - 12, h / 2 - 4, w / 2 - 6, h / 2 + 1, carPaint);
        }
        
        canvas.restore();
    }

    private void drawCrashAnimation(Canvas canvas) {
        if (crashAnimation == null) return;
        
        float shockAlpha = Math.max(0, 1 - crashAnimation.timer / (float) crashAnimation.duration);
        
        // Şok dalgası
        carPaint.setColor(Color.argb((int) (shockAlpha * 0.6f * 255), 255, 200, 50));
        carPaint.setStyle(Paint.Style.STROKE);
        carPaint.setStrokeWidth(4);
        canvas.drawCircle(crashAnimation.x, crashAnimation.y, crashAnimation.shockwave, carPaint);
        
        carPaint.setColor(Color.argb((int) (shockAlpha * 0.4f * 255), 255, 100, 50));
        carPaint.setStrokeWidth(3);
        canvas.drawCircle(crashAnimation.x, crashAnimation.y, crashAnimation.shockwave * 0.7f, carPaint);
        
        carPaint.setStyle(Paint.Style.FILL);
        
        // Partiküller
        for (CrashAnimation.CrashParticle p : crashAnimation.particles) {
            carPaint.setColor(p.color);
            carPaint.setAlpha((int) (p.life * 255));
            carPaint.setShadowLayer(8, 0, 0, p.color);
            canvas.drawCircle(p.x, p.y, p.size, carPaint);
        }
        
        carPaint.setShadowLayer(0, 0, 0, 0);
    }

    private void drawGameOver(Canvas canvas) {
        // Karanlık overlay
        paint.setColor(0xB3000000);
        canvas.drawRect(0, 0, canvasWidth, canvasHeight, paint);
        
        // Kırmızı kenar
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x80FF0000);
        paint.setStrokeWidth(4);
        canvas.drawRect(5, 5, canvasWidth - 5, canvasHeight - 5, paint);
        paint.setStyle(Paint.Style.FILL);
        
        // Metinler
        textPaint.setTextSize(30);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setShadowLayer(15, 0, 0, 0xCCFF0000);
        canvas.drawText("💥 ÇARPIŞMA! 💥", canvasWidth / 2, canvasHeight / 2 - 30, textPaint);
        
        textPaint.setShadowLayer(0, 0, 0, 0);
        textPaint.setTextSize(18);
        canvas.drawText("Skor: " + score, canvasWidth / 2, canvasHeight / 2 + 10, textPaint);
        
        if (driftScore > 0) {
            textPaint.setColor(0xFFFF6B35);
            canvas.drawText("🔥 Drift puanı: " + driftScore, canvasWidth / 2, canvasHeight / 2 + 40, textPaint);
        }
        
        textPaint.setColor(0xFFAAAAAA);
        textPaint.setTextSize(14);
        canvas.drawText("Dokun = Yeniden Başla", canvasWidth / 2, canvasHeight / 2 + 70, textPaint);
    }

    private void drawUI(Canvas canvas) {
        // Hava durumu göstergesi
        paint.setColor(0x80000000);
        canvas.drawRect(canvasWidth - 50, 5, canvasWidth - 5, 30, paint);
        
        textPaint.setTextSize(20);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(weatherEmoji, canvasWidth - 27, 25, textPaint);
        
        // Skor
        textPaint.setTextSize(22);
        canvas.drawText("🏆 " + score, canvasWidth / 2, 35, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (gameOver) {
                    resetGame();
                    return true;
                }
                touchStartX = x;
                touchCurrentX = x;
                touchActive = true;
                touchMoved = false;
                driftPressed = true;
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (!touchActive || gameOver) return true;
                touchCurrentX = x;
                if (Math.abs(touchCurrentX - touchStartX) > 3) {
                    touchMoved = true;
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                driftPressed = false;
                touchActive = false;
                touchMoved = false;
                touchStartX = -1;
                return true;
        }
        
        return super.onTouchEvent(event);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseResources();
    }
}
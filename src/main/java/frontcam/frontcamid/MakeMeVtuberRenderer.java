package frontcam.frontcamid;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MakeMeVtuberRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("make-me-vtuber-renderer");

    private static final int WIDTH = 512;
    private static final int HEIGHT = 512;

    private static final float SPRING_STIFFNESS = 0.12f;
    private static final float CAMERA_DISTANCE = 30.0f;

    private static final float FADE_START_PITCH = 70.0f;
    private static final float FADE_END_PITCH = 90.0f;
    private static final float MIN_BODY_ALPHA = 0.2f;

    private volatile boolean isOpen = false;
    private volatile boolean windowReady = false;

    private final Object dataLock = new Object();
    private volatile PlayerModelData currentData = null;

    private Thread previewThread;
    private volatile boolean skinDirty = true;

    private float camX = 0, camY = -4, camZ = -CAMERA_DISTANCE;
    private float targetX = 0, targetY = -4, targetZ = 0;

    private float smoothHeadPitchDeg = 0;

    private final List<SmoothedPart> smoothedParts = new ArrayList<>();

    private static class SmoothedPart {
        String name;
        float posX, posY, posZ;
        float rotX, rotY, rotZ;
        float scaleX = 1, scaleY = 1, scaleZ = 1;
        boolean visible = true;
    }

    public void open() {
        if (isOpen) return;
        isOpen = true;
        previewThread = new Thread(this::previewWindowLoop, "MakeMeVtuber-Preview");
        previewThread.setDaemon(true);
        previewThread.start();
    }

    public void close() {
        isOpen = false;
        if (previewThread != null) {
            previewThread.interrupt();
            previewThread = null;
        }
        MicrophoneCapture.getInstance().stop();
    }

    public void tick(Minecraft client) {
        if (!isOpen || !windowReady) return;
        if (client.player == null) return;

        // Execute on the main render thread via Minecraft's executor
        // (compatible with 1.21.2+ where RenderCall was removed in 1.21.5)
        client.execute(() -> {
            try {
                PlayerModelData data = PlayerDataExtractor.extract(client, client.player);
                synchronized (dataLock) {
                    if (currentData == null || currentData.skinPixels == null) {
                        skinDirty = true;
                    }
                    currentData = data;
                }
            } catch (Exception e) {
                LOGGER.error("[MakeMeVtuber] Error extracting player data", e);
            }
        });
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff > (float) Math.PI) diff -= (float) (2 * Math.PI);
        while (diff < -(float) Math.PI) diff += (float) (2 * Math.PI);
        return a + diff * t;
    }

    private float[] computeFaceForwardPoint(float headPosX, float headPosY, float headPosZ,
                                             float rotX, float rotY, float rotZ, float distance) {
        float maxPitch = (float) Math.toRadians(72.0);
        float clampedRotX = Math.max(-maxPitch, Math.min(maxPitch, rotX));

        float cx = (float) Math.cos(clampedRotX), sx = (float) Math.sin(clampedRotX);
        float cy = (float) Math.cos(rotY), sy = (float) Math.sin(rotY);

        float fwdX = -sy;
        float fwdY = cy * sx;
        float fwdZ = -cy * cx;

        float faceOffset = 4.0f;

        float faceCenterX = headPosX + fwdX * faceOffset;
        float faceCenterY = headPosY + fwdY * faceOffset;
        float faceCenterZ = headPosZ + fwdZ * faceOffset;

        float camPosX = headPosX + fwdX * distance;
        float camPosY = headPosY + fwdY * distance;
        float camPosZ = headPosZ + fwdZ * distance;

        return new float[]{camPosX, camPosY, camPosZ, faceCenterX, faceCenterY, faceCenterZ, fwdX, fwdY, fwdZ};
    }

    private void updateSmoothing(PlayerModelData data) {
        float headPosX = 0, headPosY = 0, headPosZ = 0;
        float headRotX = 0, headRotY = 0, headRotZ = 0;
        for (PlayerModelData.BodyPart part : data.bodyParts) {
            if ("head".equals(part.name)) {
                headPosX = part.posX;
                headPosY = part.posY;
                headPosZ = part.posZ;
                headRotX = part.rotX;
                headRotY = part.rotY;
                headRotZ = part.rotZ;
                break;
            }
        }

        float[] camData = computeFaceForwardPoint(headPosX, headPosY, headPosZ,
                headRotX, headRotY, headRotZ, CAMERA_DISTANCE);

        float headPitchDeg = (float) Math.toDegrees(headRotX);
        smoothHeadPitchDeg = lerp(smoothHeadPitchDeg, headPitchDeg, SPRING_STIFFNESS);

        float idealCamX = camData[0];
        float idealCamY = camData[1];
        float idealCamZ = camData[2];
        float idealTargetX = camData[3];
        float idealTargetY = camData[4];
        float idealTargetZ = camData[5];

        camX = lerp(camX, idealCamX, SPRING_STIFFNESS);
        camY = lerp(camY, idealCamY, SPRING_STIFFNESS);
        camZ = lerp(camZ, idealCamZ, SPRING_STIFFNESS);

        targetX = lerp(targetX, idealTargetX, SPRING_STIFFNESS);
        targetY = lerp(targetY, idealTargetY, SPRING_STIFFNESS);
        targetZ = lerp(targetZ, idealTargetZ, SPRING_STIFFNESS);

        while (smoothedParts.size() < data.bodyParts.size()) {
            smoothedParts.add(new SmoothedPart());
        }
        for (int i = 0; i < data.bodyParts.size(); i++) {
            PlayerModelData.BodyPart target = data.bodyParts.get(i);
            SmoothedPart smooth = smoothedParts.get(i);
            float partLerp = 0.2f;

            if (smooth.name == null || !smooth.name.equals(target.name)) {
                smooth.name = target.name;
                smooth.posX = target.posX; smooth.posY = target.posY; smooth.posZ = target.posZ;
                smooth.rotX = target.rotX; smooth.rotY = target.rotY; smooth.rotZ = target.rotZ;
                smooth.scaleX = target.scaleX; smooth.scaleY = target.scaleY; smooth.scaleZ = target.scaleZ;
            } else {
                smooth.posX = lerp(smooth.posX, target.posX, partLerp);
                smooth.posY = lerp(smooth.posY, target.posY, partLerp);
                smooth.posZ = lerp(smooth.posZ, target.posZ, partLerp);
                smooth.rotX = lerpAngle(smooth.rotX, target.rotX, partLerp);
                smooth.rotY = lerpAngle(smooth.rotY, target.rotY, partLerp);
                smooth.rotZ = lerpAngle(smooth.rotZ, target.rotZ, partLerp);
                smooth.scaleX = lerp(smooth.scaleX, target.scaleX, partLerp);
                smooth.scaleY = lerp(smooth.scaleY, target.scaleY, partLerp);
                smooth.scaleZ = lerp(smooth.scaleZ, target.scaleZ, partLerp);
            }
            smooth.visible = target.visible;
        }
    }

    private void previewWindowLoop() {
        if (!GLFW.glfwInit()) {
            LOGGER.error("[MakeMeVtuber] GLFW init failed!");
            return;
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE);

        long window = GLFW.glfwCreateWindow(WIDTH, HEIGHT, "MakeMeVtuber Preview", 0L, 0L);
        if (window == 0L) {
            LOGGER.error("[MakeMeVtuber] Failed to create preview window!");
            return;
        }

        setWindowIcon(window, "/assets/make-me-vtuber/icons/preview.png");

        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(0);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

        int skinTexId = GL11.glGenTextures();
        int baseTexId = GL11.glGenTextures();

        windowReady = true;
        LOGGER.info("[MakeMeVtuber] Preview window ready.");

        while (isOpen && !GLFW.glfwWindowShouldClose(window)) {
            GLFW.glfwPollEvents();

            PlayerModelData data;
            synchronized (dataLock) {
                data = currentData;
            }

            if (data != null) {
                updateSmoothing(data);
                MicrophoneCapture.getInstance().updateSmoothing();
            }

            MakeMeVtuberSettings.ChromaKey chroma = MakeMeVtuberSettings.getInstance().getChromaKey();
            GL11.glClearColor(chroma.r, chroma.g, chroma.b, chroma.a);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            if (data != null) {
                if (skinDirty && data.skinPixels != null) {
                    uploadSkinTexture(skinTexId, data);
                    uploadBaseTexture(baseTexId, data);
                    skinDirty = false;
                }
                renderModel(data, baseTexId, skinTexId);
            }

            GLFW.glfwSwapBuffers(window);

            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }

        GL11.glDeleteTextures(skinTexId);
        GL11.glDeleteTextures(baseTexId);
        GLFW.glfwDestroyWindow(window);
        windowReady = false;
        isOpen = false;
        LOGGER.info("[MakeMeVtuber] Preview window closed.");
    }

    private void uploadSkinTexture(int texId, PlayerModelData data) {
        if (data.skinPixels == null) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        data.skinPixels.position(0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                data.skinWidth, data.skinHeight, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data.skinPixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * Creates a base-only texture: overlay regions of the skin are zeroed out (transparent).
     * Standard 64x64 skin layout overlay regions:
     * - Hat:           x=32-63, y=0-15
     * - Jacket:        x=16-39, y=32-47
     * - Right sleeve:  x=40-55, y=32-47
     * - Left sleeve:   x=48-63, y=48-63
     * - Right pants:   x=0-15,  y=32-47
     * - Left pants:    x=0-15,  y=48-63
     */
    private void uploadBaseTexture(int texId, PlayerModelData data) {
        if (data.skinPixels == null) return;
        int w = data.skinWidth;
        int h = data.skinHeight;

        data.skinPixels.position(0);
        ByteBuffer basePixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                byte r = data.skinPixels.get();
                byte g = data.skinPixels.get();
                byte b = data.skinPixels.get();
                byte a = data.skinPixels.get();

                if (isOverlayRegion(x, y, w, h)) {
                    // Zero out overlay regions
                    basePixels.put((byte) 0);
                    basePixels.put((byte) 0);
                    basePixels.put((byte) 0);
                    basePixels.put((byte) 0);
                } else {
                    basePixels.put(r);
                    basePixels.put(g);
                    basePixels.put(b);
                    basePixels.put((byte) 0xFF); // force opaque for base
                }
            }
        }
        basePixels.flip();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, basePixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private boolean isOverlayRegion(int x, int y, int w, int h) {
        // Normalized to 64x64 standard skin
        int nx = x * 64 / w;
        int ny = y * 64 / h;

        // Hat region: x=32-63, y=0-15
        if (nx >= 32 && nx < 64 && ny >= 0 && ny < 16) return true;
        // Jacket (body overlay): x=16-39, y=32-47
        if (nx >= 16 && nx < 40 && ny >= 32 && ny < 48) return true;
        // Right arm overlay (right sleeve): x=40-55, y=32-47
        if (nx >= 40 && nx < 56 && ny >= 32 && ny < 48) return true;
        // Left arm overlay (left sleeve): x=48-63, y=48-63
        if (nx >= 48 && nx < 64 && ny >= 48 && ny < 64) return true;
        // Right leg overlay (right pants): x=0-15, y=32-47
        if (nx >= 0 && nx < 16 && ny >= 32 && ny < 48) return true;
        // Left leg overlay (left pants): x=0-15, y=48-63
        if (nx >= 0 && nx < 16 && ny >= 48 && ny < 64) return true;

        return false;
    }

    private void renderModel(PlayerModelData data, int baseTexId, int overlayTexId) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        float fov = 50.0f;
        float aspect = (float) WIDTH / HEIGHT;
        float near = 1.0f, far = 500.0f;
        float top = near * (float) Math.tan(Math.toRadians(fov / 2.0));
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        GL11.glFrustum(left, right, bottom, top, near, far);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        lookAt(camX, camY, camZ, targetX, targetY, targetZ, 0, -1, 0);

        float[] lightPos = {camX, camY, camZ, 1.0f};
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, lightPos);
        float[] lightAmb = {0.5f, 0.5f, 0.5f, 1.0f};
        float[] lightDif = {0.9f, 0.9f, 0.9f, 1.0f};
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_AMBIENT, lightAmb);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, baseTexId);

        float bodyAlpha = 1.0f;
        float legsAlpha = 1.0f;

        float legsFadeStart = 50.0f;
        float legsFadeEnd = 85.0f;
        if (smoothHeadPitchDeg > legsFadeStart) {
            float fadeProgress = (smoothHeadPitchDeg - legsFadeStart) / (legsFadeEnd - legsFadeStart);
            fadeProgress = Math.max(0.0f, Math.min(1.0f, fadeProgress));
            fadeProgress = fadeProgress * fadeProgress;
            legsAlpha = 1.0f - fadeProgress;
        }

        if (smoothHeadPitchDeg > FADE_START_PITCH) {
            float fadeRange = FADE_END_PITCH - FADE_START_PITCH;
            float fadeProgress = (smoothHeadPitchDeg - FADE_START_PITCH) / fadeRange;
            fadeProgress = Math.max(0.0f, Math.min(1.0f, fadeProgress));
            bodyAlpha = 1.0f - fadeProgress * (1.0f - MIN_BODY_ALPHA);
        }

        // === Base parts (opaque, overlay regions zeroed in texture) ===
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.5f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, baseTexId);
        for (int i = 0; i < data.bodyParts.size(); i++) {
            PlayerModelData.BodyPart part = data.bodyParts.get(i);
            SmoothedPart smooth = (i < smoothedParts.size()) ? smoothedParts.get(i) : null;
            if (isOverlayPart(part.name)) continue;

            float alpha = getPartAlpha(part.name, bodyAlpha, legsAlpha);
            if (alpha <= 0.0f) continue;

            if (alpha < 1.0f) {
                GL11.glEnable(GL11.GL_POLYGON_STIPPLE);
                GL11.glPolygonStipple(generateStipplePattern(alpha));
            }
            GL11.glColor4f(1f, 1f, 1f, 1f);
            renderBodyPart(part, smooth);
            if (alpha < 1.0f) {
                GL11.glDisable(GL11.GL_POLYGON_STIPPLE);
            }
        }

        // === Overlay with blending (original texture with alpha) ===
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_BLEND);
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO, GL11.GL_ONE
        );

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, overlayTexId);
        for (int i = 0; i < data.bodyParts.size(); i++) {
            PlayerModelData.BodyPart part = data.bodyParts.get(i);
            if (!isOverlayPart(part.name)) continue;

            SmoothedPart smooth = findBaseSmooth(part.name, data);

            float alpha = getPartAlpha(part.name, bodyAlpha, legsAlpha);
            if (alpha <= 0.0f) continue;

            if (alpha < 1.0f) {
                GL11.glEnable(GL11.GL_POLYGON_STIPPLE);
                GL11.glPolygonStipple(generateStipplePattern(alpha));
            }
            GL11.glColor4f(1f, 1f, 1f, 1f);
            renderBodyPart(part, smooth);
            if (alpha < 1.0f) {
                GL11.glDisable(GL11.GL_POLYGON_STIPPLE);
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);

        {
            PlayerModelData.BodyPart headPart = null;
            SmoothedPart headSmooth = null;
            for (int i = 0; i < data.bodyParts.size(); i++) {
                if ("head".equals(data.bodyParts.get(i).name)) {
                    headPart = data.bodyParts.get(i);
                    headSmooth = (i < smoothedParts.size()) ? smoothedParts.get(i) : null;
                    break;
                }
            }
            if (headPart != null && MakeMeVtuberSettings.getInstance().isMouthEnabled()) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthMask(false);
                renderJaw(headPart, headSmooth);
                GL11.glDepthMask(true);
            }
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }

    private java.nio.ByteBuffer generateStipplePattern(float alpha) {
        java.nio.ByteBuffer pattern = java.nio.ByteBuffer.allocateDirect(128);

        float[][] bayer4 = {
            {0.0f/16, 8.0f/16, 2.0f/16, 10.0f/16},
            {12.0f/16, 4.0f/16, 14.0f/16, 6.0f/16},
            {3.0f/16, 11.0f/16, 1.0f/16, 9.0f/16},
            {15.0f/16, 7.0f/16, 13.0f/16, 5.0f/16}
        };

        for (int row = 0; row < 32; row++) {
            int rowBits = 0;
            for (int col = 0; col < 32; col++) {
                float threshold = bayer4[row % 4][col % 4];
                if (alpha > threshold) {
                    rowBits |= (1 << (31 - col));
                }
            }
            pattern.put((byte) ((rowBits >> 24) & 0xFF));
            pattern.put((byte) ((rowBits >> 16) & 0xFF));
            pattern.put((byte) ((rowBits >> 8) & 0xFF));
            pattern.put((byte) (rowBits & 0xFF));
        }
        pattern.flip();
        return pattern;
    }

    private boolean isOverlayPart(String name) {
        return "hat".equals(name) || "jacket".equals(name) ||
               "left_sleeve".equals(name) || "right_sleeve".equals(name) ||
               "left_pants".equals(name) || "right_pants".equals(name);
    }

    /**
     * Maps overlay part names to their corresponding base part names,
     * then returns the smoothed transform from that base part.
     */
    private SmoothedPart findBaseSmooth(String overlayName, PlayerModelData data) {
        String baseName = switch (overlayName) {
            case "hat" -> "head";
            case "jacket" -> "body";
            case "left_sleeve" -> "left_arm";
            case "right_sleeve" -> "right_arm";
            case "left_pants" -> "left_leg";
            case "right_pants" -> "right_leg";
            default -> null;
        };
        if (baseName == null) return null;

        for (int i = 0; i < data.bodyParts.size(); i++) {
            if (baseName.equals(data.bodyParts.get(i).name) && i < smoothedParts.size()) {
                return smoothedParts.get(i);
            }
        }
        return null;
    }

    private float getPartAlpha(String name, float bodyAlpha, float legsAlpha) {
        if ("head".equals(name) || "hat".equals(name)) {
            return 1.0f;
        }
        if ("left_leg".equals(name) || "right_leg".equals(name) ||
            "left_pants".equals(name) || "right_pants".equals(name)) {
            return legsAlpha;
        }
        return bodyAlpha;
    }

    private void lookAt(float eyeX, float eyeY, float eyeZ,
                        float centerX, float centerY, float centerZ,
                        float upX, float upY, float upZ) {
        float fx = centerX - eyeX, fy = centerY - eyeY, fz = centerZ - eyeZ;
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen < 0.0001f) fLen = 1;
        fx /= fLen; fy /= fLen; fz /= fLen;

        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen < 0.0001f) sLen = 1;
        sx /= sLen; sy /= sLen; sz /= sLen;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        float[] m = {
            sx, ux, -fx, 0,
            sy, uy, -fy, 0,
            sz, uz, -fz, 0,
            0,  0,   0,  1
        };
        GL11.glMultMatrixf(m);
        GL11.glTranslatef(-eyeX, -eyeY, -eyeZ);
    }

    private void renderBodyPart(PlayerModelData.BodyPart part, SmoothedPart smooth) {
        if (!part.visible) return;
        GL11.glPushMatrix();

        float posX = smooth != null ? smooth.posX : part.posX;
        float posY = smooth != null ? smooth.posY : part.posY;
        float posZ = smooth != null ? smooth.posZ : part.posZ;
        float rotX = smooth != null ? smooth.rotX : part.rotX;
        float rotY = smooth != null ? smooth.rotY : part.rotY;
        float rotZ = smooth != null ? smooth.rotZ : part.rotZ;
        float scaleX = smooth != null ? smooth.scaleX : part.scaleX;
        float scaleY = smooth != null ? smooth.scaleY : part.scaleY;
        float scaleZ = smooth != null ? smooth.scaleZ : part.scaleZ;

        GL11.glTranslatef(posX, posY, posZ);
        if (rotZ != 0) GL11.glRotatef((float) Math.toDegrees(rotZ), 0, 0, 1);
        if (rotY != 0) GL11.glRotatef((float) Math.toDegrees(rotY), 0, 1, 0);
        if (rotX != 0) GL11.glRotatef((float) Math.toDegrees(rotX), 1, 0, 0);
        if (scaleX != 1f || scaleY != 1f || scaleZ != 1f) {
            GL11.glScalef(scaleX, scaleY, scaleZ);
        }

        for (PlayerModelData.Cuboid cuboid : part.cuboids) {
            renderCuboid(cuboid);
        }
        for (PlayerModelData.BodyPart child : part.children) {
            renderBodyPart(child, null);
        }

        GL11.glPopMatrix();
    }

    private void renderCuboid(PlayerModelData.Cuboid cuboid) {
        GL11.glBegin(GL11.GL_QUADS);
        for (PlayerModelData.Face face : cuboid.faces) {
            GL11.glNormal3f(face.normalX, face.normalY, face.normalZ);
            for (int i = 0; i < 4; i++) {
                PlayerModelData.Vertex v = face.vertices[i];
                if (v == null) continue;
                GL11.glTexCoord2f(v.u, v.v);
                GL11.glVertex3f(v.x, v.y, v.z);
            }
        }
        GL11.glEnd();
    }

    private void renderJaw(PlayerModelData.BodyPart headPart, SmoothedPart smooth) {
        float rawVolume = MicrophoneCapture.getInstance().getVolume();
        float threshold = MakeMeVtuberSettings.getInstance().getMicThreshold();

        float volume = 0;
        if (rawVolume > threshold) {
            volume = (rawVolume - threshold) / (1.0f - threshold);
            volume = Math.min(1.0f, volume);
        }
        if (volume < 0.01f) return;

        float mouthX = MakeMeVtuberSettings.getInstance().getMouthOffsetX();
        float mouthY = MakeMeVtuberSettings.getInstance().getMouthOffsetY();
        float intensity = MakeMeVtuberSettings.getInstance().getMouthIntensity();
        float mouthW = MakeMeVtuberSettings.getInstance().getMouthWidth();
        float mouthH = MakeMeVtuberSettings.getInstance().getMouthHeight();

        GL11.glPushMatrix();

        float posX = smooth != null ? smooth.posX : headPart.posX;
        float posY = smooth != null ? smooth.posY : headPart.posY;
        float posZ = smooth != null ? smooth.posZ : headPart.posZ;
        float rotX = smooth != null ? smooth.rotX : headPart.rotX;
        float rotY = smooth != null ? smooth.rotY : headPart.rotY;
        float rotZ = smooth != null ? smooth.rotZ : headPart.rotZ;

        GL11.glTranslatef(posX, posY, posZ);
        if (rotZ != 0) GL11.glRotatef((float) Math.toDegrees(rotZ), 0, 0, 1);
        if (rotY != 0) GL11.glRotatef((float) Math.toDegrees(rotY), 0, 1, 0);
        if (rotX != 0) GL11.glRotatef((float) Math.toDegrees(rotX), 1, 0, 0);

        float faceX = mouthX * 3.0f;
        float faceY = -4.0f + mouthY * 4.0f;
        float faceZ = -4.1f;

        GL11.glTranslatef(faceX, faceY, faceZ);

        float jawOpen = volume * intensity;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor3f(0.1f, 0.05f, 0.05f);

        float hw = 1.5f * mouthW;
        float hh = 0.8f * mouthH * jawOpen;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(-hw, 0, 0);
        GL11.glVertex3f(hw, 0, 0);
        GL11.glVertex3f(hw, hh, 0);
        GL11.glVertex3f(-hw, hh, 0);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glPopMatrix();
    }

    static void setWindowIcon(long window, String resourcePath) {
        try {
            InputStream is = MakeMeVtuberRenderer.class.getResourceAsStream(resourcePath);
            if (is == null) return;
            BufferedImage img = ImageIO.read(is);
            is.close();
            int w = img.getWidth(), h = img.getHeight();
            ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    pixels.put((byte) ((argb >> 16) & 0xFF));
                    pixels.put((byte) ((argb >> 8) & 0xFF));
                    pixels.put((byte) (argb & 0xFF));
                    pixels.put((byte) ((argb >> 24) & 0xFF));
                }
            }
            pixels.flip();
            GLFWImage.Buffer icons = GLFWImage.malloc(1);
            icons.position(0).width(w).height(h).pixels(pixels);
            GLFW.glfwSetWindowIcon(window, icons);
            icons.free();
        } catch (Exception e) {
            LoggerFactory.getLogger("make-me-vtuber").warn("Failed to set window icon", e);
        }
    }
}

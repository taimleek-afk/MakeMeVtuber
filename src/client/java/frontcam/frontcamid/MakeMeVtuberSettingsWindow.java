package frontcam.frontcamid;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class MakeMeVtuberSettingsWindow {
    private static final Logger LOGGER = LoggerFactory.getLogger("make-me-vtuber-settings");
    private static MakeMeVtuberSettingsWindow instance;

    private static final int WIN_W = 450;
    private static final int WIN_H = 980;

    private volatile boolean isOpen = false;
    private Thread windowThread;
    private long window = 0L;

    private double mouseX = 0, mouseY = 0;
    private boolean mouseClicked = false;
    private boolean mouseDragging = false;
    private boolean draggingSlider = false;
    private boolean draggingPad = false;

    private int padBoundsX, padBoundsY, padBoundsSize;
    private int[][] sliderBounds = new int[5][4];
    private int draggingSliderIndex = -1;

    private static final int BTN_X = 20;
    private static final int BTN_W = 410;
    private static final int BTN_H = 44;
    private static final int BTN_GAP = 8;

    private static final int SECTION1_Y = 50;
    private static final int BTN_Y_GREEN = SECTION1_Y;
    private static final int BTN_Y_BLUE = SECTION1_Y + BTN_H + BTN_GAP;
    private static final int BTN_Y_TRANSPARENT = SECTION1_Y + 2 * (BTN_H + BTN_GAP);

    private static final int SECTION2_TITLE_Y = BTN_Y_TRANSPARENT + BTN_H + 25;
    private static final int MIC_LIST_Y = SECTION2_TITLE_Y + 40;
    private static final int MIC_BTN_H = 36;
    private static final int MIC_BTN_GAP = 5;
    private static final int MAX_MIC_BUTTONS = 4;

    private int texChromaTitle = 0;
    private int texGreen = 0;
    private int texBlue = 0;
    private int texTransparent = 0;
    private int texMicTitle = 0;
    private int[] texMicDevices = new int[MAX_MIC_BUTTONS];
    private String[] micDeviceNames = new String[MAX_MIC_BUTTONS];
    private int micDeviceCount = 0;
    private int texThresholdLabel = 0;
    private int texGainLabel = 0;
    private int texWidthLabel = 0;
    private int texHeightLabel = 0;
    private int texIntensityLabel = 0;
    private int texMouthPosLabel = 0;

    public static synchronized MakeMeVtuberSettingsWindow getInstance() {
        if (instance == null) {
            instance = new MakeMeVtuberSettingsWindow();
        }
        return instance;
    }

    public void open() {
        if (isOpen) return;
        isOpen = true;
        windowThread = new Thread(this::windowLoop, "MakeMeVtuber-Settings");
        windowThread.setDaemon(true);
        windowThread.start();
    }

    public void close() {
        isOpen = false;
        if (windowThread != null) {
            windowThread.interrupt();
            windowThread = null;
        }
    }

    private void windowLoop() {
        try { Thread.sleep(300); } catch (InterruptedException e) { return; }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(WIN_W, WIN_H, "MakeMeVtuber Settings", 0L, 0L);
        if (window == 0L) {
            LOGGER.error("[MakeMeVtuber] Failed to create settings window!");
            isOpen = false;
            return;
        }

        MakeMeVtuberRenderer.setWindowIcon(window, "/assets/make-me-vtuber/icons/settings.png");

        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);

        GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW.GLFW_PRESS) {
                    mouseClicked = true;
                    mouseDragging = true;
                } else if (action == GLFW.GLFW_RELEASE) {
                    mouseDragging = false;
                    draggingSlider = false;
                    draggingSliderIndex = -1;
                    draggingPad = false;
                }
            }
        });

        texChromaTitle = createTextTexture("Chromakey Background", 22, true, new Color(220, 220, 255), null);
        texGreen = createTextTexture("Green", 20, true, new Color(50, 255, 50), null);
        texBlue = createTextTexture("Blue", 20, true, new Color(100, 160, 255), null);
        texTransparent = createTextTexture("Transparent [experimental]", 17, true, new Color(200, 200, 200), new Color(255, 200, 0));

        texMicTitle = createTextTexture("Microphone", 22, true, new Color(220, 220, 255), null);
        loadMicDevices();

        texThresholdLabel = createTextTexture("Threshold", 17, true, new Color(220, 180, 130), null);
        texGainLabel = createTextTexture("Mic Gain", 17, true, new Color(220, 180, 130), null);
        texWidthLabel = createTextTexture("Width", 17, true, new Color(160, 220, 160), null);
        texHeightLabel = createTextTexture("Height", 17, true, new Color(160, 220, 160), null);
        texIntensityLabel = createTextTexture("Intensity", 17, true, new Color(200, 170, 230), null);
        texMouthPosLabel = createTextTexture("Mouth Position", 17, true, new Color(220, 220, 180), null);

        LOGGER.info("[MakeMeVtuber] Settings window created.");

        while (isOpen && !GLFW.glfwWindowShouldClose(window)) {
            GLFW.glfwPollEvents();

            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(window, mx, my);
            mouseX = mx[0];
            mouseY = my[0];

            if (mouseClicked) {
                handleClick((float) mouseX, (float) mouseY);
                mouseClicked = false;
            }

            if (mouseDragging) {
                handleDrag((float) mouseX, (float) mouseY);
            }

            render();
            GLFW.glfwSwapBuffers(window);

            try { Thread.sleep(33); } catch (InterruptedException e) { break; }
        }

        GL11.glDeleteTextures(texChromaTitle);
        GL11.glDeleteTextures(texGreen);
        GL11.glDeleteTextures(texBlue);
        GL11.glDeleteTextures(texTransparent);
        GL11.glDeleteTextures(texMicTitle);
        GL11.glDeleteTextures(texThresholdLabel);
        GL11.glDeleteTextures(texGainLabel);
        GL11.glDeleteTextures(texWidthLabel);
        GL11.glDeleteTextures(texHeightLabel);
        GL11.glDeleteTextures(texIntensityLabel);
        GL11.glDeleteTextures(texMouthPosLabel);
        for (int i = 0; i < micDeviceCount; i++) {
            GL11.glDeleteTextures(texMicDevices[i]);
        }

        GLFW.glfwDestroyWindow(window);
        window = 0L;
        isOpen = false;
        LOGGER.info("[MakeMeVtuber] Settings window closed.");
    }

    private void loadMicDevices() {
        List<String> devices = MicrophoneCapture.getInstance().getAvailableDevices();
        micDeviceCount = Math.min(devices.size(), MAX_MIC_BUTTONS);
        for (int i = 0; i < micDeviceCount; i++) {
            String name = devices.get(i);
            if (name.length() > 35) name = name.substring(0, 32) + "...";
            micDeviceNames[i] = devices.get(i);
            texMicDevices[i] = createTextTexture(name, 15, true, new Color(200, 220, 240), null);
        }
        if (micDeviceCount == 0) {
            texMicDevices[0] = createTextTexture("No microphones found", 15, false, new Color(180, 100, 100), null);
            micDeviceNames[0] = null;
            micDeviceCount = 1;
        }
    }

    private int createTextTexture(String text, int fontSize, boolean bold,
                                   Color textColor, Color accentColor) {
        int imgW = 420;
        int imgH = 40;

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, imgW, imgH);
        g.setComposite(AlphaComposite.SrcOver);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font font = new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, fontSize);
        g.setFont(font);
        g.setColor(textColor);

        FontMetrics fm = g.getFontMetrics();
        int textY = (imgH + fm.getAscent() - fm.getDescent()) / 2;

        if (accentColor != null && text.contains("[")) {
            int bracketIdx = text.indexOf('[');
            String mainText = text.substring(0, bracketIdx);
            String accentText = text.substring(bracketIdx);

            g.drawString(mainText, 4, textY);
            int mainWidth = fm.stringWidth(mainText);
            g.setColor(accentColor);
            g.setFont(new Font("SansSerif", Font.BOLD | Font.ITALIC, fontSize - 2));
            g.drawString(accentText, 4 + mainWidth, textY);
        } else {
            g.drawString(text, 4, textY);
        }

        g.dispose();
        return uploadBufferedImage(img);
    }

    private int uploadBufferedImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                buffer.put((byte) ((argb >> 16) & 0xFF));
                buffer.put((byte) ((argb >> 8) & 0xFF));
                buffer.put((byte) (argb & 0xFF));
                buffer.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        buffer.flip();

        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texId;
    }

    private void handleClick(float x, float y) {
        if (isInButton(x, y, BTN_Y_GREEN, BTN_H)) {
            MakeMeVtuberSettings.getInstance().setChromaKey(MakeMeVtuberSettings.ChromaKey.GREEN);
        } else if (isInButton(x, y, BTN_Y_BLUE, BTN_H)) {
            MakeMeVtuberSettings.getInstance().setChromaKey(MakeMeVtuberSettings.ChromaKey.BLUE);
        } else if (isInButton(x, y, BTN_Y_TRANSPARENT, BTN_H)) {
            MakeMeVtuberSettings.getInstance().setChromaKey(MakeMeVtuberSettings.ChromaKey.TRANSPARENT);
        }

        for (int i = 0; i < micDeviceCount; i++) {
            int btnY = MIC_LIST_Y + i * (MIC_BTN_H + MIC_BTN_GAP);
            if (isInButton(x, y, btnY, MIC_BTN_H) && micDeviceNames[i] != null) {
                MicrophoneCapture.getInstance().selectDevice(micDeviceNames[i]);
                LOGGER.info("[MakeMeVtuber] Mic selected: {}", micDeviceNames[i]);
            }
        }

        for (int i = 0; i < sliderBounds.length; i++) {
            int[] b = sliderBounds[i];
            if (x >= b[0] && x <= b[0] + b[2] && y >= b[1] - 5 && y <= b[1] + b[3] + 5) {
                draggingSliderIndex = i;
                draggingSlider = true;
                break;
            }
        }

        if (x >= padBoundsX && x <= padBoundsX + padBoundsSize &&
            y >= padBoundsY && y <= padBoundsY + padBoundsSize) {
            draggingPad = true;
        }
    }

    private void handleDrag(float x, float y) {
        if (draggingSlider && draggingSliderIndex >= 0) {
            int[] b = sliderBounds[draggingSliderIndex];
            float normalized = (x - b[0]) / b[2];
            normalized = Math.max(0.0f, Math.min(1.0f, normalized));

            switch (draggingSliderIndex) {
                case 0 -> MakeMeVtuberSettings.getInstance().setMicThreshold(normalized);
                case 1 -> MakeMeVtuberSettings.getInstance().setMicGain(normalized * 5.0f);
                case 2 -> MakeMeVtuberSettings.getInstance().setMouthWidth(normalized * 3.0f);
                case 3 -> MakeMeVtuberSettings.getInstance().setMouthHeight(normalized * 3.0f);
                case 4 -> MakeMeVtuberSettings.getInstance().setMouthIntensity(normalized * 3.0f);
            }
        }

        if (draggingPad && padBoundsSize > 0) {
            float normalizedX = ((x - padBoundsX) / padBoundsSize) * 2.0f - 1.0f;
            float normalizedY = ((y - padBoundsY) / padBoundsSize) * 2.0f - 1.0f;
            MakeMeVtuberSettings.getInstance().setMouthOffset(normalizedX, normalizedY);
        }
    }

    private boolean isInButton(float x, float y, int btnY, int btnH) {
        return x >= BTN_X && x <= BTN_X + BTN_W && y >= btnY && y <= btnY + btnH;
    }

    private void render() {
        GL11.glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, WIN_W, WIN_H, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);

        MakeMeVtuberSettings.ChromaKey currentChroma = MakeMeVtuberSettings.getInstance().getChromaKey();
        String currentMic = MicrophoneCapture.getInstance().getSelectedDeviceName();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(0.15f, 0.15f, 0.25f);
        drawRect(0, 0, WIN_W, 44);
        drawTexturedQuad(texChromaTitle, 15, 8, 336, 32);

        drawButton(BTN_Y_GREEN, BTN_H, 0.0f, 0.5f, 0.0f, texGreen,
                currentChroma == MakeMeVtuberSettings.ChromaKey.GREEN,
                isInButton((float) mouseX, (float) mouseY, BTN_Y_GREEN, BTN_H));

        drawButton(BTN_Y_BLUE, BTN_H, 0.0f, 0.1f, 0.55f, texBlue,
                currentChroma == MakeMeVtuberSettings.ChromaKey.BLUE,
                isInButton((float) mouseX, (float) mouseY, BTN_Y_BLUE, BTN_H));

        drawButton(BTN_Y_TRANSPARENT, BTN_H, 0.2f, 0.2f, 0.25f, texTransparent,
                currentChroma == MakeMeVtuberSettings.ChromaKey.TRANSPARENT,
                isInButton((float) mouseX, (float) mouseY, BTN_Y_TRANSPARENT, BTN_H));

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(0.15f, 0.15f, 0.25f);
        drawRect(0, SECTION2_TITLE_Y - 5, WIN_W, 38);
        drawTexturedQuad(texMicTitle, 15, SECTION2_TITLE_Y + 2, 284, 27);

        float vol = MicrophoneCapture.getInstance().getVolume();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(0.2f, 0.2f, 0.2f);
        drawRect(230, SECTION2_TITLE_Y + 8, 90, 14);
        GL11.glColor3f(0.1f, 0.9f, 0.2f);
        drawRect(231, SECTION2_TITLE_Y + 9, 88 * vol, 12);

        for (int i = 0; i < micDeviceCount; i++) {
            int btnY = MIC_LIST_Y + i * (MIC_BTN_H + MIC_BTN_GAP);
            boolean selected = micDeviceNames[i] != null && micDeviceNames[i].equals(currentMic);
            boolean hovered = isInButton((float) mouseX, (float) mouseY, btnY, MIC_BTN_H);
            drawButton(btnY, MIC_BTN_H, 0.15f, 0.2f, 0.3f, texMicDevices[i], selected, hovered);
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        int chromaSelectedY = switch (currentChroma) {
            case GREEN -> BTN_Y_GREEN;
            case BLUE -> BTN_Y_BLUE;
            case TRANSPARENT -> BTN_Y_TRANSPARENT;
        };
        GL11.glColor3f(1.0f, 1.0f, 1.0f);
        drawCircle(10, chromaSelectedY + BTN_H / 2.0f, 4);

        for (int i = 0; i < micDeviceCount; i++) {
            if (micDeviceNames[i] != null && micDeviceNames[i].equals(currentMic)) {
                int btnY = MIC_LIST_Y + i * (MIC_BTN_H + MIC_BTN_GAP);
                GL11.glColor3f(0.2f, 1.0f, 0.4f);
                drawCircle(10, btnY + MIC_BTN_H / 2.0f, 4);
            }
        }

        int sliderBaseY = MIC_LIST_Y + micDeviceCount * (MIC_BTN_H + MIC_BTN_GAP) + 20;
        int sliderH = 20;
        int sliderGap = 58;
        int sliderTrackX = BTN_X + 130;
        int sliderTrackW = BTN_W - 130;
        int labelH = 33;
        int labelW = 346;

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        int threshY = sliderBaseY;
        drawTexturedQuad(texThresholdLabel, BTN_X, threshY, labelW, labelH);
        drawSlider(sliderTrackX, threshY + 4, sliderTrackW, sliderH,
                MakeMeVtuberSettings.getInstance().getMicThreshold(), 0.8f, 0.4f, 0.1f);

        int gainY = threshY + sliderGap;
        drawTexturedQuad(texGainLabel, BTN_X, gainY, labelW, labelH);
        drawSlider(sliderTrackX, gainY + 4, sliderTrackW, sliderH,
                MakeMeVtuberSettings.getInstance().getMicGain() / 5.0f, 0.9f, 0.5f, 0.1f);

        int widthY = gainY + sliderGap;
        drawTexturedQuad(texWidthLabel, BTN_X, widthY, labelW, labelH);
        drawSlider(sliderTrackX, widthY + 4, sliderTrackW, sliderH,
                MakeMeVtuberSettings.getInstance().getMouthWidth() / 3.0f, 0.2f, 0.7f, 0.3f);

        int heightY = widthY + sliderGap;
        drawTexturedQuad(texHeightLabel, BTN_X, heightY, labelW, labelH);
        drawSlider(sliderTrackX, heightY + 4, sliderTrackW, sliderH,
                MakeMeVtuberSettings.getInstance().getMouthHeight() / 3.0f, 0.2f, 0.7f, 0.3f);

        int intensityY = heightY + sliderGap;
        drawTexturedQuad(texIntensityLabel, BTN_X, intensityY, labelW, labelH);
        drawSlider(sliderTrackX, intensityY + 4, sliderTrackW, sliderH,
                MakeMeVtuberSettings.getInstance().getMouthIntensity() / 3.0f, 0.6f, 0.3f, 0.7f);

        int padY = intensityY + sliderGap + 10;
        drawTexturedQuad(texMouthPosLabel, BTN_X, padY, labelW, labelH);
        padY += 28;

        int padSize = 140;
        int padX = (WIN_W - padSize) / 2;

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glColor3f(0.25f, 0.2f, 0.15f);
        drawRect(padX, padY, padSize, padSize);
        GL11.glColor3f(0.5f, 0.5f, 0.6f);
        drawRectOutline(padX, padY, padSize, padSize);

        GL11.glColor3f(0.3f, 0.3f, 0.35f);
        drawRect(padX + padSize / 2.0f - 0.5f, padY, 1, padSize);
        drawRect(padX, padY + padSize / 2.0f - 0.5f, padSize, 1);

        float mouthPosX = MakeMeVtuberSettings.getInstance().getMouthOffsetX();
        float mouthPosY = MakeMeVtuberSettings.getInstance().getMouthOffsetY();
        float dotX = padX + (mouthPosX + 1.0f) * 0.5f * padSize;
        float dotY = padY + (mouthPosY + 1.0f) * 0.5f * padSize;
        GL11.glColor3f(1.0f, 0.3f, 0.3f);
        drawCircle(dotX, dotY, 6);
        GL11.glColor3f(1.0f, 1.0f, 1.0f);
        drawCircle(dotX, dotY, 3);

        this.padBoundsX = padX;
        this.padBoundsY = padY;
        this.padBoundsSize = padSize;
        this.sliderBounds = new int[][]{
            {sliderTrackX, threshY + 4, sliderTrackW, sliderH},
            {sliderTrackX, gainY + 4, sliderTrackW, sliderH},
            {sliderTrackX, widthY + 4, sliderTrackW, sliderH},
            {sliderTrackX, heightY + 4, sliderTrackW, sliderH},
            {sliderTrackX, intensityY + 4, sliderTrackW, sliderH},
        };
    }

    private void drawButton(int y, int h, float r, float g, float b, int labelTex,
                            boolean selected, boolean hovered) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        float mult = selected ? 1.4f : (hovered ? 1.1f : 0.8f);
        GL11.glColor3f(Math.min(r * mult, 1f), Math.min(g * mult, 1f), Math.min(b * mult, 1f));
        drawRect(BTN_X, y, BTN_W, h);

        if (selected) {
            GL11.glColor3f(1.0f, 1.0f, 1.0f);
            drawRectOutline(BTN_X, y, BTN_W, h);
        } else if (hovered) {
            GL11.glColor3f(0.5f, 0.5f, 0.6f);
            drawRectOutline(BTN_X, y, BTN_W, h);
        }

        int labelH = Math.min(h - 8, 28);
        int labelW = (int)(labelH * (420.0f / 40.0f));
        int labelY = y + (h - labelH) / 2;
        drawTexturedQuad(labelTex, BTN_X + 12, labelY, labelW, labelH);
    }

    private void drawSlider(int x, int y, int w, int h, float value, float r, float g, float b) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(0.18f, 0.18f, 0.22f);
        drawRect(x, y, w, h);
        GL11.glColor3f(r, g, b);
        drawRect(x, y, w * value, h);
        float handleX = x + w * value;
        GL11.glColor3f(1.0f, 1.0f, 1.0f);
        drawRect(handleX - 4, y - 4, 8, h + 8);
    }

    private void drawTexturedQuad(int texId, float x, float y, float w, float h) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private void drawRect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y); GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private void drawRectOutline(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y); GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private void drawCircle(float cx, float cy, float r) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 16; i++) {
            float angle = (float) (i * 2 * Math.PI / 16);
            GL11.glVertex2f(cx + r * (float) Math.cos(angle), cy + r * (float) Math.sin(angle));
        }
        GL11.glEnd();
    }
}

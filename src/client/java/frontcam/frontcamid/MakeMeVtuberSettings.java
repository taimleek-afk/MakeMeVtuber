package frontcam.frontcamid;

public class MakeMeVtuberSettings {
    private static final MakeMeVtuberSettings INSTANCE = new MakeMeVtuberSettings();

    public static MakeMeVtuberSettings getInstance() {
        return INSTANCE;
    }

    public enum ChromaKey {
        GREEN("Green", 0.0f, 1.0f, 0.0f, 1.0f),
        BLUE("Blue", 0.0f, 0.0f, 1.0f, 1.0f),
        TRANSPARENT("Transparent (experimental)", 0.0f, 0.0f, 0.0f, 0.0f);

        public final String label;
        public final float r, g, b, a;

        ChromaKey(String label, float r, float g, float b, float a) {
            this.label = label;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    private volatile ChromaKey currentChromaKey = ChromaKey.GREEN;
    private volatile boolean mouthEnabled = true;

    private volatile float micThreshold = 0.05f;

    private volatile float mouthOffsetX = 0.0f;
    private volatile float mouthOffsetY = 0.3f;

    private volatile float mouthWidth = 1.0f;
    private volatile float mouthHeight = 1.0f;

    private volatile float mouthIntensity = 1.0f;

    public ChromaKey getChromaKey() {
        return currentChromaKey;
    }

    public void setChromaKey(ChromaKey key) {
        this.currentChromaKey = key;
    }

    public boolean isMouthEnabled() {
        return mouthEnabled;
    }

    public void setMouthEnabled(boolean enabled) {
        this.mouthEnabled = enabled;
    }

    public float getMicThreshold() {
        return micThreshold;
    }

    public void setMicThreshold(float threshold) {
        this.micThreshold = Math.max(0.0f, Math.min(1.0f, threshold));
    }

    public float getMouthOffsetX() {
        return mouthOffsetX;
    }

    public float getMouthOffsetY() {
        return mouthOffsetY;
    }

    public void setMouthOffset(float x, float y) {
        this.mouthOffsetX = Math.max(-1.0f, Math.min(1.0f, x));
        this.mouthOffsetY = Math.max(-1.0f, Math.min(1.0f, y));
    }

    public float getMouthWidth() { return mouthWidth; }
    public float getMouthHeight() { return mouthHeight; }

    public void setMouthWidth(float w) {
        this.mouthWidth = Math.max(0.2f, Math.min(3.0f, w));
    }

    public void setMouthHeight(float h) {
        this.mouthHeight = Math.max(0.2f, Math.min(3.0f, h));
    }

    public float getMouthIntensity() { return mouthIntensity; }

    public void setMouthIntensity(float intensity) {
        this.mouthIntensity = Math.max(0.1f, Math.min(3.0f, intensity));
    }

    private volatile float micGain = 1.0f;

    public float getMicGain() { return micGain; }

    public void setMicGain(float gain) {
        this.micGain = Math.max(0.5f, Math.min(5.0f, gain));
    }
}

package frontcam.frontcamid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class MicrophoneCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("make-me-vtuber-mic");
    private static MicrophoneCapture instance;

    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean capturing = false;

    private volatile float currentVolume = 0.0f;
    private volatile float smoothVolume = 0.0f;
    private static final float VOLUME_RISE_SPEED = 0.4f;
    private static final float VOLUME_FALL_SPEED = 0.15f;

    private volatile Mixer.Info selectedMixer = null;

    public static synchronized MicrophoneCapture getInstance() {
        if (instance == null) {
            instance = new MicrophoneCapture();
        }
        return instance;
    }

    public List<String> getAvailableDevices() {
        List<String> devices = new ArrayList<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        AudioFormat format = getAudioFormat();
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);

        for (Mixer.Info info : mixers) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(targetInfo)) {
                    devices.add(info.getName());
                }
            } catch (Exception ignored) {}
        }
        return devices;
    }

    private Mixer.Info findMixer(String name) {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixers) {
            if (info.getName().equals(name)) {
                return info;
            }
        }
        return null;
    }

    public void selectDevice(String deviceName) {
        stop();
        selectedMixer = findMixer(deviceName);
        if (selectedMixer != null) {
            start();
        }
    }

    public void start() {
        if (capturing) return;
        if (selectedMixer == null) {
            List<String> devices = getAvailableDevices();
            if (!devices.isEmpty()) {
                selectedMixer = findMixer(devices.get(0));
            }
            if (selectedMixer == null) {
                LOGGER.warn("[MakeMeVtuber] No microphone available.");
                return;
            }
        }

        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            Mixer mixer = AudioSystem.getMixer(selectedMixer);

            line = (TargetDataLine) mixer.getLine(info);
            int bufferSize = 512 * format.getFrameSize();
            line.open(format, bufferSize);
            line.start();
            capturing = true;

            captureThread = new Thread(this::captureLoop, "MakeMeVtuber-Mic");
            captureThread.setDaemon(true);
            captureThread.start();

            LOGGER.info("[MakeMeVtuber] Microphone started: {}", selectedMixer.getName());
        } catch (Exception e) {
            LOGGER.error("[MakeMeVtuber] Failed to start microphone", e);
            capturing = false;
        }
    }

    public void stop() {
        capturing = false;
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        currentVolume = 0;
        smoothVolume = 0;
    }

    public float getVolume() {
        return smoothVolume;
    }

    public void updateSmoothing() {
        float target = currentVolume;
        if (target > smoothVolume) {
            smoothVolume += (target - smoothVolume) * VOLUME_RISE_SPEED;
        } else {
            smoothVolume += (target - smoothVolume) * VOLUME_FALL_SPEED;
        }
        smoothVolume = Math.max(0, Math.min(1, smoothVolume));
    }

    public String getSelectedDeviceName() {
        return selectedMixer != null ? selectedMixer.getName() : "None";
    }

    public boolean isCapturing() {
        return capturing;
    }

    private void captureLoop() {
        byte[] buffer = new byte[256];
        line.flush();

        while (capturing && !Thread.currentThread().isInterrupted()) {
            int bytesRead = line.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                currentVolume = Math.min(1.0f, calculateRMS(buffer, bytesRead) * MakeMeVtuberSettings.getInstance().getMicGain());
            }
        }
    }

    private float calculateRMS(byte[] buffer, int length) {
        long sum = 0;
        int samples = length / 2;

        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += (long) sample * sample;
        }

        double rms = Math.sqrt((double) sum / samples);
        float normalized = (float) (rms / 8000.0);
        return Math.min(1.0f, normalized);
    }

    private AudioFormat getAudioFormat() {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100, 16, 1, 2, 44100, false
        );
    }
}

package frontcam.frontcamid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PlayerModelData {

    public final List<BodyPart> bodyParts = new ArrayList<>();

    public int skinWidth = 64;
    public int skinHeight = 64;
    public ByteBuffer skinPixels;
    public boolean slimModel = false;

    public static class BodyPart {
        public String name;

        public float posX, posY, posZ;

        public float rotX, rotY, rotZ;

        public float scaleX = 1f, scaleY = 1f, scaleZ = 1f;

        public boolean visible = true;

        public final List<Cuboid> cuboids = new ArrayList<>();

        public final List<BodyPart> children = new ArrayList<>();

        public BodyPart(String name) {
            this.name = name;
        }
    }

    public static class Cuboid {
        public final List<Face> faces = new ArrayList<>();

        public float minX, minY, minZ;
        public float maxX, maxY, maxZ;
    }

    public static class Face {
        public final Vertex[] vertices = new Vertex[4];
        public float normalX, normalY, normalZ;
    }

    public static class Vertex {
        public float x, y, z;
        public float u, v;

        public Vertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

}

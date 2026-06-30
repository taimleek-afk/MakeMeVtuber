package frontcam.frontcamid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

public class PlayerDataExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger("make-me-vtuber-extractor");

    private static Field cubesField;
    private static Field childrenField;
    private static Field verticesField;
    private static Field normalField;
    private static Field posField;
    private static Field uField;
    private static Field vField;

    private static boolean reflectionInitialized = false;
    private static boolean vertexReflectionInitialized = false;

    // Cache the render state to avoid creating a new one every frame
    private static PlayerEntityRenderState cachedRenderState = null;

    private static void initBaseReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            for (Field f : ModelPart.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    cubesField = f;
                    cubesField.setAccessible(true);
                    break;
                }
            }
            for (Field f : ModelPart.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    childrenField = f;
                    childrenField.setAccessible(true);
                    break;
                }
            }
            LOGGER.info("[MakeMeVtuber] Base reflection OK: cubes={}, children={}",
                    cubesField != null ? cubesField.getName() : "NULL",
                    childrenField != null ? childrenField.getName() : "NULL");
        } catch (Exception e) {
            LOGGER.error("[MakeMeVtuber] Base reflection failed", e);
        }
    }

    public static PlayerModelData extract(MinecraftClient client, ClientPlayerEntity player) {
        initBaseReflection();
        PlayerModelData data = new PlayerModelData();

        PlayerEntityRenderer playerRenderer = (PlayerEntityRenderer) client.getEntityRenderDispatcher().getRenderer(player);
        if (playerRenderer == null) return data;

        PlayerEntityModel model = (PlayerEntityModel) playerRenderer.getModel();

        SkinTextures skinTextures = player.getSkinTextures();
        data.slimModel = (skinTextures.model() == SkinTextures.Model.SLIM);

        // In 1.21.2+, setupAnim requires a render state
        float tickDelta = client.getRenderTickCounter().getTickDelta(true);
        if (cachedRenderState == null) {
            cachedRenderState = playerRenderer.createRenderState();
        }
        playerRenderer.updateRenderState(player, cachedRenderState, tickDelta);
        model.setAngles(cachedRenderState);

        data.bodyParts.add(extractPart("head", model.head, false));
        data.bodyParts.add(extractPart("hat", model.hat, false));
        data.bodyParts.add(extractPart("body", model.body, false));
        data.bodyParts.add(extractPart("right_arm", model.rightArm, false));
        data.bodyParts.add(extractPart("left_arm", model.leftArm, false));
        data.bodyParts.add(extractPart("right_leg", model.rightLeg, false));
        data.bodyParts.add(extractPart("left_leg", model.leftLeg, false));
        data.bodyParts.add(extractPart("jacket", model.jacket, false));
        data.bodyParts.add(extractPart("left_sleeve", model.leftSleeve, false));
        data.bodyParts.add(extractPart("right_sleeve", model.rightSleeve, false));
        data.bodyParts.add(extractPart("left_pants", model.leftPants, false));
        data.bodyParts.add(extractPart("right_pants", model.rightPants, false));

        extractSkinTexture(client, player, data);

        return data;
    }

    private static PlayerModelData.BodyPart extractPart(String name, ModelPart modelPart, boolean invertPitch) {
        PlayerModelData.BodyPart part = new PlayerModelData.BodyPart(name);

        part.posX = modelPart.pivotX;
        part.posY = modelPart.pivotY;
        part.posZ = modelPart.pivotZ;
        part.rotX = invertPitch ? -modelPart.pitch : modelPart.pitch;
        part.rotY = modelPart.yaw;
        part.rotZ = modelPart.roll;
        part.scaleX = modelPart.xScale;
        part.scaleY = modelPart.yScale;
        part.scaleZ = modelPart.zScale;
        part.visible = modelPart.visible;

        if (cubesField != null) {
            try {
                @SuppressWarnings("unchecked")
                List<ModelPart.Cuboid> cubes = (List<ModelPart.Cuboid>) cubesField.get(modelPart);
                for (ModelPart.Cuboid cube : cubes) {
                    PlayerModelData.Cuboid cuboid = extractCuboid(cube);
                    part.cuboids.add(cuboid);
                }
            } catch (Exception e) {
                LOGGER.error("[MakeMeVtuber] Failed to extract cubes for: {}", name, e);
            }
        }

        if (childrenField != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, ModelPart> children = (Map<String, ModelPart>) childrenField.get(modelPart);
                for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
                    part.children.add(extractPart(entry.getKey(), entry.getValue(), false));
                }
            } catch (Exception ignored) {}
        }

        return part;
    }

    private static void initVertexReflection(ModelPart.Quad quad) {
        if (vertexReflectionInitialized) return;
        vertexReflectionInitialized = true;
        try {
            Class<?> quadClass = quad.getClass();
            for (Field f : quadClass.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType().isArray() && verticesField == null) {
                    verticesField = f;
                } else if (f.getType() == org.joml.Vector3f.class && normalField == null) {
                    normalField = f;
                }
            }
            LOGGER.info("[MakeMeVtuber] Vertex reflection: vertices={}, normal={}",
                    verticesField != null ? verticesField.getName() : "NULL",
                    normalField != null ? normalField.getName() : "NULL");

            if (verticesField != null) {
                Object[] verts = (Object[]) verticesField.get(quad);
                if (verts != null && verts.length > 0 && verts[0] != null) {
                    Class<?> vtxClass = verts[0].getClass();
                    for (Field f : vtxClass.getDeclaredFields()) {
                        f.setAccessible(true);
                        if (f.getType() == org.joml.Vector3f.class && posField == null) {
                            posField = f;
                        } else if (f.getType() == float.class) {
                            if (uField == null) {
                                uField = f;
                            } else if (vField == null) {
                                vField = f;
                            }
                        }
                    }
                    LOGGER.info("[MakeMeVtuber] Vertex fields: pos={}, u={}, v={}",
                            posField != null ? posField.getName() : "NULL",
                            uField != null ? uField.getName() : "NULL",
                            vField != null ? vField.getName() : "NULL");
                }
            }
        } catch (Exception e) {
            LOGGER.error("[MakeMeVtuber] Vertex reflection failed", e);
        }
    }

    private static PlayerModelData.Cuboid extractCuboid(ModelPart.Cuboid cube) {
        PlayerModelData.Cuboid cuboid = new PlayerModelData.Cuboid();
        cuboid.minX = cube.minX;
        cuboid.minY = cube.minY;
        cuboid.minZ = cube.minZ;
        cuboid.maxX = cube.maxX;
        cuboid.maxY = cube.maxY;
        cuboid.maxZ = cube.maxZ;

        // In Fabric/Yarn, ModelPart.Cuboid has a 'sides' field (Quad[])
        ModelPart.Quad[] quads = cube.sides;
        if (quads != null) {
            for (ModelPart.Quad quad : quads) {
                if (quad == null) continue;
                if (!vertexReflectionInitialized) {
                    initVertexReflection(quad);
                }
                PlayerModelData.Face face = extractFace(quad);
                if (face != null) {
                    cuboid.faces.add(face);
                }
            }
        }

        if (cuboid.faces.isEmpty()) {
            generateFacesFromAABB(cuboid);
        }

        return cuboid;
    }

    private static PlayerModelData.Face extractFace(ModelPart.Quad quad) {
        if (verticesField == null || normalField == null || posField == null || uField == null || vField == null) {
            return null;
        }

        try {
            Object[] vertices = (Object[]) verticesField.get(quad);
            org.joml.Vector3f normal = (org.joml.Vector3f) normalField.get(quad);

            if (vertices == null || vertices.length < 4) return null;

            PlayerModelData.Face face = new PlayerModelData.Face();
            face.normalX = normal.x();
            face.normalY = normal.y();
            face.normalZ = normal.z();

            for (int i = 0; i < 4; i++) {
                Object vtx = vertices[i];
                if (vtx == null) return null;

                org.joml.Vector3f pos = (org.joml.Vector3f) posField.get(vtx);
                float u = uField.getFloat(vtx);
                float v = vField.getFloat(vtx);

                face.vertices[i] = new PlayerModelData.Vertex(pos.x(), pos.y(), pos.z(), u, v);
            }

            return face;
        } catch (Exception e) {
            return null;
        }
    }

    private static void generateFacesFromAABB(PlayerModelData.Cuboid cuboid) {
        float x0 = cuboid.minX, y0 = cuboid.minY, z0 = cuboid.minZ;
        float x1 = cuboid.maxX, y1 = cuboid.maxY, z1 = cuboid.maxZ;

        addFace(cuboid, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1, 0,0,1);
        addFace(cuboid, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0, 0,0,-1);
        addFace(cuboid, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, 0,-1,0);
        addFace(cuboid, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, 0,1,0);
        addFace(cuboid, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1, 1,0,0);
        addFace(cuboid, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0, -1,0,0);
    }

    private static void addFace(PlayerModelData.Cuboid cuboid,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float nx, float ny, float nz) {
        PlayerModelData.Face face = new PlayerModelData.Face();
        face.normalX = nx;
        face.normalY = ny;
        face.normalZ = nz;
        face.vertices[0] = new PlayerModelData.Vertex(x0, y0, z0, 0, 0);
        face.vertices[1] = new PlayerModelData.Vertex(x1, y1, z1, 1, 0);
        face.vertices[2] = new PlayerModelData.Vertex(x2, y2, z2, 1, 1);
        face.vertices[3] = new PlayerModelData.Vertex(x3, y3, z3, 0, 1);
        cuboid.faces.add(face);
    }

    private static void extractSkinTexture(MinecraftClient client, ClientPlayerEntity player, PlayerModelData data) {
        try {
            SkinTextures skinTextures = player.getSkinTextures();
            Identifier textureLocation = skinTextures.texture();

            AbstractTexture texture = client.getTextureManager().getTexture(textureLocation);
            int texId = texture.getGlId();
            if (texId <= 0) return;

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

            if (width <= 0 || height <= 0) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                return;
            }

            data.skinWidth = width;
            data.skinHeight = height;

            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            pixels.position(0);
            data.skinPixels = pixels;

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } catch (Exception e) {
            LOGGER.error("[MakeMeVtuber] Failed to extract skin texture", e);
        }
    }
}

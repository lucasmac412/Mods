package br.com.celestialdragon.client;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;

/** Pointed scales and talons, baked once into vanilla model cubes without an extra renderer. */
final class DragonGeometry {
    private DragonGeometry() { }

    static ModelPart finish(ModelPart root) {
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            String name = path.substring(path.lastIndexOf('/') + 1);
            boolean spine = name.startsWith("point_spine");
            boolean scale = name.startsWith("point_scale");
            boolean talon = name.startsWith("point_talon");
            if (!(spine || scale || talon)) return;
            for (int face = 0; face < cube.polygons.length; face++) {
                var polygon = cube.polygons[face];
                var vertices = polygon.vertices();
                var shaped = new ModelPart.Vertex[vertices.length];
                for (int i = 0; i < vertices.length; i++) {
                    var vertex = vertices[i];
                    float x = vertex.x(), y = vertex.y(), z = vertex.z();
                    if (spine && y == cube.minY) {
                        x = (cube.minX + cube.maxX) * 0.5F;
                        z = cube.minZ + (cube.maxZ - cube.minZ) * 0.86F;
                    } else if (scale && z == cube.maxZ) {
                        x = (cube.minX + cube.maxX) * 0.5F;
                        y = cube.minY;
                    } else if (talon && z == cube.minZ) {
                        x = (cube.minX + cube.maxX) * 0.5F;
                        y = cube.maxY;
                    }
                    shaped[i] = new ModelPart.Vertex(x, y, z, vertex.u(), vertex.v());
                }
                // A tapered cube has triangular sides and one collapsed end. Recompute
                // their sloping normals, keeping the original for the zero-area end cap.
                Vector3f normal = new Vector3f(polygon.normal());
                for (int i = 1; i < shaped.length - 1; i++) {
                    var a = shaped[0]; var b = shaped[i]; var c = shaped[i + 1];
                    Vector3f cross = new Vector3f(b.x() - a.x(), b.y() - a.y(), b.z() - a.z())
                            .cross(c.x() - a.x(), c.y() - a.y(), c.z() - a.z());
                    if (cross.lengthSquared() > 0.000001F) {
                        if (cross.dot(polygon.normal()) < 0) cross.negate();
                        normal.set(cross).normalize();
                        break;
                    }
                }
                cube.polygons[face] = new ModelPart.Polygon(shaped, normal);
            }
        });
        return root;
    }
}

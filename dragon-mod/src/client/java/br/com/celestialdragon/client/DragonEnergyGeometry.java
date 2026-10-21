package br.com.celestialdragon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Solid energy cores, translucent auras and electric arcs; visible even with minimal particles. */
final class DragonEnergyGeometry {
    private DragonEnergyGeometry() { }
    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 point, int color) {
        out.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(color);
    }
    static void sphere(PoseStack.Pose pose, VertexConsumer out, Vec3 center, double radius, int color) {
        for (int latitude = 0; latitude < 12; latitude++) for (int longitude = 0; longitude < 24; longitude++) {
            double a = Math.PI * latitude / 12, b = Math.PI * (latitude + 1) / 12;
            double c = Math.PI * 2 * longitude / 24, d = Math.PI * 2 * (longitude + 1) / 24;
            vertex(pose, out, polar(center, radius, a, c), color);
            vertex(pose, out, polar(center, radius, b, c), color);
            vertex(pose, out, polar(center, radius, b, d), color);
            vertex(pose, out, polar(center, radius, a, d), color);
        }
    }
    private static Vec3 polar(Vec3 center, double radius, double latitude, double longitude) {
        return center.add(Math.sin(latitude) * Math.cos(longitude) * radius,
                Math.cos(latitude) * radius, Math.sin(latitude) * Math.sin(longitude) * radius);
    }
    static void tube(PoseStack.Pose pose, VertexConsumer out, Vec3 start, Vec3 end, double radius, int color) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 right = direction.cross(Vec3.Y_AXIS).normalize();
        if (right.lengthSqr() < 0.01) right = Vec3.X_AXIS;
        Vec3 up = right.cross(direction).normalize();
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20, b = (i + 1) * Math.PI * 2 / 20;
            Vec3 first = right.scale(Math.cos(a) * radius).add(up.scale(Math.sin(a) * radius));
            Vec3 second = right.scale(Math.cos(b) * radius).add(up.scale(Math.sin(b) * radius));
            vertex(pose, out, start.add(first), color); vertex(pose, out, end.add(first), color);
            vertex(pose, out, end.add(second), color); vertex(pose, out, start.add(second), color);
        }
    }
    static void beam(PoseStack.Pose pose, VertexConsumer out, Vec3 start, Vec3 end, float age, boolean active) {
        double pulse = 1 + Math.sin(age * 0.75) * 0.06;
        sphere(pose, out, start, 2.7 * pulse, 0x2455CCFF);
        sphere(pose, out, start, 1.7 * pulse, 0x8077EEFF);
        sphere(pose, out, start, 0.95 * pulse, 0xE8EFFFFF);
        if (!active) return;
        tube(pose, out, start, end, 2.2 * pulse, 0x2055BBFF);
        tube(pose, out, start, end, 1.25 * pulse, 0x8866EEFF);
        tube(pose, out, start, end, 0.58, 0xF0F5FFFF);
        sphere(pose, out, end, 2.1, 0x5577DDFF);
    }
    static void electricity(PoseStack.Pose pose, VertexConsumer out, Vec3 center, double radius, float age) {
        for (int arc = 0; arc < 8; arc++) {
            Vec3 previous = null;
            for (int step = 0; step < 9; step++) {
                double angle = arc * Math.PI / 4 + step * 0.24;
                double flutter = Math.sin(step * 8.7 + arc * 3.2 + Math.floor(age / 2)) * radius * 0.15;
                Vec3 next = center.add(Math.cos(angle) * (radius + flutter),
                        (step / 8.0 - 0.5) * radius * 1.7, Math.sin(angle) * (radius + flutter));
                if (previous != null) tube(pose, out, previous, next, 0.025 + radius * 0.004, 0xD0ACF7FF);
                previous = next;
            }
        }
    }
}

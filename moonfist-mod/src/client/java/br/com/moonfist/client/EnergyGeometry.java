package br.com.moonfist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Bounded, original geometry. No full-screen post-process, texture downloads or shader pack required. */
public final class EnergyGeometry {
    private EnergyGeometry(){}
    private static void vertex(PoseStack.Pose p,VertexConsumer v,Vec3 a,int color) {
        v.addVertex(p.pose(),(float)a.x,(float)a.y,(float)a.z).setColor(color);
    }
    private static void quad(PoseStack.Pose p,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color) {
        vertex(p,v,a,color);vertex(p,v,b,color);vertex(p,v,c,color);vertex(p,v,d,color);
        // Energy ribbons must be visible both behind the caster and facing the attack.
        vertex(p,v,d,color);vertex(p,v,c,color);vertex(p,v,b,color);vertex(p,v,a,color);
    }
    public static void cube(PoseStack.Pose p,VertexConsumer v,double x,double y,double z,double w,double h,double d,int color) {
        Vec3 a=new Vec3(x,y,z),b=a.add(w,0,0),c=a.add(w,h,0),e=a.add(0,h,0);
        Vec3 f=a.add(0,0,d),g=b.add(0,0,d),i=c.add(0,0,d),j=e.add(0,0,d);
        quad(p,v,a,e,c,b,color);quad(p,v,f,g,i,j,color);quad(p,v,a,b,g,f,color);
        quad(p,v,e,j,i,c,color);quad(p,v,a,f,j,e,color);quad(p,v,b,c,i,g,color);
    }
    public static void ring(PoseStack.Pose p,VertexConsumer v,Vec3 center,Vec3 normal,double radius,double width,int color) {
        Vec3 u=normal.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize();
        Vec3 w=normal.normalize().cross(u).normalize();
        for(int i=0;i<40;i++) {
            double a=i*Math.PI*2/40,b=(i+1)*Math.PI*2/40;
            Vec3 da=u.scale(Math.cos(a)).add(w.scale(Math.sin(a))),db=u.scale(Math.cos(b)).add(w.scale(Math.sin(b)));
            quad(p,v,center.add(da.scale(radius-width)),center.add(da.scale(radius+width)),
                center.add(db.scale(radius+width)),center.add(db.scale(radius-width)),color);
        }
    }
    public static void streak(PoseStack.Pose p,VertexConsumer v,Vec3 a,Vec3 b,double width,int color) {
        Vec3 direction=b.subtract(a).normalize();Vec3 side=direction.cross(new Vec3(0,0,1));
        if(side.lengthSqr()<.01)side=direction.cross(new Vec3(0,1,0));side=side.normalize().scale(width);
        quad(p,v,a.subtract(side),a.add(side),b.add(side),b.subtract(side),color);
    }
    public static void glove(PoseStack.Pose p,VertexConsumer v,boolean left) {
        double center=(left?1:-1)/16.0;
        cube(p,v,center-.14,.40,-.14,.28,.27,.28,0xff19263c);
        cube(p,v,center-.15,.45,-.15,.30,.055,.30,0xff41a6b5);
        cube(p,v,center-.145,.59,-.17,.29,.07,.08,0xff68f6f0);
        for(int i=0;i<3;i++)cube(p,v,center-.125+i*.09,.61,-.19,.065,.075,.05,0xffbdfffa);
        cube(p,v,center-.155,.42,-.05,.025,.18,.04,0xffed69cb);
        cube(p,v,center+.13,.42,-.05,.025,.18,.04,0xffed69cb);
    }
}

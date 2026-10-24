package br.com.moonfist.client;

import br.com.moonfist.combat.CombatService;
import br.com.moonfist.combat.CombatState;
import br.com.moonfist.combat.Move;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.LivingEntity;

public final class CombatHud {
    private CombatHud(){}
    public static void draw(GuiGraphicsExtractor g,DeltaTracker delta) {
        var c=Minecraft.getInstance();if(c.player==null||!CombatService.equipped(c.player)||c.gui.hud.isHidden()||c.gui.screen()!=null)return;
        var s=CombatView.get(c.player.getId());if(s==null)return;
        int x=16,y=g.guiHeight()-82,w=160;
        g.fill(x-5,y-6,x+w+5,y+43,0xcc0d1424);
        g.text(c.font,"LUA AZUL · "+CombatState.stanceName(s.stance()),x,y,0xffb4fbff,false);
        meter(g,x,y+13,w,7,s.stamina()/100,0xff49e8d5);
        meter(g,x,y+23,w,4,s.focus()/100,0xffe86acd);
        String status=s.guard()?"Defesa · G":Move.byId(s.move())==Move.IDLE?"Alt esquiva · C alvo · Z postura":Move.byId(s.move()).title;
        g.text(c.font,status,x,y+31,0xfff0e2f0,false);
        String skills="R "+(s.waveCooldown()>0?String.format(java.util.Locale.ROOT,"%.1fs",s.waveCooldown()/20f):"pronto")
            +"  |  Caps "+(s.voidCooldown()>0?String.format(java.util.Locale.ROOT,"%.1fs",s.voidCooldown()/20f):s.focus()<60?"foco 60":"pronto");
        g.text(c.font,skills,x,y-18,0xffb7afce,false);
        if(c.level.getEntity(s.lockTarget()) instanceof LivingEntity target) {
            int tx=g.guiWidth()/2-100;g.fill(tx-5,28,tx+205,65,0xd0141021);
            g.text(c.font,target.getDisplayName(),tx,32,0xffffe8c5,false);
            meter(g,tx,45,200,6,target.getHealth()/target.getMaxHealth(),0xffc84672);
            meter(g,tx,55,200,3,s.targetPosture()/100,0xffe5bf61);
        }
    }
    private static void meter(GuiGraphicsExtractor g,int x,int y,int width,int height,float fill,int color) {
        g.fill(x-1,y-1,x+width+1,y+height+1,0xff4a4562);g.fill(x,y,x+width,y+height,0xff1c2537);
        g.fill(x,y,x+Math.round(width*Math.max(0,Math.min(1,fill))),y+height,color);
    }
}

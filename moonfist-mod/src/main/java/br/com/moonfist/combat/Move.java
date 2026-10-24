package br.com.moonfist.combat;

/** Tick-defined, original choreography. Hit windows are shared by gameplay and rendering. */
public enum Move {
    IDLE("Pronto",0,0,0,0,0),
    JAB("Agulha lunar",11,8,2.5f,2.8f,3),
    CROSS("Punho cruzado",13,9,3,2.9f,4),
    LOW_KICK("Chute crescente",18,14,4.5f,3.2f,7),
    PALM("Palma azul",15,10,3.5f,3,5),
    HOOK("Gancho do eclipse",17,12,4,3.1f,6),
    SPIN_KICK("Roda lunar",22,18,6,3.5f,9),
    HAMMER("Punho do firmamento",21,16,6,3,9),
    UPPERCUT("Ascensão",24,18,7,3,10),
    SLAM("Queda celeste",29,24,9,3.8f,13),
    QUICK_HEAVY("Chute veloz",20,18,5.5f,3.2f,7),
    MID_HEAVY("Palma destruidora",24,22,7,3.5f,10),
    POWER_HEAVY("Impacto do vazio",32,28,10,3.8f,15),
    WAVE("Onda destruidora",28,22,5.5f,14,7),
    VOID("Cortes do vazio",40,35,1.75f,9,14,16,18,20,22,24,26,28),
    DODGE("Passo fantasma",12,20,0,0),
    GUARD_BREAK("Guarda quebrada",25,0,0,0),
    STAGGER("Postura quebrada",30,0,0,0);

    public final String title;
    public final int duration, cost;
    public final float damage, range;
    public final int[] hits;
    Move(String title,int duration,int cost,float damage,float range,int... hits) {
        this.title=title;this.duration=duration;this.cost=cost;this.damage=damage;this.range=range;this.hits=hits;
    }
    public boolean attack() { return damage>0; }
    public boolean light() { return ordinal()>=JAB.ordinal() && ordinal()<=SLAM.ordinal(); }
    public int hitStage(int age) { for(int i=0;i<hits.length;i++) if(hits[i]==age)return i;return -1; }
    public static Move light(int stance,int combo) {
        return values()[1+Math.floorMod(stance,3)*3+Math.floorMod(combo,3)];
    }
    public static Move heavy(int stance) { return values()[QUICK_HEAVY.ordinal()+Math.floorMod(stance,3)]; }
    public static Move byId(int id) { return id>=0&&id<values().length?values()[id]:IDLE; }
}

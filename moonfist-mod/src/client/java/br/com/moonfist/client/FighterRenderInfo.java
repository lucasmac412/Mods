package br.com.moonfist.client;

public interface FighterRenderInfo {
    int moonfist$id();
    boolean moonfist$active();
    float moonfist$partial();
    void moonfist$set(int id,boolean active,float partial);
}

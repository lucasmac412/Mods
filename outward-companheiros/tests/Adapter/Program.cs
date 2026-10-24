// Controlled stand-ins exercise the real Caravan.cs adapter, not the Outward engine.
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using Companheiros;

public sealed class Area { public int ID; }
public sealed class AreaManager
{
    public enum AreaEnum { }
    public static AreaManager Instance;
    public Area CurrentArea;
    public HashSet<int> Available = new HashSet<int>(Policy.Cities);
    public Area GetArea(AreaEnum id) { return Available.Contains((int)id) ? new Area { ID=(int)id } : null; }
    public static implicit operator bool(AreaManager value) { return value!=null; }
}
public sealed class QuestEventManager
{
    public static QuestEventManager Instance;
    public HashSet<string> Events = new HashSet<string>();
    public bool HasQuestEvent(string id) { return Events.Contains(id); }
}
public sealed class MerchantFastTravel
{
    public bool LevantAllowed=true;
    private bool CheckIfLevantBlocked() { return LevantAllowed; }
    public static implicit operator bool(MerchantFastTravel value) { return value!=null; }
}
public sealed class TravelData
{
    public AreaManager.AreaEnum From, Destination;
    public int SilverCost, RationCost;
    public bool Unidirectional;
}
namespace HarmonyLib
{
    internal static class AccessTools
    {
        public static MethodInfo Method(Type type,string name,Type[] args)
        { return type.GetMethod(name,BindingFlags.Instance|BindingFlags.NonPublic|BindingFlags.Public,null,args,null); }
    }
}
internal static class Program
{
    private static int checks;
    private static void Check(bool ok,string message) { checks++; if(!ok) throw new Exception(message); }
    private static void Main()
    {
        Check(Caravan.Build(null)==null,"No menu without a merchant/loaded world");
        var merchant=new MerchantFastTravel();
        var world=AreaManager.Instance=new AreaManager();
        var quests=QuestEventManager.Instance=new QuestEventManager();
        var unrelated=new TravelData { SilverCost=51,RationCost=7 };
        Check(!Caravan.Owns(unrelated),"Never tag unrelated native travel");
        string[] gates={"lDHL_XMS7kKEs0uOqrLQjw","WvGjemEntk6quLjy4rLrJQ","vW4sarzBGkalTwy_KhGI6A","eYmZGb_BJ0qAtpcwrndhTg"};
        foreach(int origin in Policy.Cities.Concat(new[]{101,115,602,554}))
        for(int mask=0;mask<32;mask++)
        {
            world.CurrentArea=new Area{ID=origin};quests.Events.Clear();
            for(int bit=0;bit<4;bit++) if((mask&(1<<bit))!=0) quests.Events.Add(gates[bit]);
            merchant.LevantAllowed=(mask&16)!=0;
            var host=Caravan.Build(merchant);var guest=Caravan.Build(merchant);
            if(!Policy.IsCity(origin)) { Check(host==null&&guest==null,"Wilderness vanilla fallback");continue; }
            int[] expected=Policy.Destinations(origin,(mask&3)!=0,(mask&4)!=0,merchant.LevantAllowed,(mask&8)!=0);
            Check(host.Select(r=>(int)r.Destination).SequenceEqual(expected),"Correct story events -> routes");
            Check(host.Select(r=>(int)r.Destination).SequenceEqual(guest.Select(r=>(int)r.Destination)),"Both adapters show same list");
            foreach(var route in host.Concat(guest))
            {
                Check(route.SilverCost==200&&route.RationCost==3,"Exact native debitable amounts");
                Check((int)route.From==origin,"Correct departure scene");
                Check(Caravan.Owns(route),"Only generated route gets fixed silver hook");
            }
            Check(unrelated.SilverCost==51&&unrelated.RationCost==7,"Native unrelated data unchanged");
        }
        world.CurrentArea=new Area{ID=100};world.Available.Remove(400);
        Check(Caravan.Build(merchant).All(r=>(int)r.Destination!=400),"Missing city cannot be offered");
        Check(!Caravan.Owns(null),"Null route guard");
        world.CurrentArea=null;Check(Caravan.Build(merchant)==null,"Loading fallback");
        Console.WriteLine(checks+" real adapter checks passed against controlled stand-ins; no live game or network test.");
    }
}

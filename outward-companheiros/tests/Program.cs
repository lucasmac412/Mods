using System;
using System.Linq;
using Companheiros;

internal static class Program
{
    private static int checks;
    private static void Check(bool condition, string message)
    {
        checks++; if (!condition) throw new Exception(message);
    }
    private static void Main()
    {
        Check(Policy.Silver == 200 && Policy.Rations == 3, "Tarifa fixa nativa");
        // Every origin, every combination of story gates, host/guest computation parity.
        foreach (int origin in Policy.Cities.Concat(new[] { 0,101,201,301,401,501,600,602,552,554,115,999 }))
        for (int mask=0; mask<16; mask++)
        {
            bool cierzo = (mask&1)!=0, berg = (mask&2)!=0, levant = (mask&4)!=0, sirocco = (mask&8)!=0;
            int[] host = Policy.Destinations(origin,cierzo,berg,levant,sirocco);
            int[] guest = Policy.Destinations(origin,cierzo,berg,levant,sirocco);
            Check(host.SequenceEqual(guest), "Host/guest deterministic destinations");
            Check(!host.Contains(origin), "No self travel");
            Check(host.Distinct().Count()==host.Length, "No duplicate destinations");
            Check(host.All(Policy.IsCity), "Only actual city IDs");
            foreach (int city in Policy.Cities)
            {
                bool expected = Policy.IsCity(origin) && city!=origin && !(city==100&&cierzo)
                    && !(city==500&&berg) && !(city==300&&!levant) && !(city==601&&!sirocco);
                Check(host.Contains(city)==expected,"Missing or forbidden route " + origin+" -> "+city);
            }
        }
        foreach (string uid in new[] { "", "alice", "bob", "0000-test", "áç☃", new string('x',1000) })
        {
            Check(Policy.ColorIndex(uid)>=0 && Policy.ColorIndex(uid)<6,"Valid color index");
            Check(Policy.ColorIndex(uid)==Policy.ColorIndex(new string(uid.ToCharArray())),"Stable hash");
        }
        Check(Policy.ColorIndex("alice")==5,"Fixed cross-process FNV color");
        Check(Policy.DisplayName(" ")=="Aventureiro","Unnamed character");
        Check(Policy.DisplayName(new string('a',200)).Length==32,"Bounded labels");
        Check(!Policy.DisplayName("joão\r\n\t irmão").Any(char.IsControl),"No multiline injection");
        Check(Policy.DisplayName("<color=red>irmão</color>").Contains("<"),"Plain text; UI richText disabled");
        Console.WriteLine(checks + " checks passed: route gates, solo/host/guest parity, marker identity and labels.");
        Console.WriteLine("These are executable rules tests, NOT a live Outward multiplayer session.");
    }
}

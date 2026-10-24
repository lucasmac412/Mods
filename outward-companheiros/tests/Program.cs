using System;
using System.Linq;
using System.Globalization;
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
        SearchChecks();
        Console.WriteLine(checks + " checks passed: routes, marker identity, accent-insensitive search, native filter composition and independent queries.");
        Console.WriteLine("These are executable rules tests, NOT a live Outward multiplayer session.");
    }

    private static void SearchChecks()
    {
        var query = new ItemSearchQuery();
        Check(query.IsEmpty && query.Matches(true,"Espada de Ferro"),"Default inventory unfiltered");
        Check(!query.Matches(false,"Espada de Ferro"),"Empty query never bypasses a native category/permission filter");
        query.Set("POCAO");
        Check(query.Matches(true,"Grande Poção de Vida"),"Case and accent insensitive");
        Check(query.Matches(true,"Poça\u0303o de Mana"),"Decomposed accents");
        Check(!query.Matches(true,"Espada de Ferro"),"Nonmatching item hidden");
        Check(!query.Matches(false,"Poção de Vida"),"Search cannot resurrect a native-filtered item");
        Check(!query.Matches(true,""),"Unnamed item cannot match an active query");
        query.Set("  vida\tGRANDE\u00a0");
        Check(query.Matches(true,"Grande Poção de Vida"),"All words, any order, Unicode whitespace");
        Check(!query.Matches(true,"Pequena Poção de Vida"),"All search terms required");
        query.Set("pocao mana");
        Check(!query.Matches(true,"Grande Poção de Vida"),"Different second term");
        query.Set("[.*]");
        Check(!query.Matches(true,"Qualquer item"),"No regex/wildcard interpretation");
        Check(query.Matches(true,"Item [.*] especial"),"Punctuation remains literal");
        query.Set("rações");Check(query.Matches(true,"Racoes de Viagem"),"Accented query against plain label");
        query.Set("flecha");
        var culture=CultureInfo.CurrentCulture;
        try { CultureInfo.CurrentCulture=new CultureInfo("tr-TR");Check(query.Matches(true,"FLECHA DE FERRO"),"Invariant matching under another OS culture"); }
        finally { CultureInfo.CurrentCulture=culture; }
        query.Set("\ud800pocao\udc00");Check(query.Matches(true,"Poção"),"Malformed UTF-16 cannot crash normalization");
        query.Set(new string('x',ItemSearchQuery.Limit)+"nao existe");
        Check(query.Matches(true,new string('x',ItemSearchQuery.Limit)),"Bounded query");
        query.Set(" \t\r\n");
        Check(query.IsEmpty&&query.Matches(true,"Antes oculto"),"Clearing restores previously hidden items");
        Check(!query.Matches(false,"Antes oculto"),"Clearing preserves native exclusions");
        var chest=new ItemSearchQuery();var inventory=new ItemSearchQuery();
        chest.Set("pocao");inventory.Set("ferro");
        Check(chest.Matches(true,"Poção")&&!inventory.Matches(true,"Poção"),"Chest and inventory queries independent");
        chest.Set("");Check(inventory.Matches(true,"Espada de Ferro")&&!inventory.Matches(true,"Poção"),"Clearing chest does not clear inventory");
        query.Set("ferro");
        for(int i=0;i<1200;i++)
        {
            Check(query.Matches(true,"Ferro "+i),"Bounded cache churn keeps matching");
            Check(!query.Matches(true,"Madeira "+i),"Bounded cache churn keeps exclusions");
        }
        query.Set("madeira");Check(query.Matches(true,"Madeira 1199"),"Cached names survive query changes");
    }
}

using System;
using System.IO;
using System.Linq;
using Mono.Cecil;

internal static class Program
{
    private static int checks;
    private static void Check(bool ok,string message) { checks++; if(!ok) throw new Exception(message); }
    private static void Main(string[] args)
    {
        if(args.Length!=2) throw new Exception("usage: Contracts <game reference Assembly-CSharp.dll> <built plugin.dll>");
        using var game=AssemblyDefinition.ReadAssembly(args[0]);
        using var plugin=AssemblyDefinition.ReadAssembly(args[1]);
        var required = new[] {
            ("MapDisplay","Show",1), ("MapDisplay","OnHide",0), ("MapDisplay","UpdateWorldMarkers",0),
            ("MerchantFastTravel","GetAvailableDestinationData",0), ("MerchantFastTravel","CheckIfLevantBlocked",0),
            ("MerchantFastTravel","AllPlayersInProximity",0), ("MerchantFastTravel","CanFastTravel",2),
            ("MerchantFastTravel","ProceedFastTravel",2), ("TravelData","GetRealSilverCost",0),
            ("MapWorldMarker","CalculateMapPosition",3), ("CharacterManager","SendMerchantFastTravel",2),
            ("CharacterManager","SendMerchantFastTravelRPC",5),
            ("InventoryContentDisplay","Show",0), ("StashPanel","Show",0),
            ("EnvironmentItemDisplay","Show",0), ("EnvironmentItemDisplay","Show",1),
            ("ItemListDisplay","MatchFilter",1), ("ContainerDisplay","Refresh",0),
            ("ContainerDisplay","TakeAllItems",1), ("Panel","OnHide",0),
            ("CharacterUI","get_IsInputFieldFocused",0), ("CharacterUI","get_IgnoreMenuInputs",0),
            ("EnvironmentItemDisplay","get_IsEmpty",0), ("CharacterUI","SetSelectedGameObject",1)
        };
        foreach(var (type,name,count) in required)
        {
            var t=game.MainModule.GetType(type);
            Check(t!=null && t.Methods.Any(m=>m.Name==name&&m.Parameters.Count==count),"Missing method "+type+"."+name);
        }
        foreach(var (type,name) in new[] { ("MapDisplay","m_zoomLevelSmooth"),("MapDisplay","m_currentMapSceneID"),
            ("MapDisplay","m_currentAreaHasMap"),("MapDisplay","WorldMapMarkers"),
            ("TravelData","From"),("TravelData","Destination"),("TravelData","RationCost"),("TravelData","SilverCost"),
            ("InventoryContentDisplay","m_pouchDisplay"),("InventoryContentDisplay","m_bagDisplay"),
            ("InventoryContentDisplay","m_inventoriesScrollRect"),("StashPanel","m_stashInventory"),
            ("EnvironmentItemDisplay","m_containerDisplayList"),("EnvironmentItemDisplay","m_inventoriesScrollRect") })
            Check(game.MainModule.GetType(type).Fields.Any(f=>f.Name==name),"Missing field "+type+"."+name);
        // A build against publicized references can compile illegal direct private accesses.
        // Reject those by checking the development assembly's Publicized attributes.
        foreach(var type in plugin.MainModule.GetTypes())
        foreach(var method in type.Methods.Where(m=>m.HasBody))
        foreach(var instruction in method.Body.Instructions)
        {
            if(instruction.Operand is FieldReference field && field.DeclaringType.Scope.Name=="Assembly-CSharp")
            {
                var owner=game.MainModule.GetType(field.DeclaringType.FullName);
                var actual=owner?.Fields.FirstOrDefault(f=>f.Name==field.Name);
                Check(actual!=null && !actual.CustomAttributes.Any(a=>a.AttributeType.Name=="PublicizedAttribute"),
                    "Unsafe direct publicized field access: "+field.FullName);
            }
            if(instruction.Operand is MethodReference call && call.DeclaringType.Scope.Name=="Assembly-CSharp")
            {
                var owner=game.MainModule.GetType(call.DeclaringType.FullName);
                var actual=owner?.Methods.FirstOrDefault(m=>m.Name==call.Name
                    && m.Parameters.Select(p=>p.ParameterType.FullName).SequenceEqual(call.Parameters.Select(p=>p.ParameterType.FullName)));
                Check(actual!=null && !actual.CustomAttributes.Any(a=>a.AttributeType.Name=="PublicizedAttribute"),
                    "Unsafe direct publicized method access: "+call.FullName);
            }
        }
        var resources=plugin.MainModule.Resources.Select(r=>r.Name).ToArray();
        for(int i=0;i<6;i++) Check(resources.Contains("Companheiros.player-"+i+".png"),"Missing portrait "+i);
        Check(resources.Contains("Companheiros.player-arrow.png"),"Missing direction pointer");
        foreach(var resource in plugin.MainModule.Resources.OfType<EmbeddedResource>())
        {
            using var stream=resource.GetResourceStream();
            Check(stream.Length>8,"Empty resource");
            byte[] header=new byte[8];stream.ReadExactly(header);
            Check(header.SequenceEqual(new byte[]{137,80,78,71,13,10,26,10}),"Resource isn't PNG");
        }
        Check(plugin.MainModule.AssemblyReferences.All(r=>r.Name!="SideLoader"),"No additional SideLoader dependency");
        Check(plugin.MainModule.RuntimeVersion=="v4.0.30319","Mono-compatible CLR version");
        Check(plugin.Name.Version==new Version(0,2,0,0),"Plugin package version 0.2.0");
        foreach(var (type,name) in new[] { ("ItemListDisplay","MatchFilter"),("CharacterUI","get_IgnoreMenuInputs"),
            ("CharacterUI","get_IsInputFieldFocused"),("EnvironmentItemDisplay","get_IsEmpty") })
            Check(game.MainModule.GetType(type).Methods.Any(m=>m.Name==name&&m.ReturnType.FullName=="System.Boolean"),
                "Expected Boolean Harmony result: "+type+"."+name);
        Check(plugin.MainModule.GetType("Companheiros.SearchInputField").BaseType.FullName=="UnityEngine.UI.InputField","Real native-recognisable text input");
        foreach(var type in plugin.MainModule.GetTypes().Where(t=>t.FullName.Contains("Search")))
        foreach(var method in type.Methods.Where(m=>m.HasBody))
        foreach(var instruction in method.Body.Instructions)
        if(instruction.Operand is MethodReference call)
        {
            // Search may rebuild UI, not move/remove inventory or send multiplayer packets.
            Check(!(call.DeclaringType.Name=="ItemContainer" && call.Name!="get_IsEmpty")
                && call.DeclaringType.Name!="CharacterInventory" && call.DeclaringType.Name!="RPCManager"
                && call.DeclaringType.Name!="PhotonView", "Unexpected data/network access in search: "+call.FullName);
        }
        Console.WriteLine(checks+" DE reference/IL/resource checks passed; this does NOT execute the game.");
    }
}

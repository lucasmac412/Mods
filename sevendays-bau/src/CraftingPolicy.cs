using System;

namespace CraftingDosBaus
{
    internal static class CraftingPolicy
    {
        // Accept player crafting and other stations; deny both block and window IDs.
        internal static bool Allows(string identifier)
        {
            string name=identifier??"";
            const string prefix="workstation_";
            if(name.StartsWith(prefix,StringComparison.OrdinalIgnoreCase))name=name.Substring(prefix.Length);
            return !IsFamily(name,"forge")&&!IsFamily(name,"cementMixer");
        }
        private static bool IsFamily(string name,string family)
        {return name.Equals(family,StringComparison.OrdinalIgnoreCase)||name.StartsWith(family+"_",StringComparison.OrdinalIgnoreCase);}
    }
}

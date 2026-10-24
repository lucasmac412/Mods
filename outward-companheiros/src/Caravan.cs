using System;
using System.Runtime.CompilerServices;
using HarmonyLib;

namespace Companheiros
{
    internal static class Caravan
    {
        private sealed class Tag { }
        private static readonly ConditionalWeakTable<TravelData, Tag> ourRoutes = new ConditionalWeakTable<TravelData, Tag>();
        internal static bool Owns(TravelData route) { return route != null && ourRoutes.TryGetValue(route, out _); }

        internal static TravelData[] Build(MerchantFastTravel merchant)
        {
            var manager = AreaManager.Instance;
            var quests = QuestEventManager.Instance;
            if (!manager || !merchant || quests == null || manager.CurrentArea == null) return null;
            int current = manager.CurrentArea.ID;
            // Preserve wilderness caravans and every non-city interaction. No shortcut from dungeons.
            if (!Policy.IsCity(current)) return null;

            bool levantAllowed = (bool)AccessTools.Method(typeof(MerchantFastTravel), "CheckIfLevantBlocked", Type.EmptyTypes)
                .Invoke(merchant, null);
            bool cierzoBlocked = quests.HasQuestEvent("lDHL_XMS7kKEs0uOqrLQjw")
                || quests.HasQuestEvent("WvGjemEntk6quLjy4rLrJQ");
            int[] destinations = Policy.Destinations(current, cierzoBlocked,
                quests.HasQuestEvent("vW4sarzBGkalTwy_KhGI6A"), levantAllowed,
                quests.HasQuestEvent("eYmZGb_BJ0qAtpcwrndhTg"));
            var routes = new System.Collections.Generic.List<TravelData>();
            foreach (int destination in destinations)
            {
                if (manager.GetArea((AreaManager.AreaEnum)destination) == null) continue;
                var route = new TravelData {
                    From = (AreaManager.AreaEnum)current, Destination = (AreaManager.AreaEnum)destination,
                    SilverCost = Policy.Silver, RationCost = Policy.Rations, Unidirectional = false
                };
                ourRoutes.Add(route, new Tag());
                routes.Add(route);
            }
            // Called when the native dialogue asks for its list, on BOTH host and guests.
            // Do not modify tmpCurrentTravelData: the vanilla sync only sends its FIRST route.
            // Native CanFastTravel / AllPlayersInProximity / GetAllPlayerCanTravel / DLC
            // checks, inventory debits, instigator and world transition remain in control.
            return routes.ToArray();
        }
    }
}

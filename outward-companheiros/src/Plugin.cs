using System;
using System.Reflection;
using BepInEx;
using BepInEx.Configuration;
using HarmonyLib;

namespace Companheiros
{
    [BepInPlugin(Guid, "Companheiros de Aurai", Version)]
    public sealed class Plugin : BaseUnityPlugin
    {
        public const string Guid = "lucasmac412.outward.companheiros";
        public const string Version = "0.1.0";
        internal static Plugin Instance;
        internal ConfigEntry<bool> MapEnabled, TravelEnabled;
        internal MapOverlay Overlay;
        private Harmony harmony;
        private readonly System.Collections.Generic.HashSet<string> errors = new System.Collections.Generic.HashSet<string>();

        private void Awake()
        {
            Instance = this;
            MapEnabled = Config.Bind("Geral", "MarcadoresNoMapa", true, "Mostrar aventureiros conectados no mapa M.");
            TravelEnabled = Config.Bind("Geral", "CaravanasEntreCidades", true, "200 pratas e 3 rações por viagem, pelo contratante. Instale nos dois PCs.");
            harmony = new Harmony(Guid);
            try
            {
                // Both references are publicized development assemblies. All non-public game
                // members below are accessed through Harmony reflection, never direct access.
                Require(typeof(MapDisplay), "m_zoomLevelSmooth", typeof(float));
                Require(typeof(MapDisplay), "m_currentMapSceneID", typeof(int));
                Require(typeof(MapDisplay), "m_currentAreaHasMap", typeof(bool));
                Overlay = new MapOverlay();
                Patch(typeof(MapDisplay), "Show", new[] { typeof(CharacterUI) }, nameof(MapShown));
                Patch(typeof(MapDisplay), "OnHide", Type.EmptyTypes, nameof(MapHidden));
                Patch(typeof(MapDisplay), "UpdateWorldMarkers", Type.EmptyTypes, nameof(MapUpdated));
                Logger.LogInfo("Mapa: integração DE carregada; marcadores próprios, sem limite fixo de jogadores.");
            }
            catch (Exception e) { Warn("map-load", e); }
            try
            {
                if (AccessTools.Method(typeof(MerchantFastTravel), "CheckIfLevantBlocked", Type.EmptyTypes) == null)
                    throw new MissingMethodException("MerchantFastTravel.CheckIfLevantBlocked");
                Patch(typeof(MerchantFastTravel), "GetAvailableDestinationData", Type.EmptyTypes, nameof(Destinations));
                Patch(typeof(TravelData), "GetRealSilverCost", Type.EmptyTypes, nameof(SilverCost));
                Logger.LogInfo("Caravana: integração DE carregada; cobrança e transição usam RPCs nativos.");
            }
            catch (Exception e) { Warn("travel-load", e); }
            Logger.LogInfo("Companheiros de Aurai 0.1.0 EXPERIMENTAL - requer teste real solo e cooperativo.");
        }

        private static void Require(Type type, string name, Type valueType)
        {
            if (AccessTools.Field(type, name)?.FieldType != valueType) throw new MissingFieldException(type.Name, name);
        }
        private void Patch(Type type, string method, Type[] args, string callback)
        {
            var target = AccessTools.Method(type, method, args);
            if (target == null) throw new MissingMethodException(type.Name, method);
            harmony.Patch(target, postfix: new HarmonyMethod(typeof(Plugin), callback));
        }
        internal void Warn(string key, Exception error)
        {
            if (errors.Add(key)) Logger.LogError(key + ": " + error);
        }
        private static void MapShown(MapDisplay __instance)
        {
            try { Instance?.Overlay?.Show(__instance); }
            catch (Exception e) { Instance?.Warn("map-show", e); }
        }
        private static void MapUpdated(MapDisplay __instance)
        {
            try { Instance?.Overlay?.Update(__instance); }
            catch (Exception e) { Instance?.Overlay?.Hide(); Instance?.Warn("map-update", e); }
        }
        private static void MapHidden() { Instance?.Overlay?.Hide(); }
        private static void Destinations(MerchantFastTravel __instance, ref TravelData[] __result)
        {
            if (Instance == null || !Instance.TravelEnabled.Value) return;
            try
            {
                var replacement = Caravan.Build(__instance);
                if (replacement != null) __result = replacement;
            }
            catch (Exception e) { Instance.Warn("caravan-destinations", e); }
        }
        private static void SilverCost(TravelData __instance, ref int __result)
        {
            if (Caravan.Owns(__instance)) __result = Policy.Silver;
        }
        private void OnDestroy()
        {
            Overlay?.Dispose();
            harmony?.UnpatchSelf();
            if (Instance == this) Instance = null;
        }
    }
}

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
        public const string Version = "0.2.0";
        internal static Plugin Instance;
        internal ConfigEntry<bool> MapEnabled, TravelEnabled, SearchEnabled;
        internal MapOverlay Overlay;
        internal InventorySearch Search;
        private Harmony harmony;
        private readonly System.Collections.Generic.HashSet<string> errors = new System.Collections.Generic.HashSet<string>();

        private void Awake()
        {
            Instance = this;
            MapEnabled = Config.Bind("Geral", "MarcadoresNoMapa", true, "Mostrar aventureiros conectados no mapa M.");
            TravelEnabled = Config.Bind("Geral", "CaravanasEntreCidades", true, "200 pratas e 3 rações por viagem, pelo contratante. Instale nos dois PCs.");
            SearchEnabled = Config.Bind("Geral", "BuscaNoInventarioEBaus", true, "Barra de busca local no inventário, baús e recipientes. Não altera itens.");
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
            try
            {
                Require(typeof(InventoryContentDisplay), "m_pouchDisplay", typeof(ContainerDisplay));
                Require(typeof(InventoryContentDisplay), "m_bagDisplay", typeof(ContainerDisplay));
                Require(typeof(InventoryContentDisplay), "m_inventoriesScrollRect", typeof(UnityEngine.UI.ScrollRect));
                Require(typeof(StashPanel), "m_stashInventory", typeof(ContainerDisplay));
                Require(typeof(EnvironmentItemDisplay), "m_containerDisplayList", typeof(System.Collections.Generic.List<ContainerDisplay>));
                Require(typeof(EnvironmentItemDisplay), "m_inventoriesScrollRect", typeof(UnityEngine.UI.ScrollRect));
                Search = new InventorySearch();
                Patch(typeof(InventoryContentDisplay), "Show", Type.EmptyTypes, nameof(InventoryShown));
                Patch(typeof(StashPanel), "Show", Type.EmptyTypes, nameof(StashShown));
                Patch(typeof(EnvironmentItemDisplay), "Show", Type.EmptyTypes, nameof(LootShown));
                Patch(typeof(EnvironmentItemDisplay), "Show", new[] { typeof(Item) }, nameof(LootShown));
                Patch(typeof(Panel), "OnHide", Type.EmptyTypes, nameof(SearchPanelHidden));
                Patch(typeof(ItemListDisplay), "MatchFilter", new[] { typeof(Item) }, nameof(SearchMatch));
                Patch(typeof(ContainerDisplay), "Refresh", Type.EmptyTypes, nameof(SearchListRefreshed));
                Patch(typeof(CharacterUI), "get_IsInputFieldFocused", Type.EmptyTypes, nameof(SearchTyping));
                Patch(typeof(CharacterUI), "get_IgnoreMenuInputs", Type.EmptyTypes, nameof(SearchTyping));
                Patch(typeof(EnvironmentItemDisplay), "get_IsEmpty", Type.EmptyTypes, nameof(SearchLootEmpty));
                var takeAll = AccessTools.Method(typeof(ContainerDisplay), "TakeAllItems", new[] { typeof(ContainerDisplay) });
                if (takeAll == null) throw new MissingMethodException("ContainerDisplay.TakeAllItems");
                harmony.Patch(takeAll, prefix: new HarmonyMethod(typeof(Plugin), nameof(SearchTakeAll)));
                var select = AccessTools.Method(typeof(CharacterUI), "SetSelectedGameObject", new[] { typeof(UnityEngine.GameObject) });
                if (select == null) throw new MissingMethodException("CharacterUI.SetSelectedGameObject");
                harmony.Patch(select, prefix: new HarmonyMethod(typeof(Plugin), nameof(SearchKeepTyping)));
                Logger.LogInfo("Busca: inventário, baús e recipientes; filtro local com fonte nativa e moldura dourada.");
            }
            catch (Exception e) { Search?.Dispose(); Search = null; Warn("search-load", e); }
            Logger.LogInfo("Companheiros de Aurai " + Version + " EXPERIMENTAL - requer teste real solo e cooperativo.");
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
        private static void InventoryShown(InventoryContentDisplay __instance)
        {
            try { Instance?.Search?.Show(__instance); }
            catch (Exception e) { Instance?.Warn("search-inventory", e); }
        }
        private static void StashShown(StashPanel __instance)
        {
            try { Instance?.Search?.Show(__instance); }
            catch (Exception e) { Instance?.Warn("search-stash", e); }
        }
        private static void LootShown(EnvironmentItemDisplay __instance)
        {
            try { Instance?.Search?.Show(__instance); }
            catch (Exception e) { Instance?.Warn("search-loot", e); }
        }
        private static void SearchPanelHidden(Panel __instance)
        {
            try { Instance?.Search?.Hidden(__instance); }
            catch (Exception e) { Instance?.Warn("search-hide", e); }
        }
        private static void SearchMatch(ItemListDisplay __instance, Item __0, ref bool __result)
        {
            try { if (Instance?.Search != null) __result = Instance.Search.Match(__instance, __0, __result); }
            catch (Exception e) { Instance?.Warn("search-filter", e); }
        }
        private static void SearchListRefreshed(ContainerDisplay __instance) { Instance?.Search?.Refreshed(__instance); }
        private static void SearchTyping(CharacterUI __instance, ref bool __result)
        { if (Instance?.Search?.Focused(__instance) == true) __result = true; }
        private static void SearchLootEmpty(EnvironmentItemDisplay __instance, ref bool __result)
        { if (__result && Instance?.Search?.HasHiddenLoot(__instance) == true) __result = false; }
        private static bool SearchTakeAll(ContainerDisplay __0)
        { return Instance?.Search?.CanTakeAll(__0) ?? true; }
        private static bool SearchKeepTyping(CharacterUI __instance, UnityEngine.GameObject __0)
        {
            // Native delayed item focus must not steal the caret. A mouse click outside
            // deselects InputField first, so ordinary item clicks continue normally.
            return !__0 || !__0.GetComponent<ItemDisplay>() || Instance?.Search?.Editing(__instance) != true;
        }
        private void Update()
        {
            try { Search?.Tick(); }
            catch (Exception e) { Warn("search-update", e); Search?.Dispose(); Search = null; }
        }
        private void OnDestroy()
        {
            Search?.Dispose();
            Overlay?.Dispose();
            harmony?.UnpatchSelf();
            if (Instance == this) Instance = null;
        }
    }
}

using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using HarmonyLib;
using UnityEngine;
using UnityEngine.UI;

namespace Companheiros
{
    internal sealed class InventorySearch : IDisposable
    {
        private readonly Dictionary<UIElement, SearchBar> bars = new Dictionary<UIElement, SearchBar>();
        private readonly Dictionary<ItemListDisplay, SearchBar> lists = new Dictionary<ItemListDisplay, SearchBar>();
        private readonly HashSet<UIElement> owners = new HashSet<UIElement>();
        private readonly Dictionary<UIElement, float> retryAfter = new Dictionary<UIElement, float>();
        private static readonly FieldInfo Pouch = Field(typeof(InventoryContentDisplay), "m_pouchDisplay");
        private static readonly FieldInfo Bag = Field(typeof(InventoryContentDisplay), "m_bagDisplay");
        private static readonly FieldInfo InventoryScroll = Field(typeof(InventoryContentDisplay), "m_inventoriesScrollRect");
        private static readonly FieldInfo Stash = Field(typeof(StashPanel), "m_stashInventory");
        private static readonly FieldInfo LootLists = Field(typeof(EnvironmentItemDisplay), "m_containerDisplayList");
        private static readonly FieldInfo LootScroll = Field(typeof(EnvironmentItemDisplay), "m_inventoriesScrollRect");
        private static FieldInfo Field(Type type, string name)
        { return AccessTools.Field(type, name) ?? throw new MissingFieldException(type.Name, name); }

        internal void Track(UIElement owner)
        {
            if (owner && (owner is InventoryContentDisplay || owner is StashPanel || owner is EnvironmentItemDisplay))
                owners.Add(owner);
        }

        internal void Show(InventoryContentDisplay owner)
        {
            Attach(owner, () => new[] { Pouch.GetValue(owner) as ContainerDisplay, Bag.GetValue(owner) as ContainerDisplay },
                InventoryScroll.GetValue(owner) as ScrollRect, owner.RectTransform, "Buscar nos bolsos e na mochila…");
        }
        internal void Show(StashPanel owner)
        {
            var stash = Stash.GetValue(owner) as ContainerDisplay;
            if (!stash) return;
            var scroll = stash.GetComponentInParent<ScrollRect>();
            // Only use a scroll pane dedicated to the stash, never the other inventory pane.
            if (scroll && scroll.content && !stash.transform.IsChildOf(scroll.content)) scroll = null;
            Attach(owner, () => new[] { Stash.GetValue(owner) as ContainerDisplay },
                scroll, stash.RectTransform, "Buscar neste baú…");
        }
        internal void Show(EnvironmentItemDisplay owner)
        {
            Attach(owner, () => (LootLists.GetValue(owner) as List<ContainerDisplay>)?.ToArray() ?? new ContainerDisplay[0],
                LootScroll.GetValue(owner) as ScrollRect, owner.RectTransform, "Buscar neste recipiente…");
        }
        private void Attach(UIElement owner, Func<ContainerDisplay[]> getLists, ScrollRect scroll, RectTransform fallback, string placeholder)
        {
            Track(owner);
            if (!Plugin.Instance.SearchEnabled.Value || !owner || !owner.StartDone) return;
            if (!bars.TryGetValue(owner, out var bar))
            {
                // For panes without ScrollRect, reserve space above their actual item grid,
                // leaving the native container title, money and equipment headers in place.
                RectTransform viewport = scroll ? scroll.viewport ? scroll.viewport : scroll.transform as RectTransform : null;
                if (!viewport)
                {
                    var first = getLists().FirstOrDefault(c => c);
                    var grid = first ? first.GetComponentInChildren<GridLayoutGroup>(true) : null;
                    viewport = grid ? grid.transform as RectTransform : fallback;
                }
                if (!viewport || !viewport.parent) return;
                bar = new SearchBar(owner, getLists, viewport, placeholder);
                bars.Add(owner, bar);
            }
            if (bar.ViewportVisible) { bar.Show(); Bind(bar); }
        }
        private void Bind(SearchBar bar)
        {
            foreach (var key in lists.Where(p => p.Value == bar).Select(p => p.Key).ToArray()) lists.Remove(key);
            foreach (var list in bar.Lists()) if (list) lists[list] = bar;
        }
        internal void Hidden(UIElement owner)
        {
            if (!owner || !bars.TryGetValue(owner, out var bar)) return;
            bar.Hide();
            foreach (var key in lists.Where(p => p.Value == bar).Select(p => p.Key).ToArray()) lists.Remove(key);
        }
        internal bool Match(ItemListDisplay list, Item item, bool native)
        {
            if (!native || !Plugin.Instance.SearchEnabled.Value || !lists.TryGetValue(list, out var bar) || !bar.Active) return native;
            return bar.Query.Matches(native, item ? item.Name : null);
        }
        internal bool CanTakeAll(ContainerDisplay list)
        {
            if (!list || !lists.TryGetValue(list, out var bar) || !bar.Active || bar.Query.IsEmpty) return true;
            bar.ExplainTakeAll(); return false;
        }
        internal bool HasHiddenLoot(EnvironmentItemDisplay owner)
        {
            return bars.TryGetValue(owner, out var bar) && bar.Active && !bar.Query.IsEmpty
                && bar.Lists().Any(c => c && c.ReferencedContainer && !c.ReferencedContainer.IsEmpty);
        }
        internal void Refreshed(ContainerDisplay list)
        { if (list && lists.TryGetValue(list, out var bar)) bar.CountDirty = true; }
        internal void Tick()
        {
            foreach (var owner in owners.ToArray())
            {
                if (retryAfter.TryGetValue(owner, out float time) && Time.unscaledTime < time) continue;
                try
                {
                    if (!owner)
                    { Remove(owner); owners.Remove(owner); retryAfter.Remove(owner); continue; }
                    bool visible = Plugin.Instance.SearchEnabled.Value && owner.StartDone && owner.IsDisplayed
                        && owner.gameObject.activeInHierarchy && (!(owner is Panel panel) || !panel.Hidding);
                    if (!visible)
                    {
                        bars.TryGetValue(owner, out var active);
                        bool restoreItems = !Plugin.Instance.SearchEnabled.Value && active != null && active.Active;
                        Hidden(owner);
                        if (restoreItems) active.RefreshVisible();
                        continue;
                    }
                    if (!bars.TryGetValue(owner, out var bar) || !bar.Exists)
                    {
                        Remove(owner);
                        if (owner is InventoryContentDisplay inventory) Show(inventory);
                        else if (owner is StashPanel stash) Show(stash);
                        else if (owner is EnvironmentItemDisplay loot) Show(loot);
                        if (!bars.TryGetValue(owner, out bar)) continue;
                    }
                    if (!bar.ViewportVisible) { Hidden(owner); continue; }
                    // Reconcile visibility, including tab returns that only re-enable a GO
                    // and never call InventoryContentDisplay.Show again.
                    bar.Show(); Bind(bar); bar.Tick();
                }
                catch (Exception e)
                {
                    Plugin.Instance.Warn("search-panel-" + (owner ? owner.GetInstanceID().ToString() : "destroyed"), e);
                    // One transitioning pane must not permanently disable every search bar.
                    retryAfter[owner] = Time.unscaledTime + 1f;
                    try { Remove(owner); } catch (Exception cleanup) { Plugin.Instance.Warn("search-panel-cleanup", cleanup); }
                }
            }
        }
        private void Remove(UIElement owner)
        {
            if (!bars.TryGetValue(owner, out var bar)) return;
            foreach (var key in lists.Where(p => p.Value == bar).Select(p => p.Key).ToArray()) lists.Remove(key);
            bars.Remove(owner); bar.Dispose();
        }
        public void Dispose()
        {
            lists.Clear(); foreach (var bar in bars.Values) bar.Dispose(); bars.Clear(); owners.Clear(); retryAfter.Clear();
        }
    }
}

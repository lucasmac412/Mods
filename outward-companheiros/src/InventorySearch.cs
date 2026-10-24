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
        private static readonly FieldInfo Pouch = Field(typeof(InventoryContentDisplay), "m_pouchDisplay");
        private static readonly FieldInfo Bag = Field(typeof(InventoryContentDisplay), "m_bagDisplay");
        private static readonly FieldInfo InventoryScroll = Field(typeof(InventoryContentDisplay), "m_inventoriesScrollRect");
        private static readonly FieldInfo Stash = Field(typeof(StashPanel), "m_stashInventory");
        private static readonly FieldInfo LootLists = Field(typeof(EnvironmentItemDisplay), "m_containerDisplayList");
        private static readonly FieldInfo LootScroll = Field(typeof(EnvironmentItemDisplay), "m_inventoriesScrollRect");
        private static FieldInfo Field(Type type, string name)
        { return AccessTools.Field(type, name) ?? throw new MissingFieldException(type.Name, name); }

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
            if (!Plugin.Instance.SearchEnabled.Value || !owner) return;
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
            bar.Show(); Bind(bar);
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
        internal bool Focused(CharacterUI ui)
        {
            if (!ui) return false;
            return bars.Values.Any(b => b.Active && b.Owner.CharacterUI == ui && b.CapturingText);
        }
        internal bool Editing(CharacterUI ui)
        {
            return ui && bars.Values.Any(b => b.Active && b.Owner.CharacterUI == ui && b.Input && b.Input.isFocused);
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
            foreach (var pair in bars.ToArray())
            {
                var bar = pair.Value;
                if (!pair.Key || !bar.Exists)
                {
                    Hidden(pair.Key); bar.Dispose(); bars.Remove(pair.Key);
                    foreach (var key in lists.Where(p => p.Value == bar).Select(p => p.Key).ToArray()) lists.Remove(key);
                }
                else if (!Plugin.Instance.SearchEnabled.Value || !pair.Key.IsDisplayed || !pair.Key.gameObject.activeInHierarchy)
                    Hidden(pair.Key);
                else if (bar.Active)
                { Bind(bar); bar.Tick(); }
            }
        }
        public void Dispose()
        {
            lists.Clear(); foreach (var bar in bars.Values) bar.Dispose(); bars.Clear();
        }
    }
}

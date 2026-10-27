using System;
using System.Collections.Generic;
using System.Linq;

namespace CraftingDosBaus
{
    internal static class ForgeSupply
    {
        private static readonly HashSet<string> RawMaterials=new HashSet<string>(StringComparer.Ordinal) {
            "resourceScrapIron", "resourceClayLump", "resourceScrapBrass", "resourceScrapLead",
            "resourceRockSmall", "resourceBrokenGlass", "resourceCrushedSand"
        };
        // Feed physical raw materials into the existing three smelting input slots.
        // Never converts ore directly into unit_* resources or bypasses fuel/time.
        internal static List<ItemStack> Plan(ItemActionEntryCraft action,List<ItemStack> missing,List<ItemStack> stock)
        {
            var grid=Client.Grid(action) as XUiC_WorkstationMaterialInputGrid;
            if(grid==null)return null;
            var plan=PlanSlots(grid.GetSlots(),missing,stock);
            if(plan.Count==0)Client.Say("A forja precisa fundir o material já carregado, ou não há matéria-prima/espaço disponível.");
            return plan;
        }
        internal static List<ItemStack> PlanSlots(ItemStack[] input,List<ItemStack> missing,List<ItemStack> stock)
        {
            var slots=ItemStack.Clone(input);
            var result=new Dictionary<int,ItemStack>();
            foreach(var need in missing)
            {
                string category=need.itemValue.ItemClass.MadeOfMaterial.ForgeCategory;
                if(category==null)continue;
                long units=need.count;
                // Already loaded physical ore is still awaiting native smelting.
                foreach(var item in slots.Take(3))
                    if(Server.Eligible(item)&&item.itemValue.ItemClass.MadeOfMaterial.ForgeCategory==category)
                        units-=(long)item.count*item.itemValue.ItemClass.GetWeight();
                if(units<=0)continue;
                foreach(var candidate in stock.Where(s=>Server.Eligible(s)&&!s.itemValue.HasQuality
                    && RawMaterials.Contains(s.itemValue.ItemClass.GetItemName())
                    &&s.itemValue.ItemClass.MadeOfMaterial.ForgeCategory==category)
                    .OrderBy(s=>s.itemValue.ItemClass.GetItemName().StartsWith("resource",StringComparison.Ordinal)?0:1))
                {
                    int weight=candidate.itemValue.ItemClass.GetWeight();if(weight<=0)continue;
                    int available=candidate.count;
                    for(int i=0;i<Math.Min(3,slots.Length)&&units>0&&available>0;i++)
                    {
                        if(!slots[i].IsEmpty()&&!slots[i].CanStackWith(candidate))continue;
                        int room=candidate.itemValue.ItemClass.MaxCount-(slots[i].IsEmpty()?0:slots[i].count);
                        int take=(int)Math.Min(Math.Min(available,room),(units+weight-1)/weight);
                        if(take<=0)continue;
                        if(slots[i].IsEmpty()){slots[i]=candidate.Clone();slots[i].count=0;}
                        slots[i].count+=take;available-=take;units-=(long)take*weight;
                        if(!result.TryGetValue(candidate.itemValue.type,out var sum))
                        {result[candidate.itemValue.type]=sum=candidate.Clone();sum.count=0;}
                        sum.count+=take;
                    }
                    if(units<=0)break;
                }
            }
            return result.Values.ToList();
        }
        internal static void Apply(ItemActionEntryCraft action,List<ItemStack> wallet)
        {
            var grid=Client.Grid(action) as XUiC_WorkstationMaterialInputGrid;
            if(grid==null)return;
            var slots=ItemStack.Clone(grid.GetSlots());
            foreach(var item in wallet)
            {
                for(int i=0;i<Math.Min(3,slots.Length)&&item.count>0;i++)
                {
                    if(!slots[i].IsEmpty()&&!slots[i].CanStackWith(item))continue;
                    int room=item.itemValue.ItemClass.MaxCount-(slots[i].IsEmpty()?0:slots[i].count);
                    int n=Math.Min(room,item.count);if(n<=0)continue;
                    if(slots[i].IsEmpty()){slots[i]=item.Clone();slots[i].count=0;}
                    slots[i].count+=n;item.count-=n;
                }
            }
            grid.SetSlots(slots);
            grid.WorkstationData.SetInputStacks(slots);
            grid.WindowGroup.Controller.SetAllChildrenDirty();
            Client.Say("Forja abastecida. Ligue o fogo, aguarde a fundição e fabrique normalmente.");
        }
    }
}

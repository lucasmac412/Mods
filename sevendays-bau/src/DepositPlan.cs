using System;
using System.Collections.Generic;
using System.Linq;

namespace CraftingDosBaus
{
    // Plans on clones. Existing item types are captured BEFORE placing anything.
    internal static class DepositPlan
    {
        internal sealed class Container
        {
            internal ItemStack[] Slots;
            internal bool[] Locked;
            internal HashSet<int> Existing;
            internal bool Changed;
            internal Container(ItemStackGrid grid)
            {
                Slots=ItemStack.Clone(grid.items);
                Locked=Enumerable.Range(0,Slots.Length).Select(grid.IsSlotLocked).ToArray();
                Existing=new HashSet<int>(Slots.Where((s,i)=>!Locked[i]&&!s.IsEmpty()).Select(s=>s.itemValue.type));
            }
        }
        internal static List<ItemStack> Build(IList<Container> containers,IList<ItemStack> incoming)
        {
            var remainder=ItemStack.Clone(incoming).ToList();
            foreach(var item in remainder)
            {
                // Fill compatible stacks across all matching chests first, then free slots.
                for(int pass=0;pass<2 && item.count>0;pass++)
                foreach(var chest in containers)
                {
                    if(!chest.Existing.Contains(item.itemValue.type))continue;
                    for(int i=0;i<chest.Slots.Length && item.count>0;i++)
                    {
                        if(chest.Locked[i])continue;
                        var slot=chest.Slots[i];
                        bool empty=slot.IsEmpty();
                        if(pass==0 && (empty || !slot.CanStackWith(item,true) || !slot.itemValue.Equals(item.itemValue)))continue;
                        if(pass==1 && !empty)continue;
                        int room=item.itemValue.ItemClass.MaxCount-(empty?0:slot.count);
                        int n=Math.Min(room,item.count);if(n<=0)continue;
                        if(empty){slot=item.Clone();slot.count=0;chest.Slots[i]=slot;}
                        slot.count+=n;item.count-=n;chest.Changed=true;
                    }
                }
            }
            return remainder.Where(s=>s.count>0).ToList();
        }
        internal static bool ValidRefund(IList<ItemStack> offered,IList<ItemStack> refund)
        {
            var available=ItemStack.Clone(offered);
            foreach(var item in refund)
            {
                if(item==null||item.IsEmpty())return false;
                int remaining=item.count;
                foreach(var original in available.Where(s=>s.itemValue.Equals(item.itemValue)))
                {int n=Math.Min(remaining,original.count);original.count-=n;remaining-=n;}
                if(remaining!=0)return false;
            }
            return true;
        }
        internal sealed class Carried
        {
            internal ItemStackGrid Grid;
            internal int Index;
            internal ItemStack Original;
        }
        internal static List<Carried> Select(IEnumerable<ItemStackGrid> grids,HashSet<int> types)
        {
            var result=new List<Carried>();
            foreach(var grid in grids)
            for(int i=0;i<grid.items.Length && result.Count<128;i++)
            {
                var item=grid.items[i];
                if(!grid.IsSlotLocked(i)&&!item.IsEmpty()&&item.itemValue.ItemClass!=null&&item.count<=item.itemValue.ItemClass.MaxCount&&types.Contains(item.itemValue.type))
                    result.Add(new Carried{Grid=grid,Index=i,Original=item.Clone()});
            }
            return result;
        }
    }
}

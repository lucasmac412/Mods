using System;
using System.Collections.Generic;

namespace CraftingDosBaus
{
    // No Unity/network dependencies: also executed by concurrency/rollback tests.
    internal static class WithdrawalPlan
    {
        internal sealed class Slot
        {
            public int Type, Count;
            public bool Allowed;
        }
        internal readonly struct Take
        {
            public readonly int Index, Count;
            public Take(int index, int count) { Index=index; Count=count; }
        }
        internal static List<Take> Build(IReadOnlyList<Slot> slots, IReadOnlyDictionary<int,int> requested)
        {
            if (requested==null || requested.Count==0 || requested.Count>128) return null;
            var plan=new List<Take>();
            foreach(var request in requested)
            {
                if(request.Key<=0 || request.Value<=0 || request.Value>1000000) return null;
                int remaining=request.Value;
                for(int i=0;i<slots.Count && remaining>0;i++)
                {
                    var slot=slots[i];
                    if(!slot.Allowed || slot.Type!=request.Key || slot.Count<=0) continue;
                    int take=Math.Min(remaining,slot.Count);
                    plan.Add(new Take(i,take)); remaining-=take;
                }
                if(remaining!=0) return null; // no partial debit
            }
            return plan;
        }
        internal static bool InRange(double distanceSquared,double range)
        { return range>=1 && range<=100 && !double.IsNaN(distanceSquared) && distanceSquared>=0 && distanceSquared<=range*range; }
    }
}

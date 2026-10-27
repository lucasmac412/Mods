using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using HarmonyLib;

namespace CraftingDosBaus
{
    internal static class Hooks
    {
        private static HarmonyMethod H(string name)=>new HarmonyMethod(typeof(Hooks),name);
        internal static void Install(Harmony harmony)
        {
            try
            {
                var scopeTargets=new[]{AccessTools.Method(typeof(ItemActionEntryCraft),"hasItems"),AccessTools.Method(typeof(XUiC_RecipeCraftCount),"calcMaxCraftable"),AccessTools.Method(typeof(XUiC_IngredientEntry),"GetBindingValueInternal")};
                if(scopeTargets.Any(m=>m==null))throw new MissingMethodException("Métodos de fabricação 3.4 ausentes");
                foreach(var method in scopeTargets)harmony.Patch(method,prefix:H(nameof(Enter)),finalizer:H(nameof(Leave)));
                harmony.Patch(AccessTools.Method(typeof(ItemActionEntryCraft),"OnActivated"),prefix:H(nameof(Activate)),finalizer:H(nameof(ActivatedEnd)));
                harmony.Patch(AccessTools.Method(typeof(ItemActionEntryCraft),"OnDisabledActivate"),prefix:H(nameof(DisabledActivate)));
                harmony.Patch(AccessTools.Method(typeof(XUiM_PlayerInventory),"GetAllItemStacks"),postfix:H(nameof(AllStacks)));
                harmony.Patch(AccessTools.Method(typeof(XUiM_PlayerInventory),"GetItemCount",new[]{typeof(ItemValue)}),postfix:H(nameof(ItemCount)));
                harmony.Patch(AccessTools.Method(typeof(XUiC_WorkstationInputGrid),"GetItemCount"),postfix:H(nameof(ItemCount)));
                harmony.Patch(AccessTools.Method(typeof(XUiC_RecipeCraftCount),"calcMaxCraftable"),transpiler:H(nameof(Maximum)));
                harmony.Patch(AccessTools.Method(typeof(XUiC_RecipeList),"BuildRecipeInfosList"),prefix:H(nameof(RecipeList)));
                foreach(var type in new[]{typeof(XUiM_PlayerInventory),typeof(XUiC_WorkstationInputGrid)})
                {
                    harmony.Patch(AccessTools.Method(type,"HasItems"),postfix:H(nameof(HasItems)));
                    harmony.Patch(AccessTools.Method(type,"RemoveItems"),prefix:H(nameof(BeforeRemove)),postfix:H(nameof(AfterRemove)));
                }
                harmony.Patch(AccessTools.Method(typeof(XUiC_BackpackWindow),"Init"),postfix:H(nameof(BackpackReady)));
                foreach(var method in typeof(XUiC_CraftingQueue).GetMethods().Where(m=>m.Name=="AddRecipeToCraft"&&m.ReturnType==typeof(bool)))
                    harmony.Patch(method,postfix:H(nameof(Queued)));
            }
            catch {harmony.UnpatchSelf();throw;}
        }
        internal static bool Supported(XUiController controller)
        {
            var root=controller?.WindowGroup?.Controller as XUiC_CraftingWindowGroup;
            return root!=null && CraftingPolicy.Allows(root.Workstation)
                && CraftingPolicy.Allows(root.WindowGroup?.Id)
                && root.GetChildByType<XUiC_WorkstationMaterialInputGrid>()==null;
        }
        private static bool SupportedInventory(object inventory)
        { return inventory is XUiM_PlayerInventory || (inventory is XUiController controller && Supported(controller)); }
        private struct ScopeState { public int Depth; public bool Suppressed; }
        private static void Enter(object __instance,out ScopeState __state)
        {
            __state=new ScopeState{Depth=Client.Scope,Suppressed=Client.Suppress};
            var controller=__instance is ItemActionEntryCraft action?action.ItemController:__instance as XUiController;
            if(Supported(controller))Client.Scope++;
            else Client.Suppress=true;
        }
        private static void Leave(ScopeState __state){Client.Scope=__state.Depth;Client.Suppress=__state.Suppressed;}
        private static void BackpackReady(XUiC_BackpackWindow __instance)
        {
            var button=__instance.GetChildById("btnGuardarNosBaus");
            if(button!=null)button.OnPress+=QuickStore.Pressed;
        }
        private static bool Activate(ItemActionEntryCraft __instance,out bool __state)
        {
            __state=Client.Suppress;
            bool result=Client.Begin(__instance);
            if(result&&!Client.Completing)Client.Suppress=true; // cached counts NEVER authorize a debit
            return result;
        }
        private static void ActivatedEnd(bool __state){Client.Suppress=__state;}
        private static bool DisabledActivate(ItemActionEntryCraft __instance)=>Client.Begin(__instance,true);
        private static void AllStacks(ref List<ItemStack> __result)
        {if(Client.Active){__result=new List<ItemStack>(__result);__result.AddRange(ItemStack.Clone(Client.Extras));}}
        private static void ItemCount(object __instance,ItemValue __0,ref int __result)
        {if(Client.Active&&SupportedInventory(__instance))__result=(int)Math.Min(int.MaxValue,(long)__result+Client.ExtraCount(__0.type));}
        private static void RecipeList(XUiC_RecipeList __instance,List<ItemStack> __0)
        {if(Plugin.Ready&&!Client.Suppress&&Supported(__instance))__0.AddRange(ItemStack.Clone(Client.Extras));}
        private static ItemStack[] MergeGrid(ItemStack[] items)
        {return Client.Active?items.Concat(ItemStack.Clone(Client.Extras)).ToArray():items;}
        private static IEnumerable<CodeInstruction> Maximum(IEnumerable<CodeInstruction> instructions)
        {
            int matches=0;
            foreach(var instruction in instructions)
            {
                yield return instruction;
                if(instruction.operand is MethodInfo method&&method.Name=="GetSlots"&&method.ReturnType==typeof(ItemStack[]))
                {matches++;yield return new CodeInstruction(OpCodes.Call,AccessTools.Method(typeof(Hooks),nameof(MergeGrid)));}
            }
            if(matches!=1)throw new InvalidOperationException("Fluxo de quantidade máxima mudou na API do jogo.");
        }
        private static int NativeCount(object inventory,ItemValue item)
        {
            bool previous=Client.Suppress;Client.Suppress=true;
            try {return inventory is XUiM_PlayerInventory p?p.GetItemCount(item):((XUiC_WorkstationInputGrid)inventory).GetItemCount(item);}
            finally{Client.Suppress=previous;}
        }
        private static void HasItems(object __instance,IList<ItemStack> __0,int __1,ref bool __result)
        {
            if(!Client.Active||!SupportedInventory(__instance)||__result||__1<1)return;
            __result=__0.GroupBy(s=>s.itemValue.type).All(g=>g.Sum(s=>(long)s.count)*__1 <=(long)NativeCount(__instance,g.First().itemValue)+Client.ExtraCount(g.Key));
        }
        private static void BeforeRemove(object __instance,IList<ItemStack> __0,int __1,out Dictionary<int,int> __state)
        {
            __state=null;
            if(!Client.Completing||!SupportedInventory(__instance))return;
            __state=new Dictionary<int,int>();
            foreach(var group in __0.GroupBy(s=>s.itemValue.type))
            {
                long deficit=Math.Max(0,group.Sum(s=>(long)s.count)*__1-NativeCount(__instance,group.First().itemValue));
                if(deficit>int.MaxValue||deficit>Client.ExtraCount(group.Key))throw new InvalidOperationException("Ingredientes mudaram antes do consumo.");
                if(deficit>0)__state.Add(group.Key,(int)deficit);
            }
        }
        private static void AfterRemove(IList<ItemStack> __2,Dictionary<int,int> __state)
        {
            if(__state==null)return;
            foreach(var pair in __state)Client.Consume(pair.Key,pair.Value,__2);
            Client.Spent=true;
        }
        private static void Queued(bool __result)
        {if(Client.Completing&&__result)Client.Queued=true;}
    }
}

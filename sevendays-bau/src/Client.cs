using System;
using System.Collections.Generic;
using System.Linq;
using HarmonyLib;
using UnityEngine;

namespace CraftingDosBaus
{
    internal static class Client
    {
        internal static int Scope;
        internal static bool Suppress, Completing, Queued, Spent;
        internal static List<ItemStack> Wallet;
        private static List<ItemStack> stock=new List<ItemStack>();
        private static float nextPoll,stockAt=-100, retryAt;
        private static readonly HashSet<string> Received=new HashSet<string>();
        private sealed class Pending
        {
            public string Id;
            public ItemActionEntryCraft Action;
            public Recipe Recipe;
            public int Count;
            public List<ItemStack> Wanted;
            public bool FeedForge;
        }
        private static Pending pending;
        private static readonly System.Reflection.FieldInfo CountField=AccessTools.Field(typeof(ItemActionEntryCraft),"craftCountControl");
        private static readonly System.Reflection.FieldInfo IngredientField=AccessTools.Field(typeof(ItemActionEntryCraft),"tempIngredientList");
        private static readonly System.Reflection.MethodInfo HasItemsMethod=AccessTools.Method(typeof(ItemActionEntryCraft),"hasItems");
        internal static EntityPlayerLocal Player=>GameManager.Instance?.World?.GetPrimaryPlayer();
        internal static bool Active=>Plugin.Ready&&!Suppress&&(Scope>0||Completing);
        internal static XUiC_RecipeCraftCount CountControl(ItemActionEntryCraft action)=>(XUiC_RecipeCraftCount)CountField.GetValue(action);
        internal static XUiC_WorkstationInputGrid Grid(ItemActionEntryCraft action)=>CountControl(action).WindowGroup.Controller.GetChildByType<XUiC_WorkstationInputGrid>();
        internal static List<ItemStack> Extras=>Completing ? Wallet??new List<ItemStack>() : Time.realtimeSinceStartup-stockAt<3?stock:new List<ItemStack>();
        internal static int ExtraCount(int type)=>Extras.Where(s=>s.itemValue.type==type).Sum(s=>s.count);
        internal static void Reset()
        { stock.Clear();stockAt=-100;pending=null;Wallet=null;Completing=false;Scope=0;Suppress=false;nextPoll=0; }
        internal static void Tick()
        {
            var player=Player;
            if(player==null||player.IsDead())return;
            float now=Time.realtimeSinceStartup;
            if(now>=nextPoll)
            {
                nextPoll=now+1;
                Send(NetPackageManager.GetPackage<NetPackageCraftingDosBaus>().Setup(NetPackageCraftingDosBaus.SnapshotRequest,player.entityId,""));
            }
            if(pending!=null&&now>=retryAt)
            { retryAt=now+3; Send(NetPackageManager.GetPackage<NetPackageCraftingDosBaus>().Setup(NetPackageCraftingDosBaus.Withdraw,player.entityId,pending.Id,pending.Wanted)); }
        }
        internal static void Say(string text)
        { var player=Player;if(player!=null)GameManager.ShowTooltip(player,"[Baús] "+text); }
        private static void Send(NetPackageCraftingDosBaus packet)
        {
            if(SingletonMonoBehaviour<ConnectionManager>.Instance.IsServer)Server.Handle(packet,null);
            else SingletonMonoBehaviour<ConnectionManager>.Instance.SendToServer(packet,true);
        }
        internal static void Receive(NetPackageCraftingDosBaus packet)
        {
            var player=Player;if(player==null||player.entityId!=packet.Player)return;
            if(packet.Kind==NetPackageCraftingDosBaus.Snapshot)
            {
                bool changed=stock.Count!=packet.Stacks.Count || stockAt<0 || stock.Where((s,i)=>i>=packet.Stacks.Count || s.itemValue.type!=packet.Stacks[i].itemValue.type || s.count!=packet.Stacks[i].count).Any();
                stock=packet.Stacks;stockAt=Time.realtimeSinceStartup;
                if(changed) RefreshCrafting(player);
                return;
            }
            if(packet.Kind==NetPackageCraftingDosBaus.Denied)
            { if(pending?.Id==packet.Id){pending=null;Say(packet.Message);stockAt=-100;nextPoll=0;}return; }
            if(packet.Kind!=NetPackageCraftingDosBaus.Grant)return;
            if(Received.Contains(packet.Id)) { Ack(packet);return; }
            // Same withdrawal reply may arrive after a retry. Consume each receipt once.
            Received.Add(packet.Id);
            var task=pending?.Id==packet.Id?pending:null;
            if(task!=null)pending=null;
            Wallet=ItemStack.Clone(packet.Stacks).ToList();
            Completing=true;Queued=false;Spent=false;
            try
            {
                bool valid=task!=null && task.Action.ItemController?.xui!=null
                    && ReferenceEquals(((XUiC_RecipeEntry)task.Action.ItemController).Recipe,task.Recipe)
                    && CountControl(task.Action).Count==task.Count
                    && CountControl(task.Action).WindowGroup.isShowing;
                if(valid)
                {
                    if(task.FeedForge) ForgeSupply.Apply(task.Action,Wallet);
                    else task.Action.OnActivated(); // native unlock/tool/fuel/queue/time checks
                }
                // A full queue/closed UI must not lose already withdrawn physical items.
                if(!Queued||Spent||task?.FeedForge==true) ReturnRemainder();
                else Say("Falha na confirmação da fila. Saldo retido; consulte o log antes de continuar.");
            }
            catch(Exception e)
            {
                Log.Error("[CraftingDosBaus] Erro ao aplicar retirada "+packet.Id+": "+e);
                if(!Queued)ReturnRemainder();
                Say("Erro ao fabricar; confira o log. Não repita o pedido.");
            }
            finally { Completing=false;Ack(packet);stockAt=-100;nextPoll=0; }
        }
        private static void Ack(NetPackageCraftingDosBaus grant)
        { Send(NetPackageManager.GetPackage<NetPackageCraftingDosBaus>().Setup(NetPackageCraftingDosBaus.Ack,grant.Player,grant.Id)); }
        private static void RefreshCrafting(EntityPlayerLocal player)
        {
            var groups=LocalPlayerUI.GetUIForPlayer(player)?.xui?.WindowGroups;
            if(groups==null)return;
            foreach(var group in groups)
            {
                var recipes=group.Controller?.GetChildByType<XUiC_RecipeList>();
                if(recipes==null)continue;
                AccessTools.Field(typeof(XUiC_RecipeList),"resortRecipes").SetValue(recipes,true);
                group.Controller.SetAllChildrenDirty();
            }
        }
        private static void ReturnRemainder()
        {
            var inventory=LocalPlayerUI.GetUIForPlayer(Player)?.xui?.PlayerInventory;
            if(inventory==null)return;
            foreach(var stack in Wallet.Where(s=>s.count>0).ToArray())
            {
                // Return like a cancelled native craft: inventory first, overflow on the ground.
                var copy=stack.Clone();
                inventory.AddItem(copy);
                if(copy.count>0) {
                    GameManager.Instance.ItemDropServer(copy,Player.position+Vector3.up,Vector3.zero,Player.entityId,300f);
                    Say("Inventário cheio: ingredientes devolvidos no chão ao seu lado.");
                }
                stack.count=0;
            }
            Wallet.RemoveAll(s=>s.count<=0);
            if(Wallet.Count==0)Wallet=null;
        }
        internal static bool Begin(ItemActionEntryCraft action,bool disabled=false)
        {
            if(!Plugin.Ready||Completing)return true;
            if(pending!=null){Say("Aguardando os baús...");return false;}
            if(Wallet!=null&&Wallet.Any(s=>s.count>0))
            {
                Completing=true;Queued=false;Spent=false;
                try { action.OnActivated(); if(!Queued||Spent)ReturnRemainder(); }
                finally {Completing=false;}
                return false;
            }
            var entry=action.ItemController as XUiC_RecipeEntry;
            if(entry?.Recipe==null||!entry.IsCurrentWorkstation||!XUiM_Recipes.GetRecipeIsUnlocked(entry.xui,entry.Recipe))return true;
            var window=CountControl(action).WindowGroup.Controller as XUiC_CraftingWindowGroup;
            int tier=(int)AccessTools.Field(typeof(ItemActionEntryCraft),"craftingTier").GetValue(action);
            if(window==null||!window.CraftingRequirementsValid(entry.Recipe)||tier>entry.Recipe.GetCraftingTier(Player))return true;
            bool own;
            Suppress=true;
            try {own=(bool)HasItemsMethod.Invoke(action,new object[]{entry.xui,entry.Recipe});}
            finally{Suppress=false;}
            if(own)return true;
            // Re-run against the snapshot to finish native ingredient computation,
            // including recipes whose first quality ingredient previously caused early return.
            bool withChests=(bool)HasItemsMethod.Invoke(action,new object[]{entry.xui,entry.Recipe});
            if(!withChests && !(Grid(action) is XUiC_WorkstationMaterialInputGrid))return true;
            var ingredients=((List<ItemStack>)IngredientField.GetValue(action)).Select(s=>s.Clone()).ToList();
            int count=CountControl(action).Count;
            if(count<1||count>1000000)return true;
            var grid=Grid(action);
            var wanted=new List<ItemStack>();
            Suppress=true;
            try
            {
                foreach(var group in ingredients.GroupBy(s=>s.itemValue.type))
                {
                    var value=group.First().itemValue;
                    long required=group.Sum(s=>(long)s.count)*count;
                    int ownCount=grid!=null?grid.GetItemCount(value):entry.xui.PlayerInventory.GetItemCount(value);
                    long missing=Math.Max(0,required-ownCount);
                    if(missing>1000000)return true;
                    if(missing>0)wanted.Add(new ItemStack(value.Clone(),(int)missing));
                }
            }
            finally {Suppress=false;}
            if(wanted.Count==0)return true;
            bool feed=grid is XUiC_WorkstationMaterialInputGrid && wanted.Any(s=>s.itemValue.ItemClass.GetItemName().StartsWith("unit_",StringComparison.Ordinal));
            if(feed)wanted=ForgeSupply.Plan(action,wanted,stock);
            if(wanted==null||wanted.Count==0)return true;
            if(!feed && wanted.Any(s=>ExtraCount(s.itemValue.type)<s.count))return true;
            var request=new Pending {Id=Guid.NewGuid().ToString("N"),Action=action,Recipe=entry.Recipe,Count=count,Wanted=wanted,FeedForge=feed};
            pending=request;retryAt=Time.realtimeSinceStartup+3;
            Say(feed?"Abastecendo a forja pelos baús...":"Usando ingredientes dos baús...");
            Send(NetPackageManager.GetPackage<NetPackageCraftingDosBaus>().Setup(NetPackageCraftingDosBaus.Withdraw,Player.entityId,request.Id,wanted));
            return false;
        }
        internal static void Consume(int type,int count,IList<ItemStack> removed)
        {
            if(Wallet==null)throw new InvalidOperationException("Saldo remoto ausente");
            foreach(var stack in Wallet.Where(s=>s.itemValue.type==type&&s.count>0))
            {
                int n=Math.Min(count,stack.count);if(n<=0)continue;
                var used=stack.Clone();used.count=n;removed?.Add(used);
                stack.count-=n;count-=n;if(count==0)break;
            }
            if(count!=0)throw new InvalidOperationException("Saldo remoto insuficiente");
        }
    }
}

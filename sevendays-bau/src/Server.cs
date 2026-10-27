using System;
using System.Collections.Generic;
using System.Linq;
using Platform;
using UnityEngine;

namespace CraftingDosBaus
{
    internal static class Server
    {
        internal static readonly object Gate=new object();
        private sealed class Receipt { public List<ItemStack> Stacks; public string Message; }
        private sealed class Source { public TEFeatureStorage Storage; public int Index; public TileEntityComposite Parent; }
        private static readonly Dictionary<string,Receipt> Receipts=new Dictionary<string,Receipt>();
        private static readonly Dictionary<string,float> NextSnapshot=new Dictionary<string,float>();
        internal static void Reset() { lock(Gate) { Receipts.Clear(); NextSnapshot.Clear(); } }
        internal static bool Eligible(ItemStack stack)
        { return stack!=null && !stack.IsEmpty() && stack.count>0 && (!stack.itemValue.HasModSlots || !stack.itemValue.HasMods()); }
        internal static void Handle(NetPackageCraftingDosBaus request,ClientInfo sender)
        {
            lock(Gate)
            {
                var world=GameManager.Instance?.World;
                var player=world?.GetEntity(request.Player) as EntityPlayer;
                if(player==null || player.IsDead()) return;
                var owner=sender?.InternalId ?? PlatformManager.InternalLocalUserIdentifier;
                if(owner==null) return;
                string identity=owner.CombinedString;
                string key=identity+":"+request.Id;
                if(request.Kind==NetPackageCraftingDosBaus.Ack)
                { if(Receipts.TryGetValue(key,out var r)) r.Stacks=null; return; }
                if(request.Kind==NetPackageCraftingDosBaus.SnapshotRequest)
                {
                    float now=Time.realtimeSinceStartup;
                    if(NextSnapshot.TryGetValue(identity,out float next)&&now<next) return;
                    NextSnapshot[identity]=now+0.7f;
                    var counts=new Dictionary<int,ItemStack>();
                    foreach(var slot in Sources(world,player,owner))
                    {
                        var item=slot.Storage.ItemGrid.items[slot.Index];
                        if(!counts.TryGetValue(item.itemValue.type,out var sum))
                        { if(counts.Count>=1024) continue; counts[item.itemValue.type]=sum=item.Clone(); sum.count=0; }
                        sum.count=(int)Math.Min(1000000L,(long)sum.count+item.count);
                    }
                    Reply(request,sender,NetPackageCraftingDosBaus.Snapshot,counts.Values.ToList(),"");return;
                }
                if(request.Kind!=NetPackageCraftingDosBaus.Withdraw || !Guid.TryParse(request.Id,out _)) return;
                if(Receipts.TryGetValue(key,out var previous))
                {
                    if(previous.Stacks!=null) Reply(request,sender,NetPackageCraftingDosBaus.Grant,ItemStack.Clone(previous.Stacks).ToList(),"");
                    else Reply(request,sender,NetPackageCraftingDosBaus.Denied,null,previous.Message??"Esta retirada já foi recebida.");
                    return;
                }
                if(Receipts.Count>=20000) { Reply(request,sender,NetPackageCraftingDosBaus.Denied,null,"Limite de sessão atingido; reinicie a partida.");return; }
                var wanted=new Dictionary<int,int>();
                foreach(var stack in request.Stacks)
                {
                    if(!Eligible(stack)||stack.count>1000000||wanted.ContainsKey(stack.itemValue.type))
                    { Reply(request,sender,NetPackageCraftingDosBaus.Denied,null,"Pedido de ingredientes inválido.");return; }
                    wanted.Add(stack.itemValue.type,stack.count);
                }
                var sources=Sources(world,player,owner);
                var slots=sources.Select(s=>new WithdrawalPlan.Slot {Type=s.Storage.ItemGrid.items[s.Index].itemValue.type,Count=s.Storage.ItemGrid.items[s.Index].count,Allowed=true}).ToList();
                var plan=WithdrawalPlan.Build(slots,wanted);
                if(plan==null || plan.Count>4096)
                {
                    const string reason="Os ingredientes mudaram ou o baú está aberto/em uso. Tente novamente.";
                    Receipts[key]=new Receipt {Message=reason};
                    Reply(request,sender,NetPackageCraftingDosBaus.Denied,null,reason);return;
                }
                var backups=new Dictionary<Source,ItemStack>();
                var granted=new List<ItemStack>();
                try
                {
                    // A single server dispatch validates ALL quantities before modifying any slot.
                    // Never applies a client's container snapshot to the authoritative inventory.
                    foreach(var take in plan)
                    {
                        var source=sources[take.Index]; var item=source.Storage.ItemGrid.items[source.Index];
                        backups.Add(source,item.Clone());
                        var copy=item.Clone();copy.count=take.Count;granted.Add(copy);
                        item.count-=take.Count;if(item.count==0)item.Clear();
                    }
                    foreach(var storage in backups.Keys.Select(s=>s.Storage).Distinct()) storage.SetModified();
                }
                catch(Exception e)
                {
                    foreach(var backup in backups)backup.Key.Storage.ItemGrid.items[backup.Key.Index]=backup.Value;
                    foreach(var storage in backups.Keys.Select(s=>s.Storage).Distinct()) {try{storage.SetModified();}catch(Exception notify){Log.Error("[CraftingDosBaus] Falha ao notificar reversão: "+notify.Message);}}
                    Log.Error("[CraftingDosBaus] Retirada revertida: "+e);
                    Receipts[key]=new Receipt{Message="Falha na retirada; ingredientes mantidos nos baús."};
                    Reply(request,sender,NetPackageCraftingDosBaus.Denied,null,Receipts[key].Message);return;
                }
                Receipts[key]=new Receipt {Stacks=ItemStack.Clone(granted).ToList()};
                Reply(request,sender,NetPackageCraftingDosBaus.Grant,granted,"");
            }
        }
        internal static List<TileEntityComposite> Containers(World world,EntityPlayer player,PlatformUserIdentifierAbs owner)
        {
            var found=new List<TileEntityComposite>();
            foreach(var chunk in world.ChunkCache.chunks.dict.Values.ToArray())
            {
                chunk.EnterReadLock();
                try
                {
                    foreach(var raw in chunk.tileEntities.dict.Values)
                    {
                        var te=raw as TileEntityComposite;
                        if(te==null || !WithdrawalPlan.InRange(((Vector3)te.ToWorldPos()-player.position).sqrMagnitude,Plugin.Config.AlcanceMetros))continue;
                        var storage=te.GetFeature<TEFeatureStorage>();
                        if(storage==null || !storage.ItemGrid.PlayerOwned || te.IsUserAccessing() || LockManager.Instance.IsLockedServer(te))continue;
                        var access=te.GetFeature<TEFeatureLockable>();
                        if(access!=null && access.IsLocked() && !access.IsUserAllowed(owner))continue;
                        found.Add(te);
                    }
                }
                finally {chunk.ExitReadLock();}
            }
            return found.OrderBy(t=>((Vector3)t.ToWorldPos()-player.position).sqrMagnitude).ThenBy(t=>t.ToWorldPos().x).ThenBy(t=>t.ToWorldPos().y).ThenBy(t=>t.ToWorldPos().z).ToList();
        }
        private static List<Source> Sources(World world,EntityPlayer player,PlatformUserIdentifierAbs owner)
        {
            var found=new List<Source>();
            foreach(var te in Containers(world,player,owner))
            {
                var storage=te.GetFeature<TEFeatureStorage>();
                for(int i=0;i<storage.ItemGrid.items.Length;i++)
                    if(!storage.ItemGrid.IsSlotLocked(i)&&Eligible(storage.ItemGrid.items[i]))found.Add(new Source{Storage=storage,Index=i,Parent=te});
            }
            return found;
        }
        private static void Reply(NetPackageCraftingDosBaus request,ClientInfo sender,byte kind,List<ItemStack> items,string message)
        {
            var reply=NetPackageManager.GetPackage<NetPackageCraftingDosBaus>().Setup(kind,request.Player,request.Id,items,message);
            if(sender==null)Client.Receive(reply);else sender.SendPackage(reply);
        }
    }
}

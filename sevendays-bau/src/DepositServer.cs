using System;
using System.Collections.Generic;
using System.Linq;
using Platform;

namespace CraftingDosBaus
{
    internal static class DepositServer
    {
        private sealed class Receipt
        {internal List<ItemStack> Remainder;internal string Message;internal bool Acknowledged;}
        private static readonly Dictionary<string,Receipt> Receipts=new Dictionary<string,Receipt>();
        internal static void Reset(){lock(Server.Gate)Receipts.Clear();}
        internal static void Handle(NetPackageGuardarNosBaus request,ClientInfo sender)
        {
            lock(Server.Gate)
            {
                if(!Guid.TryParse(request.Id,out _))return;
                var owner=sender?.InternalId??PlatformManager.InternalLocalUserIdentifier;
                if(owner==null)return;
                string key=owner.CombinedString+":"+request.Id;
                if(request.Kind==NetPackageGuardarNosBaus.Ack)
                {if(Receipts.TryGetValue(key,out var ack)){ack.Acknowledged=true;ack.Remainder=null;}return;}
                if(request.Kind==NetPackageGuardarNosBaus.Deposit&&Receipts.TryGetValue(key,out var old))
                {
                    if(!old.Acknowledged)Reply(request,sender,NetPackageGuardarNosBaus.Result,ItemStack.Clone(old.Remainder).ToList(),old.Message);
                    return;
                }
                var world=GameManager.Instance?.World;
                var player=world?.GetEntity(request.Player) as EntityPlayer;
                var targets=player==null||player.IsDead()?new List<TileEntityComposite>():Server.Containers(world,player,owner);
                if(request.Kind==NetPackageGuardarNosBaus.Query)
                {
                    var types=new Dictionary<int,ItemStack>();
                    foreach(var te in targets)
                    {
                        var grid=te.GetFeature<TEFeatureStorage>().ItemGrid;
                        for(int i=0;i<grid.items.Length;i++)
                        {
                            var s=grid.items[i];
                            if(grid.IsSlotLocked(i)||s.IsEmpty()||types.ContainsKey(s.itemValue.type)||types.Count>=2048)continue;
                            var item=s.Clone();item.count=1;types.Add(item.itemValue.type,item);
                        }
                    }
                    Reply(request,sender,NetPackageGuardarNosBaus.Offer,types.Values.ToList(),"");return;
                }
                if(request.Kind!=NetPackageGuardarNosBaus.Deposit)return;
                if(request.Stacks.Count<1||request.Stacks.Count>128||request.Stacks.Any(s=>s==null||s.IsEmpty()||s.itemValue.ItemClass==null||s.count>s.itemValue.ItemClass.MaxCount))
                    return; // corrupted request is never written into a chest
                var storages=targets.Select(t=>t.GetFeature<TEFeatureStorage>()).ToArray();
                var originals=storages.Select(s=>ItemStack.Clone(s.ItemGrid.items)).ToArray();
                var plans=storages.Select(s=>new DepositPlan.Container(s.ItemGrid)).ToList();
                List<ItemStack> unused;
                string message;
                if(Receipts.Count>=20000)
                {Reply(request,sender,NetPackageGuardarNosBaus.Result,ItemStack.Clone(request.Stacks).ToList(),"Limite da sessão; reinicie a partida.");return;}
                try
                {
                    unused=DepositPlan.Build(plans,request.Stacks);
                    for(int i=0;i<plans.Count;i++)if(plans[i].Changed)storages[i].ItemGrid.SetItems(plans[i].Slots,false);
                    for(int i=0;i<plans.Count;i++)if(plans[i].Changed)storages[i].SetModified();
                    int stored=request.Stacks.Sum(s=>s.count)-unused.Sum(s=>s.count);
                    message=stored>0?"Guardados "+stored+" itens. O que não coube continua com você.":"Nenhum espaço disponível nos baús correspondentes.";
                }
                catch(Exception e)
                {
                    for(int i=0;i<plans.Count;i++)if(plans[i].Changed)
                    {
                        storages[i].ItemGrid.SetItems(originals[i],false);
                        try{storages[i].SetModified();}catch(Exception notify){Log.Error("[CraftingDosBaus] Reversão depósito: "+notify.Message);}
                    }
                    Log.Error("[CraftingDosBaus] Depósito revertido: "+e);
                    unused=ItemStack.Clone(request.Stacks).ToList();message="Não foi possível guardar; itens devolvidos.";
                }
                Receipts[key]=new Receipt{Remainder=ItemStack.Clone(unused).ToList(),Message=message};
                Reply(request,sender,NetPackageGuardarNosBaus.Result,unused,message);
            }
        }
        private static void Reply(NetPackageGuardarNosBaus request,ClientInfo sender,byte kind,List<ItemStack> items,string message)
        {
            var p=NetPackageManager.GetPackage<NetPackageGuardarNosBaus>().Setup(kind,request.Player,request.Id,items,message);
            if(sender==null)QuickStore.Receive(p);else sender.SendPackage(p);
        }
    }
}

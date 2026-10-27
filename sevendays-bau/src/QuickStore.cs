using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace CraftingDosBaus
{
    internal static class QuickStore
    {
        private sealed class Pending
        {
            internal string Id;
            internal int Player;
            internal bool Depositing;
            internal float Started,RetryAt;
            internal List<DepositPlan.Carried> Sources;
            internal List<ItemStack> Escrow,Refund;
            internal string Message;
            internal bool Dropped;
        }
        private static Pending pending;
        private static readonly HashSet<string> Received=new HashSet<string>();
        internal static bool Busy=>pending!=null;
        internal static void Reset(){pending=null;}
        internal static void Pressed(XUiController sender,int mouseButton)
        {
            if(mouseButton!=0||!Plugin.Ready)return;
            var player=Client.Player;if(player==null||player.IsDead())return;
            if(Busy||Client.Busy){Client.Say("Aguarde a transferência atual terminar.");return;}
            pending=new Pending{Id=Guid.NewGuid().ToString("N"),Player=player.entityId,Started=Time.realtimeSinceStartup};
            Client.Say("Procurando baús próximos...");
            SendPending();
        }
        internal static void Tick()
        {
            if(pending==null||Client.Player==null)return;
            if(pending.Refund!=null){FinishReturn();return;}
            if(!pending.Depositing&&Time.realtimeSinceStartup-pending.Started>12)
            {pending=null;Client.Say("O servidor não respondeu. Nenhum item foi retirado.");return;}
            if(Time.realtimeSinceStartup>=pending.RetryAt)SendPending();
        }
        private static void SendPending()
        {
            var task=pending;if(task==null)return;
            task.RetryAt=Time.realtimeSinceStartup+3;
            Send(NetPackageManager.GetPackage<NetPackageGuardarNosBaus>().Setup(task.Depositing?NetPackageGuardarNosBaus.Deposit:NetPackageGuardarNosBaus.Query,
                task.Player,task.Id,task.Escrow==null?null:ItemStack.Clone(task.Escrow).ToList()));
        }
        private static void Send(NetPackageGuardarNosBaus packet)
        {
            if(SingletonMonoBehaviour<ConnectionManager>.Instance.IsServer)DepositServer.Handle(packet,null);
            else SingletonMonoBehaviour<ConnectionManager>.Instance.SendToServer(packet,true);
        }
        internal static void Receive(NetPackageGuardarNosBaus packet)
        {
            var player=Client.Player;if(player==null||player.entityId!=packet.Player)return;
            if(packet.Kind==NetPackageGuardarNosBaus.Result&&Received.Contains(packet.Id)){Ack(packet.Player,packet.Id);return;}
            var task=pending;if(task==null||task.Id!=packet.Id)return;
            if(packet.Kind==NetPackageGuardarNosBaus.Offer&&!task.Depositing)
            {
                if(player.IsDead()||Client.Busy){pending=null;return;}
                var types=new HashSet<int>(packet.Stacks.Select(s=>s.itemValue.type));
                var selected=DepositPlan.Select(new[]{player.bag.ItemGrid,player.inventory.ItemGrid},types);
                if(selected.Count==0){pending=null;Client.Say("Nenhum item desbloqueado corresponde aos baús próximos.");return;}
                task.Sources=selected;task.Escrow=selected.Select(s=>s.Original.Clone()).ToList();
                // Reserve exact slots once, before sending; retries reuse the SAME reservation.
                try
                {
                    foreach(var slot in selected)slot.Grid.SetItem(slot.Index,ItemStack.Empty);
                }
                catch(Exception e)
                {
                    foreach(var slot in selected)slot.Grid.SetItem(slot.Index,slot.Original);
                    pending=null;Log.Error("[CraftingDosBaus] Não foi possível reservar itens: "+e);return;
                }
                task.Depositing=true;Client.Say("Guardando nos baús correspondentes...");SendPending();
            }
            else if(packet.Kind==NetPackageGuardarNosBaus.Result&&task.Depositing&&task.Refund==null)
            {
                if(!DepositPlan.ValidRefund(task.Escrow,packet.Stacks))
                {Log.Error("[CraftingDosBaus] Resposta de depósito inconsistente; reserva mantida.");return;}
                task.Refund=ItemStack.Clone(packet.Stacks).ToList();task.Message=packet.Message;
                FinishReturn();
            }
        }
        private static void FinishReturn()
        {
            var task=pending;var player=Client.Player;
            var inventory=player==null?null:LocalPlayerUI.GetUIForPlayer(player)?.xui?.PlayerInventory;
            if(task?.Refund==null||inventory==null)return;
            try
            {
                foreach(var item in task.Refund.Where(s=>s.count>0))
                {
                    // Prefer original slots, including the hotbar, if still available.
                    foreach(var source in task.Sources.Where(s=>s.Original.itemValue.Equals(item.itemValue)))
                    {
                        if(source.Grid.IsSlotLocked(source.Index))continue;
                        var current=source.Grid.items[source.Index];
                        if(!current.IsEmpty()&&!current.itemValue.Equals(item.itemValue))continue;
                        int count=Math.Min(item.count,item.itemValue.ItemClass.MaxCount-(current.IsEmpty()?0:current.count));
                        if(count<=0)continue;
                        var restored=item.Clone();restored.count=count+(current.IsEmpty()?0:current.count);
                        source.Grid.SetItem(source.Index,restored);item.count-=count;
                    }
                    if(item.count>0)inventory.AddItem(item);
                    if(item.count>0)
                    {
                        GameManager.Instance.ItemDropServer(item.Clone(),player.position+Vector3.up,Vector3.zero,player.entityId,300f);
                        item.count=0;task.Dropped=true;
                    }
                }
                Received.Add(task.Id);pending=null;Client.Invalidate();
                Client.Say(task.Message+(task.Dropped?" Mochila cheia: recolha o restante no chão ao seu lado.":""));Ack(task.Player,task.Id);
            }
            catch(Exception e){Log.Error("[CraftingDosBaus] Devolução pendente: "+e.Message);}
        }
        private static void Ack(int player,string id)
        {Send(NetPackageManager.GetPackage<NetPackageGuardarNosBaus>().Setup(NetPackageGuardarNosBaus.Ack,player,id));}
    }
}

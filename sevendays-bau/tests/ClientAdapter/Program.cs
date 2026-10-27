using System;
using System.Linq;
using System.Collections.Generic;
using CraftingDosBaus;
using UnityEngine;
static class Tests {
 static int checks;
 static void Check(bool value,string message){checks++;if(!value)throw new Exception(message);}
 static ItemStack Item(int type,int n)=>new ItemStack{itemValue=new ItemValue{type=type},count=n};
 static ConnectionManager Net=>SingletonMonoBehaviour<ConnectionManager>.Instance;
 static ItemStackGrid Bag=>Client.Player.bag.ItemGrid;
 static ItemStackGrid Belt=>Client.Player.inventory.ItemGrid;
 static void Reset(){QuickStore.Reset();Client.Player=new EntityPlayerLocal();Client.Busy=false;Net.Sent.Clear();Net.IsServer=false;GameManager.Instance.Drops.Clear();LocalPlayerUI.Current=new LocalPlayerUI();Time.realtimeSinceStartup=100;Log.Errors.Clear();}
 static string Start(){QuickStore.Pressed(null,0);return Net.Sent.Last().Id;}
 static void Offer(string id,params ItemStack[] types)=>QuickStore.Receive(new NetPackageGuardarNosBaus().Setup(NetPackageGuardarNosBaus.Offer,1,id,types.ToList()));
 static void Result(string id,params ItemStack[] refund)=>QuickStore.Receive(new NetPackageGuardarNosBaus().Setup(NetPackageGuardarNosBaus.Result,1,id,refund.ToList(),"Concluído"));
 static int Count(int type)=>Bag.items.Concat(Belt.items).Where(s=>s.itemValue.type==type).Sum(s=>s.count);
 static void Main(){
  Reset();Bag.SetItem(0,Item(1,7));Bag.SetItem(1,Item(2,9));Belt.SetItem(0,Item(1,5));string id=Start();
  Check(Net.Sent.Single().Kind==NetPackageGuardarNosBaus.Query&&Count(1)==12,"query must not remove anything");
  Offer(id,Item(1,1));Check(Count(1)==0&&Count(2)==9&&Net.Sent.Last().Stacks.Sum(s=>s.count)==12,"exact matching slots reserved once");
  int sent=Net.Sent.Count;QuickStore.Pressed(null,0);Check(Net.Sent.Count==sent,"double click blocked during reservation");
  Offer(id,Item(1,1));Check(Net.Sent.Count==sent&&Count(1)==0,"duplicate offer ignored");
  Time.realtimeSinceStartup+=4;QuickStore.Tick();Check(Net.Sent.Last().Id==id&&Net.Sent.Last().Stacks.Sum(s=>s.count)==12&&Count(1)==0,"retry reuses escrow without second removal");
  Result(id,Item(1,4));Check(Count(1)==4&&Count(2)==9&&!QuickStore.Busy&&Bag.items[0].count==4,"partial refund goes to original slot");
  Result(id,Item(1,4));Check(Count(1)==4&&Net.Sent.Last().Kind==NetPackageGuardarNosBaus.Ack,"duplicate result only acknowledged");
  Reset();Bag.SetItem(0,Item(1,7));Bag.Locked.Add(0);id=Start();Offer(id,Item(1,1));Check(Count(1)==7&&!QuickStore.Busy&&Net.Sent.Count==1,"locked items never reserved");
  Reset();Bag.SetItem(0,Item(1,7));id=Start();Time.realtimeSinceStartup+=13;QuickStore.Tick();Offer(id,Item(1,1));Check(Count(1)==7&&!QuickStore.Busy&&Net.Sent.Count==1,"query timeout and late offer are harmless");
  Reset();Bag.SetItem(0,Item(1,7));id=Start();Offer(id,Item(1,1));Result(id,Item(1,8));Check(Count(1)==0&&QuickStore.Busy&&Log.Errors.Count==1,"invalid refund fails closed");
  Result(id,Item(1,7));Check(Count(1)==7&&!QuickStore.Busy,"corrected refund applies once");
  Reset();Belt.SetItem(0,Item(1,13));id=Start();Offer(id,Item(1,1));Result(id,Item(1,13));Check(Belt.items[0].count==13&&Bag.items.All(s=>s.IsEmpty()),"hotbar return restores original slot");
  Reset();Bag.SetItem(0,Item(1,7));id=Start();Offer(id,Item(1,1));
  foreach(var grid in new[]{Bag,Belt})for(int i=0;i<grid.items.Length;i++)grid.SetItem(i,Item(2,100));
  Result(id,Item(1,7));Check(Count(2)==800&&Count(1)==0&&GameManager.Instance.Drops.Sum(s=>s.count)==7&&!QuickStore.Busy,"occupied original slots preserve other items and drop exact overflow");
  Check(Client.Messages.Last().Contains("chão"),"overflow warning survives success tooltip");
  Reset();Bag.SetItem(0,Item(1,7));id=Start();Offer(id,Item(1,1));LocalPlayerUI.Current=null;Result(id,Item(1,7));Check(QuickStore.Busy&&Count(1)==0,"unavailable UI defers refund and acknowledgement");
  LocalPlayerUI.Current=new LocalPlayerUI();QuickStore.Tick();Check(!QuickStore.Busy&&Count(1)==7,"refund finishes when UI returns");
  Reset();Client.Busy=true;Bag.SetItem(0,Item(1,7));QuickStore.Pressed(null,0);Check(Net.Sent.Count==0&&Count(1)==7,"crafting transfer prevents concurrent escrow");
  for(int i=1;i<=100;i++){
   Reset();Bag.SetItem(0,Item(1,i));id=Start();Offer(id,Item(1,1));for(int r=0;r<3;r++){Time.realtimeSinceStartup+=4;QuickStore.Tick();}
   Result(id,Item(1,i));for(int r=0;r<3;r++)Result(id,Item(1,i));Check(Count(1)==i&&!QuickStore.Busy,"retries conserve quantity "+i);
  }
  Console.WriteLine(checks+" client escrow checks passed using production QuickStore.cs and DepositPlan.cs with controlled UI/inventory/network stand-ins.");
 }
}

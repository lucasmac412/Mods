using System;
using System.Linq;
using System.IO;
using System.Collections.Generic;
using System.Threading.Tasks;
using CraftingDosBaus;
using Platform;
using UnityEngine;

static class Test {
 static int checks;
 static void Check(bool ok,string text){checks++;if(!ok)throw new Exception(text);}
 static ItemStack Item(int type,int count)=>new ItemStack{itemValue=new ItemValue{type=type},count=count};
 static ClientInfo Peer(int id,string owner)=>new ClientInfo{entityId=id,InternalId=new PlatformUserIdentifierAbs(owner)};
 static void Reset(){Server.Reset();Client.Replies.Clear();GameManager.Instance.World=new World();var w=GameManager.Instance.World;w.Players[1]=new EntityPlayer();w.Players[2]=new EntityPlayer();w.ChunkCache.chunks.dict[0]=new Chunk();Time.realtimeSinceStartup=100;}
 static TileEntityComposite Chest(int x,params ItemStack[] items){var t=new TileEntityComposite{Position=new Vector3i{x=x},Storage=new TEFeatureStorage{ItemGrid=new ItemStackGrid{items=items}}};var d=GameManager.Instance.World.ChunkCache.chunks.dict[0].tileEntities.dict;d[d.Count]=t;return t;}
 static NetPackageCraftingDosBaus Request(ClientInfo peer,string id,params ItemStack[] items){var p=new NetPackageCraftingDosBaus().Setup(NetPackageCraftingDosBaus.Withdraw,peer.entityId,id,items.ToList());p.Sender=peer;p.ProcessPackage(GameManager.Instance.World,GameManager.Instance);return p;}
 static void Main(){
  Reset();var guest=Peer(2,"guest");var a=Chest(5,Item(1,3));var b=Chest(10,Item(1,8),Item(2,4));string id=Guid.NewGuid().ToString("N");
  Request(guest,id,Item(1,7),Item(2,2));Check(a.Storage.ItemGrid.items[0].count==0&&b.Storage.ItemGrid.items[0].count==4&&b.Storage.ItemGrid.items[1].count==2,"Exact nearest-first all-ingredient debit");
  Check(guest.Replies.Last().Kind==NetPackageCraftingDosBaus.Grant&&guest.Replies.Last().Stacks.Sum(s=>s.count)==9,"Exact delivery contents");
  Request(guest,id,Item(1,7),Item(2,2));Check(b.Storage.ItemGrid.items[0].count==4&&guest.Replies.Last().Kind==NetPackageCraftingDosBaus.Grant,"Retry returns receipt without second debit");
  var ack=new NetPackageCraftingDosBaus().Setup(NetPackageCraftingDosBaus.Ack,2,id);ack.Sender=guest;ack.ProcessPackage(GameManager.Instance.World,GameManager.Instance);
  Request(guest,id,Item(1,7));Check(b.Storage.ItemGrid.items[0].count==4&&guest.Replies.Last().Kind==NetPackageCraftingDosBaus.Denied,"Acknowledged ID cannot spend twice");
  foreach(string exclusion in new[]{"distance","open","busy","locked","slot","worldloot","mods","height"}){
   Reset();guest=Peer(2,"guest");var t=Chest(5,Item(1,10));
   switch(exclusion){case "distance":t.Position.x=51;break;case "height":t.Position.y=51;break;case "open":t.Open=true;break;case "busy":t.Busy=true;break;case "locked":t.Lock=new TEFeatureLockable{Locked=true,Owner="stranger"};break;case "slot":t.Storage.ItemGrid.Locked.Add(0);break;case "worldloot":t.Storage.ItemGrid.PlayerOwned=false;break;case "mods":t.Storage.ItemGrid.items[0].itemValue.HasModSlots=true;t.Storage.ItemGrid.items[0].itemValue.Modded=true;break;}
   Request(guest,Guid.NewGuid().ToString("N"),Item(1,5));Check(t.Storage.ItemGrid.items[0].count==10&&guest.Replies.Single().Kind==NetPackageCraftingDosBaus.Denied,"Excluded: "+exclusion);
   Check(GameManager.Instance.World.ChunkCache.chunks.dict[0].ReadLocks==0,"Chunk lock released: "+exclusion);
  }
  Reset();guest=Peer(2,"guest");a=Chest(50,Item(1,5));a.Lock=new TEFeatureLockable{Locked=true,Owner="guest"};Request(guest,Guid.NewGuid().ToString("N"),Item(1,5));Check(a.Storage.ItemGrid.items[0].count==0,"Inclusive 50m and owner of locked chest");
  Reset();guest=Peer(2,"guest");a=Chest(1,Item(1,5));Request(guest,Guid.NewGuid().ToString("N"),Item(1,4),Item(2,1));Check(a.Storage.ItemGrid.items[0].count==5,"Insufficient second ingredient leaves first intact");
  Reset();guest=Peer(2,"guest");a=Chest(1,Item(1,5));var spoof=new NetPackageCraftingDosBaus().Setup(NetPackageCraftingDosBaus.Withdraw,1,Guid.NewGuid().ToString("N"),new List<ItemStack>{Item(1,5)});spoof.Sender=guest;spoof.ProcessPackage(GameManager.Instance.World,GameManager.Instance);Check(a.Storage.ItemGrid.items[0].count==5&&guest.Replies.Count==0,"Authenticated sender cannot spoof another player");
  foreach(bool permanent in new[]{false,true}){
   Reset();guest=Peer(2,"guest");a=Chest(1,Item(1,5));a.Storage.FailOnce=!permanent;a.Storage.FailAlways=permanent;
   Request(guest,Guid.NewGuid().ToString("N"),Item(1,3));Check(a.Storage.ItemGrid.items[0].count==5,"Rollback after storage notification failure");Check(guest.Replies.Last().Kind==NetPackageCraftingDosBaus.Denied,"Failure never grants ingredients");
  }
  // Two actual production request handlers compete for the same last stack.
  for(int round=0;round<100;round++){
   Reset();a=Chest(1,Item(1,7));var p1=Peer(1,"host");var p2=Peer(2,"guest");
   Parallel.Invoke(()=>Request(p1,Guid.NewGuid().ToString("N"),Item(1,7)),()=>Request(p2,Guid.NewGuid().ToString("N"),Item(1,7)));
   Check(p1.Replies.Concat(p2.Replies).Count(p=>p.Kind==NetPackageCraftingDosBaus.Grant)==1,"Concurrent last stack has a single winner");
   Check(a.Storage.ItemGrid.items[0].count==0,"Concurrent debit never negative or duplicated");
  }
  // Property tests run the SAME planning routine used before production debits.
  var rng=new Random(3406);
  for(int k=0;k<1000;k++){
   var slots=Enumerable.Range(0,20).Select(_=>new WithdrawalPlan.Slot{Type=rng.Next(1,5),Count=rng.Next(0,100),Allowed=rng.Next(0,3)!=0}).ToList();
   var wanted=Enumerable.Range(1,4).ToDictionary(i=>i,_=>rng.Next(1,250));var plan=WithdrawalPlan.Build(slots,wanted);
   bool enough=wanted.All(p=>slots.Where(s=>s.Allowed&&s.Type==p.Key).Sum(s=>s.Count)>=p.Value);
   Check((plan!=null)==enough,"Planner agrees with independent totals");if(plan!=null)foreach(var p in wanted)Check(plan.Where(t=>slots[t.Index].Type==p.Key).Sum(t=>t.Count)==p.Value,"Plan conserves each ingredient");
  }
  Check(WithdrawalPlan.InRange(2500,50)&&!WithdrawalPlan.InRange(2500.001,50)&&!WithdrawalPlan.InRange(double.NaN,50),"Distance boundary and invalid values");
  // Wire round-trip uses the production packet class (fake ItemStack codec only).
  var wire=new NetPackageCraftingDosBaus().Setup(NetPackageCraftingDosBaus.Withdraw,2,Guid.NewGuid().ToString("N"),new List<ItemStack>{Item(5,12)});
  using(var ms=new MemoryStream()){wire.write(new PooledBinaryWriter(ms));ms.Position=0;var copy=new NetPackageCraftingDosBaus();copy.read(new PooledBinaryReader(ms));Check(copy.Id==wire.Id&&copy.Player==2&&copy.Stacks[0].count==12,"Packet round trip");}
  Console.WriteLine(checks+" server/planner/wire checks passed using production Server.cs + Network.cs + WithdrawalPlan.cs and controlled game stand-ins.");
  DepositTests.Run();
  Console.WriteLine("Not a real Steam multiplayer/Unity client test.");
 }
}

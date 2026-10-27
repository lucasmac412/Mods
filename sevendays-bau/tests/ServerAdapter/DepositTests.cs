using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using CraftingDosBaus;
using Platform;

static class DepositTests
{
    static int checks;
    static void Check(bool ok,string text){checks++;if(!ok)throw new Exception("Deposit: "+text);}
    static ItemStack Item(int type,int n,int meta=0)=>new ItemStack{itemValue=new ItemValue{type=type,Meta=meta},count=n};
    static ClientInfo Peer(int id)=>new ClientInfo{entityId=id,InternalId=new PlatformUserIdentifierAbs("peer"+id)};
    static void Reset()
    {
        Server.Reset();DepositServer.Reset();GameManager.Instance.World=new World();
        GameManager.Instance.World.Players[1]=new EntityPlayer();GameManager.Instance.World.Players[2]=new EntityPlayer();
        GameManager.Instance.World.ChunkCache.chunks.dict[0]=new Chunk();
    }
    static TileEntityComposite Chest(int x,params ItemStack[] items)
    {
        var t=new TileEntityComposite{Position=new Vector3i{x=x},Storage=new TEFeatureStorage{ItemGrid=new ItemStackGrid{items=items}}};
        var d=GameManager.Instance.World.ChunkCache.chunks.dict[0].tileEntities.dict;d[d.Count]=t;return t;
    }
    static NetPackageGuardarNosBaus Request(ClientInfo peer,string id,params ItemStack[] items)
    {
        var p=new NetPackageGuardarNosBaus().Setup(NetPackageGuardarNosBaus.Deposit,peer.entityId,id,items.ToList());
        p.Sender=peer;p.ProcessPackage(GameManager.Instance.World,GameManager.Instance);return p;
    }
    static int Count(TileEntityComposite t,int type)=>t.Storage.ItemGrid.items.Where(s=>s.itemValue.type==type).Sum(s=>s.count);
    public static int Run()
    {
        Reset();var p=Peer(1);var a=Chest(1,Item(1,95),Item(0,0));var b=Chest(2,Item(1,90),Item(0,0));var unrelated=Chest(0,Item(2,5),Item(0,0));
        string id=Guid.NewGuid().ToString("N");Request(p,id,Item(1,40),Item(3,4));
        Check(Count(a,1)==125&&Count(b,1)==100,"fills all existing stacks before empty slots in nearest matching chest");
        Check(Count(unrelated,1)==0&&Count(unrelated,3)==0,"unrelated and empty chests do not accept new categories");
        Check(p.Deposits.Last().Stacks.Single().itemValue.type==3&&p.Deposits.Last().Stacks.Single().count==4,"unmatched items returned exactly");
        Request(p,id,Item(1,40),Item(3,4));Check(Count(a,1)==125&&p.Deposits.Last().Stacks.Single().count==4,"retry never stores twice");
        var ack=new NetPackageGuardarNosBaus().Setup(NetPackageGuardarNosBaus.Ack,1,id);ack.Sender=p;ack.ProcessPackage(GameManager.Instance.World,GameManager.Instance);
        int replies=p.Deposits.Count;Request(p,id,Item(1,40));Check(p.Deposits.Count==replies&&Count(a,1)==125,"acknowledged transaction cannot replay");
        foreach(var exclusion in new[]{"far","vertical","open","busy","private","world","locked"})
        {
            Reset();p=Peer(1);a=Chest(1,Item(1,90),Item(0,0));
            switch(exclusion){case "far":a.Position.x=51;break;case "vertical":a.Position.y=51;break;case "open":a.Open=true;break;case "busy":a.Busy=true;break;case "private":a.Lock=new TEFeatureLockable{Locked=true,Owner="other"};break;case "world":a.Storage.ItemGrid.PlayerOwned=false;break;case "locked":a.Storage.ItemGrid.Locked.Add(0);break;}
            Request(p,Guid.NewGuid().ToString("N"),Item(1,25));
            Check(Count(a,1)==90&&p.Deposits.Last().Stacks.Sum(s=>s.count)==25,"exclusion: "+exclusion);
        }
        Reset();p=Peer(1);a=Chest(50,Item(1,99));a.Lock=new TEFeatureLockable{Locked=true,Owner="peer1"};Request(p,Guid.NewGuid().ToString("N"),Item(1,10));
        Check(Count(a,1)==100&&p.Deposits.Last().Stacks.Sum(s=>s.count)==9,"inclusive radius, owner access and capacity refund");
        Reset();p=Peer(1);a=Chest(1,Item(1,50),Item(0,0));a.Storage.ItemGrid.Locked.Add(1);Request(p,Guid.NewGuid().ToString("N"),Item(1,80));
        Check(Count(a,1)==100&&a.Storage.ItemGrid.items[1].IsEmpty()&&p.Deposits.Last().Stacks.Sum(s=>s.count)==30,"locked empty slot never used");
        Reset();p=Peer(1);a=Chest(1,Item(1,50,1),Item(0,0));Request(p,Guid.NewGuid().ToString("N"),Item(1,12,2));
        Check(a.Storage.ItemGrid.items[0].count==50&&a.Storage.ItemGrid.items[1].itemValue.Meta==2,"different item metadata not destroyed by stacking");
        foreach(bool permanent in new[]{false,true})
        {
            Reset();p=Peer(1);a=Chest(1,Item(1,90));b=Chest(2,Item(1,90));b.Storage.FailOnce=!permanent;b.Storage.FailAlways=permanent;
            Request(p,Guid.NewGuid().ToString("N"),Item(1,20));
            Check(Count(a,1)==90&&Count(b,1)==90&&p.Deposits.Last().Stacks.Sum(s=>s.count)==20,"multi-chest write failure restores originals and refunds all");
        }
        for(int i=0;i<100;i++)
        {
            Reset();a=Chest(1,Item(1,99));var p1=Peer(1);var p2=Peer(2);
            Parallel.Invoke(()=>Request(p1,Guid.NewGuid().ToString("N"),Item(1,1)),()=>Request(p2,Guid.NewGuid().ToString("N"),Item(1,1)));
            Check(Count(a,1)==100&&p1.Deposits.Concat(p2.Deposits).Sum(r=>r.Stacks.Sum(s=>s.count))==1,"two players competing for final space conserve items");
        }
        var random=new Random(2034);
        for(int i=0;i<1000;i++)
        {
            var grids=Enumerable.Range(0,4).Select(_=>new ItemStackGrid{items=Enumerable.Range(0,8).Select(__=>Item(random.Next(0,4),random.Next(1,101))).ToArray()}).ToArray();
            foreach(var grid in grids)grid.Locked.Add(random.Next(0,8));
            var inputs=Enumerable.Range(1,4).Select(t=>Item(t,random.Next(1,101))).ToList();
            var plan=grids.Select(g=>new DepositPlan.Container(g)).ToList();
            var original=plan.Select(c=>ItemStack.Clone(c.Slots)).ToArray();
            var left=DepositPlan.Build(plan,inputs);
            foreach(int type in Enumerable.Range(1,4))
            {
                int before=original.SelectMany(s=>s).Where(s=>!s.IsEmpty()&&s.itemValue.type==type).Sum(s=>s.count);
                int after=plan.SelectMany(c=>c.Slots).Where(s=>!s.IsEmpty()&&s.itemValue.type==type).Sum(s=>s.count);
                Check(after-before+left.Where(s=>s.itemValue.type==type).Sum(s=>s.count)==inputs.Single(s=>s.itemValue.type==type).count,"randomized conservation");
            }
            for(int n=0;n<plan.Count;n++)
            for(int slot=0;slot<plan[n].Slots.Length;slot++)
            {
                var value=plan[n].Slots[slot];
                Check(value.count<=100,"never overfill a slot");
                if(grids[n].IsSlotLocked(slot))Check(value.count==original[n][slot].count&&value.itemValue.Equals(original[n][slot].itemValue),"locked slot invariant");
                if(!value.IsEmpty())Check(plan[n].Existing.Contains(value.itemValue.type)||grids[n].IsSlotLocked(slot),"only original categories in each chest");
            }
        }
        var carried=new ItemStackGrid{items=new[]{Item(1,4),Item(1,6),Item(2,8)}};carried.Locked.Add(0);
        var hotbar=new ItemStackGrid{items=new[]{Item(1,7)}};
        var selected=DepositPlan.Select(new[]{carried,hotbar},new HashSet<int>{1});
        Check(selected.Count==2&&selected.Sum(s=>s.Original.count)==13,"backpack and hotbar matching, locked slots excluded");
        Check(carried.items[1].count==6&&hotbar.items[0].count==7,"selection does not remove inventory contents");
        Check(DepositPlan.ValidRefund(new[]{Item(1,4)},new[]{Item(1,2)}),"partial refund valid");
        Check(!DepositPlan.ValidRefund(new[]{Item(1,4)},new[]{Item(1,3),Item(1,3)})&&!DepositPlan.ValidRefund(new[]{Item(1,4)},new[]{Item(2,1)}),"duplicated or invented refunds rejected");
        Reset();p=Peer(1);a=Chest(1,Item(1,5));var spoof=new NetPackageGuardarNosBaus().Setup(NetPackageGuardarNosBaus.Deposit,2,Guid.NewGuid().ToString("N"),new List<ItemStack>{Item(1,10)});spoof.Sender=p;spoof.ProcessPackage(GameManager.Instance.World,GameManager.Instance);
        Check(Count(a,1)==5&&p.Deposits.Count==0,"authenticated peer cannot select another player");
        var wire=new NetPackageGuardarNosBaus().Setup(NetPackageGuardarNosBaus.Deposit,1,Guid.NewGuid().ToString("N"),new List<ItemStack>{Item(1,12)});
        using(var ms=new MemoryStream()){wire.write(new PooledBinaryWriter(ms));ms.Position=0;var copy=new NetPackageGuardarNosBaus();copy.read(new PooledBinaryReader(ms));Check(copy.Id==wire.Id&&copy.Stacks.Single().count==12,"deposit packet roundtrip");}
        Console.WriteLine(checks+" deposit checks passed using production planner, server and packet with controlled game stand-ins.");
        return checks;
    }
}

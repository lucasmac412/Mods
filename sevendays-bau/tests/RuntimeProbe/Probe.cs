using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.Serialization;
using System.Xml.XPath;
using HarmonyLib;
using CraftingDosBaus;

// Test-only mod, never included in the user ZIP. Runs inside the official 3.4 server.
public sealed class Probe : IModApi
{
    int checks;
    public void InitMod(Mod mod) { ModEvents.GameStartDone.RegisterHandler(Run); }
    void Check(bool value,string label)
    { if(!value)throw new Exception(label);checks++;Log.Out("[BAUS-PROBE] OK "+label); }
    void Run(ref ModEvents.SGameStartDoneData data)
    {
        try
        {
            Check(Plugin.Ready,"all Harmony hooks installed in native engine");
            int id=NetPackageManager.GetPackageId(typeof(NetPackageCraftingDosBaus));
            Check(id>0,"custom packet registered by game: "+id);
            var wood=ItemClass.GetItem("resourceWood");
            Check(wood.type>0,"real resource registry loaded");
            var bag=new Bag(new Vector2i(8,6),XUiC_ItemStack.StackLocationTypes.Backpack,null);
            var belt=new Inventory(ItemStackGrid.Create(new Vector2i(10,1),XUiC_ItemStack.StackLocationTypes.ToolBelt,false,false,null),0);
            var inv=new XUiM_PlayerInventory(null,null);
            AccessTools.Field(typeof(XUiM_PlayerInventory),"backpack").SetValue(inv,bag);
            AccessTools.Field(typeof(XUiM_PlayerInventory),"toolbelt").SetValue(inv,belt);
            AccessTools.Field(typeof(XUiM_PlayerInventory),"currencyItem").SetValue(inv,ItemClass.GetItem(TraderInfo.CurrencyItem));
            for(int iteration=1;iteration<=20;iteration++)
            {
                bag.SetSlot(0,new ItemStack(wood.Clone(),3*iteration));
                Client.Wallet=new List<ItemStack>{new ItemStack(wood.Clone(),7*iteration)};
                Client.Completing=false;Client.Scope=0;Client.Suppress=false;
                Check(inv.GetItemCount(wood)==3*iteration,"outside crafting ignores virtual stock "+iteration);
                Client.Completing=true;Client.Spent=false;
                Check(inv.GetItemCount(wood)==10*iteration,"authorized wallet included "+iteration);
                var needs=new List<ItemStack>{new ItemStack(wood.Clone(),5)};
                Check(inv.HasItems(needs,2*iteration),"native HasItems patched "+iteration);
                var removed=new List<ItemStack>();
                inv.RemoveItems(needs,2*iteration,removed);
                Check(bag.GetItemCount(wood)==0,"native backpack debited "+iteration);
                Check(Client.Wallet.Sum(s=>s.count)==0&&Client.Spent,"remote balance debited "+iteration);
                Check(removed.Sum(s=>s.count)==10*iteration,"native cancellation keeps full actual ingredients "+iteration);
            }
            Client.Reset();
            var station=new ProbeStation();
            station.SetSlots(new[]{new ItemStack(wood.Clone(),4)});
            Client.Wallet=new List<ItemStack>{new ItemStack(wood.Clone(),6)};Client.Completing=true;Client.Spent=false;
            Check(!station.HasItems(new[]{new ItemStack(wood.Clone(),5)},2),"station ignores remote wallet");
            Check(station.GetItemCount(wood)==4,"station counts only native input");
            var stationRemoved=new List<ItemStack>();
            station.RemoveItems(new[]{new ItemStack(wood.Clone(),4)},1,stationRemoved);
            Check(station.GetSlots()[0].IsEmpty()&&Client.Wallet[0].count==6&&!Client.Spent,"station consumption leaves remote wallet untouched");
            Check(stationRemoved.Sum(s=>s.count)==4&&station.Updates>0,"station uses native refund and backend");
            foreach(string method in new[]{"GetItemCount","HasItems","RemoveItems"})
                Check(Harmony.GetPatchInfo(AccessTools.Method(typeof(XUiC_WorkstationInputGrid),method))==null,"no workstation patch: "+method);
            Client.Reset();
            foreach(string area in new[]{"","forge","cementMixer","workbench","campfire","chemistryStation"})
            {
                XUiC_CraftingWindowGroup root=area==""?new XUiC_CraftingWindowGroup():new XUiC_WorkstationWindowGroup();
                root.Workstation=area;
                var group=(XUiWindowGroup)FormatterServices.GetUninitializedObject(typeof(XUiWindowGroup));
                group.Controller=root;root.WindowGroup=group;
                var entry=new XUiC_IngredientEntry{WindowGroup=group};
                Check(Hooks.Personal(entry)==(area==""),"crafting context classified: "+area);
                object[] args={entry,null};
                AccessTools.Method(typeof(Hooks),"Enter").Invoke(null,args);
                Check(area==""?Client.Scope==1&&!Client.Suppress:Client.Scope==0&&Client.Suppress,"scoped remote counts only for player: "+area);
                AccessTools.Method(typeof(Hooks),"Leave").Invoke(null,new[]{args[1]});
                Check(Client.Scope==0&&!Client.Suppress,"crafting scope restored: "+area);
            }
            var chest=ItemStackGrid.Create(new Vector2i(3,1),XUiC_ItemStack.StackLocationTypes.Backpack,true,true,null);
            int max=wood.ItemClass.MaxCount;
            chest.SetItem(0,new ItemStack(wood.Clone(),max-2));
            var plan=new DepositPlan.Container(chest);
            var remainder=DepositPlan.Build(new[]{plan},new[]{new ItemStack(wood.Clone(),5)});
            Check(plan.Slots[0].count==max&&plan.Slots[1].count==3&&remainder.Count==0,"deposit follows native stacking capacity and fills matching chest");
            Check(chest.items[0].count==max-2&&chest.items[1].IsEmpty(),"deposit plan does not mutate actual grid");
            chest.SetItems(plan.Slots,false);
            Check(chest.items[0].count==max&&chest.items[1].count==3,"native grid accepts planned inventory");
            var unmatched=ItemStackGrid.Create(new Vector2i(3,1),XUiC_ItemStack.StackLocationTypes.Backpack,true,true,null);
            Check(DepositPlan.Build(new[]{new DepositPlan.Container(unmatched)},new[]{new ItemStack(wood.Clone(),5)}).Single().count==5,"empty chest is not a destination");
            bag.SetSlot(0,new ItemStack(wood.Clone(),7));bag.SetSlot(1,new ItemStack(wood.Clone(),11));bag.LockedSlots[0]=true;
            belt.ItemGrid.SetItem(0,new ItemStack(wood.Clone(),13));
            var selection=DepositPlan.Select(new[]{bag.ItemGrid,belt.ItemGrid},new HashSet<int>{wood.type});
            Check(selection.Sum(t=>t.Original.count)==24&&selection.Count==2,"native backpack locks and hotbar selection");
            Check(DepositPlan.ValidRefund(selection.Select(t=>t.Original).ToList(),new[]{new ItemStack(wood.Clone(),15)}),"valid partial refund with real ItemValues");
            Check(!DepositPlan.ValidRefund(selection.Select(t=>t.Original).ToList(),new[]{new ItemStack(wood.Clone(),25)}),"excess refund rejected");
            int depositId=NetPackageManager.GetPackageId(typeof(NetPackageGuardarNosBaus));
            Check(depositId>0&&depositId!=id,"deposit protocol registered separately");
            using(var stream=new MemoryStream())
            {
                var writer=new PooledBinaryWriter();writer.SetBaseStream(stream);
                var packet=NetPackageManager.GetPackage<NetPackageGuardarNosBaus>().Setup(NetPackageGuardarNosBaus.Deposit,17,Guid.NewGuid().ToString("N"),new List<ItemStack>{new ItemStack(wood.Clone(),12)});
                packet.write(writer);writer.Flush();stream.Position=0;
                var reader=new PooledBinaryReader();reader.SetBaseStream(stream);
                Check(reader.ReadUInt16()==depositId,"native deposit protocol id");
                var copy=new NetPackageGuardarNosBaus();copy.read(reader);
                Check(copy.Kind==NetPackageGuardarNosBaus.Deposit&&copy.Id==packet.Id&&copy.Stacks.Single().count==12,"native deposit packet roundtrip");
            }
            XmlFile windows=null;
            ThreadManager.RunCoroutineSync(XmlPatcher.LoadAndPatchConfig("XUi_InGame/windows",file=>windows=file));
            Check(windows!=null&&windows.XmlDoc.XPathSelectElements("/windows/window[@name='windowBackpack']/rect[@name='header']/button[@name='btnGuardarNosBaus']").Count()==1,"real XML patcher inserts exactly one backpack button");
            using(var stream=new MemoryStream())
            {
                var writer=new PooledBinaryWriter();writer.SetBaseStream(stream);
                var packet=NetPackageManager.GetPackage<NetPackageCraftingDosBaus>().Setup(NetPackageCraftingDosBaus.Grant,17,"native-roundtrip",new List<ItemStack>{new ItemStack(wood.Clone(),123)});
                packet.write(writer);writer.Flush();stream.Position=0;
                var reader=new PooledBinaryReader();reader.SetBaseStream(stream);
                Check(reader.ReadUInt16()==id,"native protocol package id");
                var copy=new NetPackageCraftingDosBaus();copy.read(reader);
                Check(copy.Kind==NetPackageCraftingDosBaus.Grant&&copy.Player==17&&copy.Id=="native-roundtrip","native packet fields");
                Check(copy.Stacks.Count==1&&copy.Stacks[0].count==123&&copy.Stacks[0].itemValue.type==wood.type,"real ItemStack binary roundtrip");
            }
            Log.Out("[BAUS-PROBE] PASS checks="+checks);
        }
        catch(Exception e) {Log.Error("[BAUS-PROBE] FAIL "+e);}
        finally {Client.Reset();SdtdConsole.Instance.ExecuteSync("shutdown",null);}
    }
    // Only the GUI/backend fixture is substituted; HasItems, DecItem and RemoveItems
    // are the real game methods with production Harmony patches installed.
    sealed class ProbeStation : XUiC_WorkstationInputGrid
    {
        ItemStack[] slots;
        public int Updates;
        public override ItemStack[] GetSlots()=>slots;
        public override void SetSlots(ItemStack[] value){slots=value;}
        public override void UpdateBackend(ItemStack[] value){slots=value;Updates++;}
    }
}

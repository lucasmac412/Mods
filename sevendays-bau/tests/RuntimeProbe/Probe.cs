using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
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
            Client.Wallet=new List<ItemStack>{new ItemStack(wood.Clone(),6)};Client.Completing=true;
            Check(station.HasItems(new[]{new ItemStack(wood.Clone(),5)},2),"native workstation sees authorized ingredients");
            var stationRemoved=new List<ItemStack>();
            station.RemoveItems(new[]{new ItemStack(wood.Clone(),5)},2,stationRemoved);
            Check(station.GetSlots()[0].IsEmpty()&&Client.Wallet[0].count==0,"native workstation consumes local and remote exactly");
            Check(stationRemoved.Sum(s=>s.count)==10&&station.Updates>0,"native workstation preserves refund and updates backend");
            Client.Reset();
            var iron=ItemClass.GetItem("resourceScrapIron");
            var unit=ItemClass.GetItem("unit_iron");
            Check(iron.type>0&&unit.type>0,"native forge item ids");
            var ore=new List<ItemStack>{new ItemStack(iron.Clone(),100)};
            int weight=iron.ItemClass.GetWeight();
            var forgeNeeds=new List<ItemStack>{new ItemStack(unit.Clone(),weight*7)};
            var empty=ItemStack.CreateArray(9);
            var feed=ForgeSupply.PlanSlots(empty,forgeNeeds,ore);
            Check(feed.Count==1&&feed[0].count==7&&feed[0].itemValue.type==iron.type,"forge requests physical iron by real material weight");
            Check(ore[0].count==100&&empty.All(s=>s.IsEmpty()),"forge planner does not mutate stock/input");
            empty[0]=new ItemStack(iron.Clone(),7);
            Check(ForgeSupply.PlanSlots(empty,forgeNeeds,ore).Count==0,"already loaded ore not requested twice");
            for(int i=0;i<3;i++)empty[i]=new ItemStack(wood.Clone(),10);
            Check(ForgeSupply.PlanSlots(empty,forgeNeeds,ore).Count==0,"full smelting slots prevent withdrawal");
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

using System;
using System.Collections.Generic;
using System.IO;

namespace CraftingDosBaus
{
    // A new registered type also prevents old peers lacking deposit support from joining.
    public sealed class NetPackageGuardarNosBaus : NetPackage
    {
        internal const byte Query=0, Offer=1, Deposit=2, Result=3, Ack=4;
        internal byte Kind;
        internal int Player;
        internal string Id="",Message="";
        internal List<ItemStack> Stacks=new List<ItemStack>();
        public override bool FlushQueue=>true;
        public NetPackageGuardarNosBaus Setup(byte kind,int player,string id,List<ItemStack> items=null,string message="")
        {Kind=kind;Player=player;Id=id;Stacks=items??new List<ItemStack>();Message=message;return this;}
        public override void write(PooledBinaryWriter bw)
        {
            base.write(bw);BinaryWriter w=bw;
            w.Write(Kind);w.Write(Player);w.Write(Id);w.Write(Message);w.Write((ushort)Stacks.Count);
            foreach(var item in Stacks)item.Write(bw);
        }
        public override void read(PooledBinaryReader br)
        {
            Kind=br.ReadByte();Player=br.ReadInt32();Id=br.ReadString();Message=br.ReadString();
            int count=br.ReadUInt16();
            if(Kind>Ack||!Guid.TryParse(Id,out _)||Message.Length>300||count>2048||((Kind==Deposit||Kind==Result)&&count>128))
                throw new InvalidDataException("GuardarNosBaus: pacote inválido");
            Stacks=new List<ItemStack>(count);
            for(int i=0;i<count;i++)Stacks.Add(new ItemStack().Read(br));
        }
        public override void ProcessPackage(World world,GameManager callbacks)
        {
            if(world==null||!Plugin.Ready)return;
            if(SingletonMonoBehaviour<ConnectionManager>.Instance.IsServer)
            {
                if(Sender==null||Sender.entityId!=Player)return;
                if(Kind==Query||Kind==Deposit||Kind==Ack)DepositServer.Handle(this,Sender);
            }
            else if(Kind==Offer||Kind==Result)QuickStore.Receive(this);
        }
    }
}

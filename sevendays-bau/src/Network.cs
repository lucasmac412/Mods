using System;
using System.Collections.Generic;
using System.IO;

namespace CraftingDosBaus
{
    // Registered by the game's NetPackage type discovery on every peer.
    // Snapshot reads and withdrawals are directed to the server; grants only to owner.
    public sealed class NetPackageCraftingDosBaus : NetPackage
    {
        internal const byte SnapshotRequest=0, Withdraw=1, Snapshot=2, Grant=3, Denied=4, Ack=5;
        internal byte Kind;
        internal int Player;
        internal string Id="";
        internal List<ItemStack> Stacks=new List<ItemStack>();
        internal string Message="";
        public override bool FlushQueue=>true;
        public NetPackageCraftingDosBaus Setup(byte kind,int player,string id,List<ItemStack> stacks=null,string message="")
        { Kind=kind;Player=player;Id=id??"";Stacks=stacks??new List<ItemStack>();Message=message;return this; }
        public override void read(PooledBinaryReader br)
        {
            Kind=br.ReadByte(); Player=br.ReadInt32(); Id=br.ReadString(); Message=br.ReadString();
            if(Kind>Ack || Id.Length>64 || Message.Length>300) throw new InvalidDataException("CraftingDosBaus: pacote inválido");
            int count=br.ReadUInt16();
            if(count>4096 || ((Kind==Withdraw)&&count>128)) throw new InvalidDataException("CraftingDosBaus: lista muito grande");
            Stacks=new List<ItemStack>(count);
            for(int i=0;i<count;i++) Stacks.Add(new ItemStack().Read(br));
        }
        public override void write(PooledBinaryWriter bw)
        { base.write(bw);BinaryWriter writer=bw;writer.Write(Kind);writer.Write(Player);writer.Write(Id);writer.Write(Message);writer.Write((ushort)Stacks.Count);foreach(var stack in Stacks)stack.Write(bw); }
        public override void ProcessPackage(World world,GameManager callbacks)
        {
            if(world==null || !Plugin.Ready) return;
            if(SingletonMonoBehaviour<ConnectionManager>.Instance.IsServer)
            {
                if(Sender==null || Sender.entityId!=Player) return;
                if(Kind==SnapshotRequest || Kind==Withdraw || Kind==Ack) Server.Handle(this,Sender);
            }
            else if(Kind==Snapshot || Kind==Grant || Kind==Denied) Client.Receive(this);
        }
    }
}

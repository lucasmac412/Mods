using System;
using System.Collections.Generic;
using System.IO;
namespace UnityEngine {
 public struct Vector3 { public float x,y,z; public Vector3(float a,float b,float c){x=a;y=b;z=c;} public float sqrMagnitude=>x*x+y*y+z*z;public static Vector3 operator -(Vector3 a,Vector3 b)=>new Vector3(a.x-b.x,a.y-b.y,a.z-b.z); }
 public static class Time {public static float realtimeSinceStartup;}
}
namespace Platform {
 public class PlatformUserIdentifierAbs {public string CombinedString;public PlatformUserIdentifierAbs(string id){CombinedString=id;}}
 public static class PlatformManager{public static PlatformUserIdentifierAbs InternalLocalUserIdentifier=new PlatformUserIdentifierAbs("host");}
}
public struct Vector3i {public int x,y,z;public static explicit operator UnityEngine.Vector3(Vector3i v)=>new UnityEngine.Vector3(v.x,v.y,v.z);}
public class EntityPlayer {public UnityEngine.Vector3 position; public bool Dead;public bool IsDead()=>Dead;}
public class DictBox<K,V> where K:notnull { public Dictionary<K,V> dict=new Dictionary<K,V>();}
public class ChunkCache {public DictBox<int,Chunk> chunks=new DictBox<int,Chunk>();}
public class Chunk {public DictBox<int,object> tileEntities=new DictBox<int,object>();public int ReadLocks;public void EnterReadLock(){ReadLocks++;}public void ExitReadLock(){ReadLocks--;}}
public class World {public ChunkCache ChunkCache=new ChunkCache();public Dictionary<int,EntityPlayer> Players=new Dictionary<int,EntityPlayer>();public object GetEntity(int id)=>Players.TryGetValue(id,out var p)?p:null;}
public class GameManager {public static GameManager Instance=new GameManager();public World World=new World();}
public class TileEntityComposite {
 public Vector3i Position;public TEFeatureStorage Storage;public TEFeatureLockable Lock;public bool Open,Busy;
 public Vector3i ToWorldPos()=>Position;public bool IsUserAccessing()=>Open;
 public T GetFeature<T>() where T:class=>typeof(T)==typeof(TEFeatureStorage)?Storage as T:Lock as T;
}
public class TEFeatureStorage {
 public ItemStackGrid ItemGrid=new ItemStackGrid();public int Modified;public bool FailAlways,FailOnce;
 public void SetModified(){Modified++;if(FailAlways||FailOnce){FailOnce=false;throw new IOException("injected persistence notification failure");}}
}
public class ItemStackGrid {public bool PlayerOwned=true;public ItemStack[] items=Array.Empty<ItemStack>();public HashSet<int> Locked=new HashSet<int>();public bool IsSlotLocked(int i)=>Locked.Contains(i);public void SetItems(ItemStack[] value,bool fire=true){items=ItemStack.Clone(value);}}
public class TEFeatureLockable {public bool Locked;public string Owner;public bool IsLocked()=>Locked;public bool IsUserAllowed(Platform.PlatformUserIdentifierAbs p)=>p.CombinedString==Owner;}
public class LockManager {public static LockManager Instance=new LockManager();public bool IsLockedServer(TileEntityComposite t)=>t.Busy;}
public class ItemClass {public int MaxCount=100;}
public class ItemValue {public ItemClass ItemClass=new ItemClass();public int Meta;public bool Equals(ItemValue other)=>other!=null&&type==other.type&&Meta==other.Meta;public int type;public bool HasModSlots,Modded;public bool HasMods()=>Modded;public ItemValue Clone()=>(ItemValue)MemberwiseClone();}
public class ItemStack {
 public ItemValue itemValue=new ItemValue();public int count;
 public bool CanStackWith(ItemStack other,bool partial=false)=>itemValue.type==other.itemValue.type&&count<itemValue.ItemClass.MaxCount;
 public bool IsEmpty()=>count<=0||itemValue.type==0;public void Clear(){count=0;itemValue=new ItemValue();}
 public ItemStack Clone()=>new ItemStack{itemValue=itemValue.Clone(),count=count};
 public static ItemStack[] Clone(IList<ItemStack> items){var a=new ItemStack[items.Count];for(int i=0;i<a.Length;i++)a[i]=items[i].Clone();return a;}
 public ItemStack Read(PooledBinaryReader r){itemValue.type=r.ReadInt32();count=r.ReadInt32();return this;}
 public void Write(PooledBinaryWriter w){w.Write(itemValue.type);w.Write(count);}
}
public abstract class NetPackage {
 public ClientInfo Sender; public virtual bool FlushQueue=>false;
 public abstract void read(PooledBinaryReader r);public virtual void write(PooledBinaryWriter w){}
 public abstract void ProcessPackage(World w,GameManager g);
}
public class PooledBinaryReader:BinaryReader{public PooledBinaryReader(Stream s):base(s){}}
public class PooledBinaryWriter:BinaryWriter{public PooledBinaryWriter(Stream s):base(s){}}
public class ClientInfo {
 public int entityId;public Platform.PlatformUserIdentifierAbs InternalId;
 public List<CraftingDosBaus.NetPackageCraftingDosBaus> Replies=new List<CraftingDosBaus.NetPackageCraftingDosBaus>();
 public List<CraftingDosBaus.NetPackageGuardarNosBaus> Deposits=new List<CraftingDosBaus.NetPackageGuardarNosBaus>();
 public void SendPackage(NetPackage p){if(p is CraftingDosBaus.NetPackageCraftingDosBaus c)Replies.Add(c);else Deposits.Add((CraftingDosBaus.NetPackageGuardarNosBaus)p);}
}
public class ConnectionManager{public bool IsServer=true; public void SendToServer(NetPackage p,bool flush){}}
public class SingletonMonoBehaviour<T> where T:new(){public static T Instance=new T();}
public static class NetPackageManager {public static T GetPackage<T>() where T:NetPackage,new()=>new T();}
public static class Log {public static void Error(string s){} }
namespace CraftingDosBaus {
 internal static class Plugin{internal static bool Ready=true;internal static Settings Config=new Settings();internal sealed class Settings{public int AlcanceMetros=50;}}
 internal static class QuickStore{internal static readonly List<NetPackageGuardarNosBaus> Replies=new List<NetPackageGuardarNosBaus>();internal static void Receive(NetPackageGuardarNosBaus p)=>Replies.Add(p);}
 internal static class Client{internal static readonly List<NetPackageCraftingDosBaus> Replies=new List<NetPackageCraftingDosBaus>();internal static void Receive(NetPackageCraftingDosBaus p)=>Replies.Add(p);}
}

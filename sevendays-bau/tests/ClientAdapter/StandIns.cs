using System;
using System.Collections.Generic;
using System.Linq;
using CraftingDosBaus;
namespace UnityEngine {
 public struct Vector3 {public static Vector3 up=>new Vector3();public static Vector3 zero=>new Vector3();public static Vector3 operator +(Vector3 a,Vector3 b)=>a;}
 public static class Time {public static float realtimeSinceStartup;}
}
public class ItemClass {public int MaxCount=100;}
public class ItemValue {public int type,Meta;public ItemClass ItemClass=new ItemClass();public ItemValue Clone()=>(ItemValue)MemberwiseClone();public bool Equals(ItemValue other)=>other!=null&&type==other.type&&Meta==other.Meta;}
public class ItemStack {
 public ItemValue itemValue=new ItemValue();public int count;
 public static ItemStack Empty=>new ItemStack();
 public bool IsEmpty()=>count<=0||itemValue.type==0;
 public bool CanStackWith(ItemStack other,bool partial=false)=>itemValue.type==other.itemValue.type&&count<itemValue.ItemClass.MaxCount;
 public ItemStack Clone()=>new ItemStack{itemValue=itemValue.Clone(),count=count};
 public static ItemStack[] Clone(IList<ItemStack> items)=>items.Select(s=>s.Clone()).ToArray();
}
public class ItemStackGrid {
 public ItemStack[] items=Enumerable.Range(0,4).Select(_=>ItemStack.Empty).ToArray();public HashSet<int> Locked=new HashSet<int>();
 public bool IsSlotLocked(int i)=>Locked.Contains(i);public void SetItem(int i,ItemStack value){items[i]=value.Clone();}
}
public class Bag {public ItemStackGrid ItemGrid=new ItemStackGrid();}
public class Inventory {public ItemStackGrid ItemGrid=new ItemStackGrid();}
public class EntityPlayerLocal {public int entityId=1;public bool Dead;public UnityEngine.Vector3 position;public Bag bag=new Bag();public Inventory inventory=new Inventory();public bool IsDead()=>Dead;}
public class XUiController {}
public class XUi {public FakeInventory PlayerInventory=new FakeInventory();}
public class LocalPlayerUI {public XUi xui=new XUi();public static LocalPlayerUI Current=new LocalPlayerUI();public static LocalPlayerUI GetUIForPlayer(EntityPlayerLocal p)=>Current;}
public class FakeInventory {
 public void AddItem(ItemStack item) {
  foreach(var grid in new[]{Client.Player.bag.ItemGrid,Client.Player.inventory.ItemGrid})
  for(int i=0;i<grid.items.Length&&item.count>0;i++) {
   var s=grid.items[i];if(!s.IsEmpty()&&!s.itemValue.Equals(item.itemValue))continue;
   int n=Math.Min(item.count,item.itemValue.ItemClass.MaxCount-(s.IsEmpty()?0:s.count));if(n<=0)continue;
   var copy=item.Clone();copy.count=n+(s.IsEmpty()?0:s.count);grid.SetItem(i,copy);item.count-=n;
  }
 }
}
public class GameManager {public static GameManager Instance=new GameManager();public List<ItemStack> Drops=new List<ItemStack>();public void ItemDropServer(ItemStack s,UnityEngine.Vector3 p,UnityEngine.Vector3 v,int id,float life){Drops.Add(s.Clone());}}
public class ConnectionManager {public bool IsServer;public List<NetPackageGuardarNosBaus> Sent=new List<NetPackageGuardarNosBaus>();public void SendToServer(NetPackageGuardarNosBaus p,bool reliable){Sent.Add(p);}}
public class SingletonMonoBehaviour<T> where T:new(){public static T Instance=new T();}
public static class NetPackageManager {public static T GetPackage<T>() where T:new()=>new T();}
public static class Log {public static List<string> Errors=new List<string>();public static void Error(string s){Errors.Add(s);}}
namespace CraftingDosBaus {
 internal static class Plugin {internal static bool Ready=true;}
 internal static class Client {internal static EntityPlayerLocal Player=new EntityPlayerLocal();internal static bool Busy;internal static List<string> Messages=new List<string>();internal static void Say(string s){Messages.Add(s);}internal static void Invalidate(){}}
 internal static class DepositServer {internal static void Handle(NetPackageGuardarNosBaus p,object sender){SingletonMonoBehaviour<ConnectionManager>.Instance.Sent.Add(p);}}
 public class NetPackageGuardarNosBaus {
  internal const byte Query=0,Offer=1,Deposit=2,Result=3,Ack=4;
  internal byte Kind;internal int Player;internal string Id,Message;internal List<ItemStack> Stacks=new List<ItemStack>();
  public NetPackageGuardarNosBaus Setup(byte kind,int player,string id,List<ItemStack> stacks=null,string message=""){Kind=kind;Player=player;Id=id;Stacks=stacks??new List<ItemStack>();Message=message;return this;}
 }
}

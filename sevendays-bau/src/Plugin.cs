using System;
using System.IO;
using System.Reflection;
using HarmonyLib;
using Newtonsoft.Json;

namespace CraftingDosBaus
{
    public sealed class Plugin : IModApi
    {
        internal static Settings Config = new Settings();
        internal static bool Ready;
        internal static string ModPath;
        public void InitMod(Mod mod)
        {
            ModPath=mod.Path;
            try
            {
                string path=Path.Combine(mod.Path,"config.json");
                if(File.Exists(path)) Config=JsonConvert.DeserializeObject<Settings>(File.ReadAllText(path))??new Settings();
                Config.AlcanceMetros=Math.Max(1,Math.Min(100,Config.AlcanceMetros));
                if(!Config.Ativado) return;
                // Read runtime fields rather than relying on compiler-inlined constants.
                int major=(int)typeof(Constants).GetField("cVersionMajor").GetRawConstantValue();
                int minor=(int)typeof(Constants).GetField("cVersionMinor").GetRawConstantValue();
                if(major!=3 || minor!=40) throw new NotSupportedException("Esta versão foi feita para 7 Days to Die 3.4.x.");
                Hooks.Install(new Harmony("lucasmac412.7days.craftingdosbaus"));
                ModEvents.GameUpdate.RegisterHandler(Update);
                ModEvents.WorldShuttingDown.RegisterHandler(Shutdown);
                Ready=true;
                Log.Out("[CraftingDosBaus] 0.3.0: 3.4, alcance "+Config.AlcanceMetros+" m, retirada autorizada pelo servidor.");
            }
            catch(Exception e) { Log.Error("[CraftingDosBaus] Não foi possível ativar: "+e); }
        }
        private static void Update(ref ModEvents.SGameUpdateData data)
        { if(Ready) {Client.Tick(); QuickStore.Tick();} }
        private static void Shutdown(ref ModEvents.SWorldShuttingDownData data)
        { Client.Reset(); QuickStore.Reset(); Server.Reset(); DepositServer.Reset(); }
        public sealed class Settings
        { public bool Ativado=true; public int AlcanceMetros=50; }
    }
}

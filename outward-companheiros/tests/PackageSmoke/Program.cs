using System;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Runtime.Serialization;
using System.Security.Cryptography;

// No compile-time reference to the plugin, Harmony or game. Loads the exact
// delivered plugin + Harmony/MonoMod from the public ZIP, with pinned DE metadata.
// DE references contain throw-null bodies: this is a binary integration test,
// NOT an execution of the game's reward logic, Unity, Photon or Doorstop startup.
internal static class Program
{
    private const string ZipHash = "655a505ca2ba0e2120d71b7872b86bc42ff12b1a1b668538b9ddc2ac845f8f3b";
    private const string PluginHash = "ffe6d706ab1b7674f881e9d327d23c0a7a9dea92d6692e56f38025693757d5ae";
    private static int checks, observed = -1, probeCalls;
    private static bool skipOriginal;
    private static FieldInfo receiver;
    private static void Check(bool value, string label)
    { checks++; if (!value) throw new Exception(label); }
    private static string Sha(string path)
    { using (var h = SHA256.Create()) using (var f = File.OpenRead(path)) return BitConverter.ToString(h.ComputeHash(f)).Replace("-", "").ToLowerInvariant(); }
    public static bool ObserveSelector(object __instance)
    {
        observed = Convert.ToInt32(receiver.GetValue(__instance));
        probeCalls++;
        return !skipOriginal;
    }
    private static void Main(string[] args)
    {
        if (args.Length != 3 && args.Length != 4) throw new ArgumentException("<released ZIP> <DE Assembly-CSharp.dll> <Unity refs directory> [reject]");
        bool reject = args.Length == 4 && args[3] == "reject";
        Check(Sha(args[0]) == ZipHash, "Testing the actual published complete ZIP");
        string temp = Path.Combine(Path.GetTempPath(), "companheiros-smoke-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(temp);
        ZipFile.ExtractToDirectory(args[0], temp);
        string root = Path.Combine(temp, "transfere pro jogo");
        string core = Path.Combine(root, "BepInEx", "core");
        string pluginPath = Path.Combine(root, "BepInEx", "plugins", "CompanheirosAurai", "Outward.Companheiros.dll");
        string gamePath = Path.GetFullPath(args[1]);
        var paths = new[] { core, Path.GetDirectoryName(gamePath), Path.GetFullPath(args[2]) };
        AppDomain.CurrentDomain.AssemblyResolve += (_, e) => {
            string name = new AssemblyName(e.Name).Name + ".dll";
            if (name == "Assembly-CSharp.dll") return Assembly.LoadFrom(gamePath);
            foreach (string dir in paths) {
                string candidate = Path.Combine(dir, name);
                if (File.Exists(candidate)) return Assembly.LoadFrom(candidate);
            }
            return null;
        };
        Check(Sha(pluginPath) == PluginHash, "Exact released DLL hash");
        var harmonyAssembly = Assembly.LoadFrom(Path.Combine(core, "0Harmony.dll"));
        var harmonyType = harmonyAssembly.GetType("HarmonyLib.Harmony", true);
        var harmonyMethodType = harmonyAssembly.GetType("HarmonyLib.HarmonyMethod", true);
        var plugin = Assembly.LoadFrom(pluginPath);
        Check(plugin.GetName().Version == new Version(0,3,0,0), "Released assembly version");
        var sharing = plugin.GetType("Companheiros.RewardSharing", true);
        var install = sharing.GetMethod("Install", BindingFlags.Static | BindingFlags.NonPublic);
        var disable = sharing.GetMethod("Disable", BindingFlags.Static | BindingFlags.NonPublic);
        var game = Assembly.LoadFrom(gamePath);
        var rewardType = game.GetType("NodeCanvas.Tasks.Actions.GiveReward", true);
        receiver = rewardType.GetField("RewardReceiver", BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic);
        var target = rewardType.GetMethod("OnExecute", BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.DeclaredOnly);
        var harmony = Activator.CreateInstance(harmonyType, "companheiros.shipped.binary");
        bool enabled = true, getterThrows = false;
        var configError = new InvalidOperationException("simulated config failure");
        Func<bool> config = () => { if (getterThrows) throw configError; return enabled; };
        if (reject)
        {
            bool rejected = false;
            try { install.Invoke(null, new object[] { harmony, config }); }
            catch (TargetInvocationException e) { rejected = e.InnerException is MissingMemberException; }
            Check(rejected, "Incompatible reference layout rejected before installing patch");
            Console.WriteLine(checks + " shipped DLL incompatible-contract checks passed: " + gamePath);
            return;
        }
        // Assert these really are stripped refs, not silently mistaken for a game install.
        var body = target.GetMethodBody().GetILAsByteArray();
        Check(body.Length == 2 && body[0] == 0x14 && body[1] == 0x7a, "Reference body is ldnull/throw; no claim of actual reward execution");
        install.Invoke(null, new object[] { harmony, config });
        var observer = Activator.CreateInstance(harmonyType, "companheiros.binary.observer");
        object probe = Activator.CreateInstance(harmonyMethodType, typeof(Program).GetMethod(nameof(ObserveSelector)));
        harmonyMethodType.GetField("priority").SetValue(probe, 0); // after production prefix (400)
        var patch = harmonyType.GetMethods().Single(m=>m.Name=="Patch"&&m.GetParameters().Length==6);
        patch.Invoke(observer, new object[] { target, probe, null, null, null, null });
        object reward = FormatterServices.GetUninitializedObject(rewardType); // reference constructors throw too
        foreach (bool active in new[] { false, true })
        foreach (bool skip in new[] { false, true })
        foreach (int original in new[] { 0, 1, 2, 99 })
        {
            enabled = active; skipOriginal = skip; observed = -1; probeCalls = 0;
            receiver.SetValue(reward, Enum.ToObject(receiver.FieldType, original));
            Exception failure = null;
            try { target.Invoke(reward, null); } catch (TargetInvocationException e) { failure = e.InnerException; }
            Check(probeCalls == 1, "Single real detoured call through packaged plugin and packaged Harmony");
            Check(observed == (active && (original==0||original==1) ? 2 : original), "Released prefix selects correct recipient");
            Check(Convert.ToInt32(receiver.GetValue(reward))==original, "Released finalizer restores selector");
            Check(skip ? failure==null : failure is NullReferenceException, "Reference exception/cancelled original preserved");
        }
        enabled = true; getterThrows = true; skipOriginal = false;
        receiver.SetValue(reward, Enum.ToObject(receiver.FieldType, 0));
        Exception configFailure = null;
        try { target.Invoke(reward, null); } catch (TargetInvocationException e) { configFailure = e.InnerException; }
        Check(ReferenceEquals(configFailure, configError), "Configuration exception not swallowed");
        Check(Convert.ToInt32(receiver.GetValue(reward))==0, "Failure in prefix leaves original selection");
        getterThrows = false;
        disable.Invoke(null, null); skipOriginal = true; observed = -1;
        target.Invoke(reward, null);
        Check(observed == 0, "Disable on released DLL preserves host-only selection");
        harmonyType.GetMethod("UnpatchSelf").Invoke(harmony, null);
        install.Invoke(null, new object[] { harmony, config }); observed = -1;
        target.Invoke(reward, null);
        Check(observed==2 && Convert.ToInt32(receiver.GetValue(reward))==0, "Released DLL reload works with packaged runtime");
        harmonyType.GetMethod("UnpatchSelf").Invoke(harmony, null);
        harmonyType.GetMethod("UnpatchSelf").Invoke(observer, null);
        Check(Assembly.Load("0Harmony").Location==harmonyAssembly.Location, "Uses bundled Harmony, not NuGet test runtime");
        Console.WriteLine(checks + " exact published ZIP/DLL + bundled Harmony + DE reference checks passed.");
        Console.WriteLine("Harmony=" + harmonyAssembly.FullName + "; PluginSHA256=" + Sha(pluginPath));
        Console.WriteLine("NO live Outward, Unity/Photon, native reward delivery, or BepInEx/Doorstop startup tested.");
    }
}

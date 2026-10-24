using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Runtime.CompilerServices;
using HarmonyLib;
using Companheiros;
using NodeCanvas.Tasks.Actions;

// Controlled stand-in, NOT Outward or Photon. On .NET Framework the actual
// HarmonyX library detours this method, exercising the production patch wiring.
namespace NodeCanvas.Tasks.Actions
{
    public sealed class GiveReward
    {
        public enum Receiver { Host, Instigator, Everyone }
        public Receiver RewardReceiver;
        public int Calls, Instigator;
        public List<Wallet> Players;
        public Action During;
        public Exception Error;
        public void Execute() { OnExecute(); }
        [MethodImpl(MethodImplOptions.NoInlining)]
        private void OnExecute()
        {
            Calls++;
            During?.Invoke();
            if (Error != null) throw Error;
            for (int i = 0; i < Players.Count; i++)
                if (RewardReceiver == Receiver.Everyone || (RewardReceiver == Receiver.Host && i == 0)
                    || (RewardReceiver == Receiver.Instigator && i == Instigator))
                {
                    Players[i].Silver += 120;
                    Players[i].Items += 2;
                    Players[i].Skills.Add(42);
                }
        }
    }
    public sealed class Wallet
    {
        public int Silver, Items;
        public HashSet<int> Skills = new HashSet<int>();
    }
}

internal static class Program
{
    private static int checks;
    private static bool enabled = true;
    private static void Check(bool value, string message)
    { checks++; if (!value) throw new Exception(message); }
    private static readonly MethodInfo before = typeof(RewardSharing).GetMethod("Before", BindingFlags.Static | BindingFlags.NonPublic);
    private static readonly MethodInfo finish = typeof(RewardSharing).GetMethod("Finish", BindingFlags.Static | BindingFlags.NonPublic);
    private static void Execute(GiveReward reward)
    {
#if NETFRAMEWORK
        reward.Execute(); // real Harmony prefix and finalizer, not manual dispatch
#else
        object[] args = { reward, null };
        before.Invoke(null, args);
        try { reward.Execute(); }
        finally { finish.Invoke(null, new[] { (object)reward, args[1] }); }
#endif
    }
    private static void Main()
    {
#if NETFRAMEWORK
        var harmony = new Harmony("companheiros.reward.tests");
        RewardSharing.Install(harmony, () => enabled);
#else
        typeof(RewardSharing).GetField("enabled", BindingFlags.Static | BindingFlags.NonPublic)
            .SetValue(null, new Func<bool>(() => enabled));
#endif
        foreach (bool active in new[] { false, true })
        foreach (int count in new[] { 1, 2, 4 })
        foreach (GiveReward.Receiver receiver in new[] { GiveReward.Receiver.Host,
            GiveReward.Receiver.Instigator, GiveReward.Receiver.Everyone, (GiveReward.Receiver)99 })
        for (int instigator = 0; instigator < count; instigator++)
        {
            enabled = active;
            var task = NewTask(count, receiver, instigator);
            for (int run = 1; run <= 3; run++)
            {
                Execute(task);
                Check(task.Calls == run, "One native call per reward, no duplicate replay");
                Check(task.RewardReceiver == receiver, "Reusable selector restored");
                for (int i = 0; i < count; i++)
                {
                    bool receives = receiver == GiveReward.Receiver.Everyone
                        || (receiver == GiveReward.Receiver.Host && (active || i == 0))
                        || (receiver == GiveReward.Receiver.Instigator && (active || i == instigator));
                    Check(task.Players[i].Silver == (receives ? 120 * run : 0), "Full currency once to each eligible player");
                    Check(task.Players[i].Items == (receives ? 2 * run : 0), "Full item quantity once to each eligible player");
                    Check(task.Players[i].Skills.Contains(42) == receives, "Native skill path selected");
                }
            }
        }
        enabled = true;
        var failed = NewTask(2, GiveReward.Receiver.Host, 0);
        var error = new InvalidOperationException("simulated native failure");
        failed.Error = error;
        bool propagated = false;
        try { Execute(failed); } catch (InvalidOperationException e) { propagated = ReferenceEquals(e, error); }
        Check(propagated, "Original exception preserved");
        Check(failed.Calls == 1 && failed.Players.All(p => p.Silver == 0), "Failure neither retried nor compensated");
        Check(failed.RewardReceiver == GiveReward.Receiver.Host, "Selector restored even on error");
        failed.Error = null; Execute(failed);
        Check(failed.Calls == 2 && failed.Players.All(p => p.Silver == 120), "Later legitimate reward works after error");

        var nested = NewTask(2, GiveReward.Receiver.Host, 0);
        bool nesting = false;
        nested.During = () => { if (!nesting) { nesting = true; Execute(nested); } };
        Execute(nested);
        Check(nested.RewardReceiver == GiveReward.Receiver.Host, "Nested same-task selectors unwind correctly");
        Check(nested.Calls == 2 && nested.Players.All(p => p.Silver == 240), "No extra delivery from nesting");

        var toggle = NewTask(2, GiveReward.Receiver.Host, 0);
        enabled = true; Execute(toggle);
        enabled = false; Execute(toggle);
        Check(toggle.Players[0].Silver == 240 && toggle.Players[1].Silver == 120, "Disable takes effect on next reward");
        enabled = true; Execute(toggle);
        Check(toggle.Players[0].Silver == 360 && toggle.Players[1].Silver == 240, "Re-enable without sticky state");
        RewardSharing.Disable(); Execute(toggle);
        Check(toggle.Players[0].Silver == 480 && toggle.Players[1].Silver == 240, "Unload disables sharing");
#if NETFRAMEWORK
        harmony.UnpatchSelf();
        Console.WriteLine(checks + " production reward checks with REAL HarmonyX patch/finalizer on Windows .NET Framework passed.");
#else
        Console.WriteLine(checks + " production reward callback checks passed (controlled stand-in; no Harmony detour on this target).");
#endif
        Console.WriteLine("These tests do NOT run Outward, Steam, Unity, Photon or an actual coop session.");
    }
    private static GiveReward NewTask(int count, GiveReward.Receiver receiver, int instigator)
    {
        return new GiveReward { RewardReceiver = receiver, Instigator = instigator,
            Players = Enumerable.Range(0, count).Select(_ => new Wallet()).ToList() };
    }
}

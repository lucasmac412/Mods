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
        public Action<int> DuringDelivery;
        public int Silver = 120, Quantity = 2, Skill = 42;
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
                    DuringDelivery?.Invoke(i);
                    Players[i].Silver += Silver;
                    Players[i].Items += Quantity;
                    Players[i].Skills.Add(Skill);
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
        // A remote delivery failure after the host already received a reward must
        // NOT cause this patch to retry/replay it or suppress the game's exception.
        var partial = NewTask(2, GiveReward.Receiver.Host, 0);
        var remoteFailure = new InvalidOperationException("simulated guest delivery failure");
        partial.DuringDelivery = i => { if (i == 1) throw remoteFailure; };
        propagated = false;
        try { Execute(partial); } catch (InvalidOperationException e) { propagated = ReferenceEquals(e, remoteFailure); }
        Check(propagated && partial.RewardReceiver == GiveReward.Receiver.Host, "Partial failure propagates and restores selector");
        Check(partial.Calls == 1 && partial.Players[0].Silver == 120 && partial.Players[1].Silver == 0,
            "Partial delivery not silently retried or misrepresented as atomic");
        // The mod cannot repair native/network partial delivery. This is a limit,
        // not a successful Photon test: the host keeps its already delivered reward.

        var empty = NewTask(0, GiveReward.Receiver.Host, 0);
        Execute(empty);
        Check(empty.Calls == 1 && empty.RewardReceiver == GiveReward.Receiver.Host, "No recipients does not leak state or retry");
        var session = NewTask(2, GiveReward.Receiver.Host, 0);
        var guest = session.Players[1];
        guest.Skills.Add(42);
        Execute(session);
        Check(guest.Skills.Count == 1, "Existing skill remains handled by native executor");
        session.Players.RemoveAt(1); Execute(session);
        Check(guest.Silver == 120 && session.Players[0].Silver == 240, "Absent participant gets no manufactured reward");
        session.Players.Add(guest);
        Check(guest.Silver == 120, "Rejoining alone does not replay previous rewards");
        Execute(session);
        Check(guest.Silver == 240 && session.Players[0].Silver == 360, "Next reward uses current recipient set");
        foreach (int amount in new[] { 0, 1, 200, 1000000 })
        foreach (int quantity in new[] { 0, 1, 17 })
        {
            var amounts = NewTask(2, GiveReward.Receiver.Instigator, 1);
            amounts.Silver = amount; amounts.Quantity = quantity; amounts.Skill = 1234;
            Execute(amounts);
            Check(amounts.Players.All(p => p.Silver == amount && p.Items == quantity && p.Skills.Contains(1234)),
                "Reward contents unmodified; no hardcoded amount or split");
        }
#if NETFRAMEWORK
        AdversarialHarmonyChecks();
#endif
        RewardSharing.Disable(); Execute(toggle);
        Check(toggle.Players[0].Silver == 480 && toggle.Players[1].Silver == 240, "Unload disables sharing");
#if NETFRAMEWORK
        harmony.UnpatchSelf();
        RewardSharing.Install(harmony, () => enabled);
        var reload = NewTask(2, GiveReward.Receiver.Host, 0); Execute(reload);
        Check(reload.Calls == 1 && reload.Players.All(p=>p.Silver==120), "Reload installs a single delivery path");
        Check(Harmony.GetPatchInfo(AccessTools.DeclaredMethod(typeof(GiveReward), "OnExecute")).Prefixes.Count(p=>p.owner==harmony.Id)==1,
            "Exactly one sharing prefix after unload/reload");
        harmony.UnpatchSelf();
        Execute(reload);
        Check(reload.Players[0].Silver==240 && reload.Players[1].Silver==120, "Unpatch restores native host-only selection");
        Console.WriteLine(checks + " production reward checks with REAL HarmonyX patch/finalizer on Windows .NET Framework passed.");
#else
        Console.WriteLine(checks + " production reward callback checks passed (controlled stand-in; no Harmony detour on this target).");
#endif
        Console.WriteLine("These tests do NOT run Outward, Steam, Unity, Photon or an actual coop session.");
    }
#if NETFRAMEWORK
    private static readonly Exception foreignError = new InvalidOperationException("another patch failed");
    private static bool CancelOriginal() { return false; }
    private static void ThrowFromAnotherPatch() { throw foreignError; }
    private static void AdversarialHarmonyChecks()
    {
        var foreign = new Harmony("companheiros.foreign.patch.test");
        var target = AccessTools.DeclaredMethod(typeof(GiveReward), "OnExecute");
        try
        {
            foreign.Patch(target, prefix: new HarmonyMethod(typeof(Program), nameof(CancelOriginal)) { priority = Priority.First });
            var canceled = NewTask(2, GiveReward.Receiver.Host, 0); Execute(canceled);
            Check(canceled.Calls == 0 && canceled.Players.All(p=>p.Silver==0), "Cancelled reward stays cancelled");
            Check(canceled.RewardReceiver==GiveReward.Receiver.Host, "Cancelled action restores selector");
            foreign.UnpatchSelf();
            foreach (int priority in new[] { Priority.First, Priority.Last })
            {
                foreign.Patch(target, prefix: new HarmonyMethod(typeof(Program), nameof(ThrowFromAnotherPatch)) { priority = priority });
                var task = NewTask(2, GiveReward.Receiver.Instigator, 1);
                bool propagated = false;
                try { Execute(task); } catch (InvalidOperationException e) { propagated = ReferenceEquals(e, foreignError); }
                Check(propagated && task.Calls==0, "Foreign prefix exception preserved without reward");
                Check(task.RewardReceiver==GiveReward.Receiver.Instigator, "Exception before/after sharing prefix restores selector");
                foreign.UnpatchSelf();
            }
            foreign.Patch(target, postfix: new HarmonyMethod(typeof(Program), nameof(ThrowFromAnotherPatch)));
            var completed = NewTask(2, GiveReward.Receiver.Host, 0);
            bool postfixError = false;
            try { Execute(completed); } catch (InvalidOperationException e) { postfixError = ReferenceEquals(e, foreignError); }
            Check(postfixError && completed.RewardReceiver==GiveReward.Receiver.Host, "Foreign postfix exception preserves error and restores selector");
            Check(completed.Calls==1 && completed.Players.All(p=>p.Silver==120), "Postfix failure does not replay completed delivery");
        }
        finally { foreign.UnpatchSelf(); }
    }
#endif
    private static GiveReward NewTask(int count, GiveReward.Receiver receiver, int instigator)
    {
        return new GiveReward { RewardReceiver = receiver, Instigator = instigator,
            Players = Enumerable.Range(0, count).Select(_ => new Wallet()).ToList() };
    }
}

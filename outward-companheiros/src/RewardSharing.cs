using System;
using HarmonyLib;
using NodeCanvas.Tasks.Actions;

namespace Companheiros
{
    // Change the recipients of ONE native reward action, not inventory mutations or
    // inbound RPCs. GiveReward itself handles local/remote delivery and skill unlocks.
    internal static class RewardSharing
    {
        private static Func<bool> enabled;

        internal struct Snapshot
        {
            internal GiveReward.Receiver Original;
            internal bool Changed;
        }

        internal static void Install(Harmony harmony, Func<bool> isEnabled)
        {
            var target = AccessTools.DeclaredMethod(typeof(GiveReward), "OnExecute", Type.EmptyTypes);
            var receiver = AccessTools.Field(typeof(GiveReward), "RewardReceiver");
            if (target == null || target.ReturnType != typeof(void) || target.IsStatic
                || receiver == null || !receiver.IsPublic || receiver.FieldType != typeof(GiveReward.Receiver)
                || Convert.ToInt32(Enum.Parse(typeof(GiveReward.Receiver), "Host")) != 0
                || Convert.ToInt32(Enum.Parse(typeof(GiveReward.Receiver), "Instigator")) != 1
                || Convert.ToInt32(Enum.Parse(typeof(GiveReward.Receiver), "Everyone")) != 2)
                throw new MissingMemberException("GiveReward DE: incompatible reward contract; sharing not installed.");

            enabled = isEnabled ?? throw new ArgumentNullException(nameof(isEnabled));
            try
            {
                harmony.Patch(target,
                    prefix: new HarmonyMethod(typeof(RewardSharing), nameof(Before)),
                    finalizer: new HarmonyMethod(typeof(RewardSharing), nameof(Finish)));
            }
            catch { enabled = null; throw; }
        }

        internal static void Disable() { enabled = null; }

        private static void Before(GiveReward __instance, out Snapshot __state)
        {
            __state = default;
            if (__instance == null || enabled?.Invoke() != true) return;
            var original = __instance.RewardReceiver;
            // Already-shared and unknown future selectors pass through untouched.
            if (original != GiveReward.Receiver.Host && original != GiveReward.Receiver.Instigator) return;
            __state = new Snapshot { Original = original, Changed = true };
            __instance.RewardReceiver = GiveReward.Receiver.Everyone;
        }

        // Harmony finalizers also run on exceptions. Restore the reusable dialogue
        // task; never replay an action, swallow its exception or deliver a second copy.
        private static void Finish(GiveReward __instance, Snapshot __state)
        {
            if (__state.Changed && __instance != null) __instance.RewardReceiver = __state.Original;
        }
    }
}

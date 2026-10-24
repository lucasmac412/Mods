using System;
using System.Collections.Generic;
using System.Text;

namespace Companheiros
{
    // Pure rules shared by the plugin and executable tests. No game/save writes.
    public static class Policy
    {
        public const int Silver = 200;
        public const int Rations = 3;
        public static readonly int[] Cities = { 100, 200, 300, 400, 500, 601 };

        public static bool IsCity(int area) { return Array.IndexOf(Cities, area) >= 0; }

        public static int[] Destinations(int current, bool cierzoBlocked, bool bergBlocked,
            bool levantAllowed, bool siroccoUnlocked)
        {
            if (!IsCity(current)) return new int[0];
            var result = new List<int>();
            foreach (int id in Cities)
            {
                if (id == current || id == 100 && cierzoBlocked || id == 500 && bergBlocked
                    || id == 300 && !levantAllowed || id == 601 && !siroccoUnlocked) continue;
                result.Add(id);
            }
            return result.ToArray();
        }

        public static int ColorIndex(string uid)
        {
            // .NET string.GetHashCode is deliberately avoided: it varies between processes.
            uint value = 2166136261;
            foreach (char c in uid ?? "") value = unchecked((value ^ c) * 16777619);
            return (int)(value % 6);
        }

        public static string DisplayName(string name)
        {
            if (string.IsNullOrWhiteSpace(name)) return "Aventureiro";
            var result = new StringBuilder();
            foreach (char c in name)
            {
                if (result.Length >= 32) break;
                if (!char.IsControl(c) && !char.IsSurrogate(c)) result.Append(c);
            }
            return result.Length == 0 ? "Aventureiro" : result.ToString();
        }
    }
}

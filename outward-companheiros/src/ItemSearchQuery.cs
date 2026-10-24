using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace Companheiros
{
    // Per-view search. Never holds Item/ItemContainer references or changes game data.
    public sealed class ItemSearchQuery
    {
        public const int Limit = 64;
        private string[] terms = new string[0];
        private readonly Dictionary<string, string> names = new Dictionary<string, string>(StringComparer.Ordinal);
        public bool IsEmpty { get { return terms.Length == 0; } }

        public void Set(string value)
        {
            value = value ?? "";
            if (value.Length > Limit) value = value.Substring(0, Limit);
            terms = Fold(value).Split(new[] { ' ', '\t', '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
        }
        public bool Matches(bool nativeAllowed, string name)
        {
            if (!nativeAllowed) return false;
            if (IsEmpty) return true;
            if (string.IsNullOrEmpty(name)) return false;
            if (!names.TryGetValue(name, out var folded))
            {
                if (names.Count >= 512) names.Clear();
                folded = Fold(name); names[name] = folded;
            }
            foreach (string term in terms)
                if (folded.IndexOf(term, StringComparison.Ordinal) < 0) return false;
            return true;
        }
        private static string Fold(string value)
        {
            // Remove invalid isolated UTF-16 surrogates before Unicode normalization.
            var clean = new StringBuilder(value.Length);
            for (int i = 0; i < value.Length; i++)
            {
                char c = value[i];
                if (!char.IsSurrogate(c)) clean.Append(c);
                else if (char.IsHighSurrogate(c) && i+1 < value.Length && char.IsLowSurrogate(value[i+1]))
                { clean.Append(c); clean.Append(value[++i]); }
            }
            string decomposed = clean.ToString().Normalize(NormalizationForm.FormD);
            var result = new StringBuilder(decomposed.Length);
            foreach (char c in decomposed)
                if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                    result.Append(char.IsWhiteSpace(c) ? ' ' : char.ToLowerInvariant(c));
            return result.ToString();
        }
    }
}

using System;
using System.Reflection;
using HarmonyLib;
using UnityEngine;
using UnityEngine.UI;

namespace Companheiros
{
    internal static class SearchFocus
    {
        private static readonly FieldInfo Selected = AccessTools.Field(typeof(CharacterUI), "m_selectedInputField");
        private static readonly FieldInfo Focused = AccessTools.Field(typeof(CharacterUI), "m_isInputFieldFocused");
        private static readonly FieldInfo UnfocusedAt = AccessTools.Field(typeof(CharacterUI), "m_inputFieldUnfocusTime");
        private static readonly FieldInfo PendingActivation = AccessTools.Field(typeof(InputField), "m_ShouldActivateNextUpdate");

        internal static void Validate()
        {
            if (Selected?.FieldType != typeof(InputField) || Focused?.FieldType != typeof(bool)
                || UnfocusedAt?.FieldType != typeof(float) || PendingActivation?.FieldType != typeof(bool))
                throw new MissingFieldException("Outward search input-focus bindings");
        }

        internal static void Release(CharacterUI ui, InputField input)
        {
            if (!input) return;
            // DeactivateInputField alone can leave a queued activation or stale native
            // CharacterUI flags. Cancel only this search field's pending activation.
            PendingActivation.SetValue(input, false);
            input.DeactivateInputField();
            PendingActivation.SetValue(input, false);
            if (!ui) return;
            var nativeField = Selected.GetValue(ui) as InputField;
            if (nativeField == input) ClearNativeField(ui);
            var actualSelection = ui.EventSystemCurrentSelectedGo;
            bool selectedHere = actualSelection == input.gameObject
                || !actualSelection && ui.CurrentSelectedGameObject == input.gameObject;
            if (!nativeField && selectedHere) ClearNativeField(ui);
            // Never clear another text field or veto selection of an item, tab or close button.
            if (selectedHere) ui.SetSelectedGameObject(null);
        }
        private static void ClearNativeField(CharacterUI ui)
        {
            Selected.SetValue(ui, null);
            Focused.SetValue(ui, false);
            UnfocusedAt.SetValue(ui, Time.time);
        }
    }
}

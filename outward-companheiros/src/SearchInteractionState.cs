namespace Companheiros
{
    // Shared with executable regression tests. Visibility and text capture are independent:
    // ending an edit never hides the bar, and showing a panel never starts typing.
    public sealed class SearchInteractionState
    {
        public bool Visible { get; private set; }
        public bool Editing { get; private set; }
        public bool Show()
        {
            bool changed = !Visible; Visible = true; return changed;
        }
        public bool Hide()
        {
            bool changed = Visible; Visible = false; Editing = false; return changed;
        }
        public void BeginEdit() { Editing = Visible; }
        public void EndEdit() { Editing = false; }
        public bool ShouldRelease(bool nativeFocused, bool selected, bool exitKey, bool outsideClick)
        {
            return Visible && (Editing || nativeFocused)
                && (exitKey || outsideClick || !selected);
        }
    }
}

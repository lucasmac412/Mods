using System;
using System.Linq;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;
using Object = UnityEngine.Object;

namespace Companheiros
{
    // InputField remains a real Unity field so the game's Rewired-aware CharacterUI
    // can recognise text entry and suppress inventory/action shortcuts while typing.
    internal sealed class SearchInputField : InputField
    {
        internal CharacterUI OwnerUI;
        internal Action Selected, ExitRequested;
        public override void OnSelect(BaseEventData eventData)
        {
            base.OnSelect(eventData);
            if (OwnerUI) OwnerUI.SetSelectedGameObject(gameObject);
            Selected?.Invoke();
        }
        public override void OnUpdateSelected(BaseEventData eventData)
        {
            if (UnityEngine.Input.GetKeyDown(KeyCode.Escape) || UnityEngine.Input.GetKeyDown(KeyCode.Return)
                || UnityEngine.Input.GetKeyDown(KeyCode.KeypadEnter))
            { ExitRequested?.Invoke(); eventData.Use(); return; }
            base.OnUpdateSelected(eventData);
        }
    }

    internal sealed class SearchBar : IDisposable
    {
        internal readonly UIElement Owner;
        internal readonly ItemSearchQuery Query = new ItemSearchQuery();
        internal readonly Func<ContainerDisplay[]> Lists;
        internal SearchInputField Input;
        internal bool CountDirty;
        internal bool Active { get { return state.Visible && root && root.activeSelf && Owner && Owner.IsDisplayed; } }
        internal bool Exists { get { return root && viewport; } }
        internal bool ViewportVisible { get { return viewport && viewport.gameObject.activeInHierarchy; } }
        private readonly RectTransform viewport;
        private readonly GameObject root;
        private readonly Text count;
        private readonly SearchInteractionState state = new SearchInteractionState();
        private Vector2 previousMax;
        private bool suppress, pending, releasing;
        private float refreshAt, messageUntil;
        private const float Height = 50, Gap = 7;
        private static readonly Color Gold = new Color(.69f,.56f,.33f,1);
        private static readonly Color Parchment = new Color(.95f,.88f,.73f,1);

        internal SearchBar(UIElement owner, Func<ContainerDisplay[]> lists,
            RectTransform viewport, string placeholder)
        {
            Owner = owner; Lists = lists; this.viewport = viewport;
            var fontSample = owner.GetComponentsInChildren<Text>(true).FirstOrDefault(t => t.font);
            Font font = fontSample ? fontSample.font : Resources.GetBuiltinResource<Font>("Arial.ttf");
            var rect = new GameObject("Companheiros - Busca", typeof(RectTransform)).GetComponent<RectTransform>();
            rect.SetParent(viewport.parent, false); rect.localScale = Vector3.one;
            root = rect.gameObject; root.AddComponent<LayoutElement>().ignoreLayout = true;
            var image = root.AddComponent<Image>(); image.color = new Color(.075f,.066f,.052f,.98f);
            var border = root.AddComponent<Outline>(); border.effectColor = Gold; border.effectDistance = new Vector2(1,-1);
            Input = root.AddComponent<SearchInputField>(); Input.OwnerUI = owner.CharacterUI;
            Input.Selected = () => state.BeginEdit(); Input.ExitRequested = FinishEditing;
            Input.targetGraphic = image; Input.characterLimit = ItemSearchQuery.Limit;
            Input.lineType = InputField.LineType.SingleLine; Input.contentType = InputField.ContentType.Standard;
            Input.customCaretColor = true; Input.caretColor = Parchment; Input.selectionColor = new Color(.56f,.42f,.20f,.55f);
            Input.navigation = new Navigation { mode = Navigation.Mode.None };
            var text = Label("Texto", rect, font, 17, Parchment);
            text.rectTransform.offsetMin = new Vector2(14,4); text.rectTransform.offsetMax = new Vector2(-94,-18);
            text.alignment = TextAnchor.MiddleLeft; text.horizontalOverflow = HorizontalWrapMode.Overflow;
            Input.textComponent = text;
            var hint = Label("Exemplo", rect, font, 15, new Color(.66f,.61f,.50f,.95f));
            hint.rectTransform.offsetMin = text.rectTransform.offsetMin; hint.rectTransform.offsetMax = text.rectTransform.offsetMax;
            hint.alignment = TextAnchor.MiddleLeft; hint.fontStyle = FontStyle.Italic; hint.text = placeholder; Input.placeholder = hint;
            var title = Label("Título", rect, font, 10, Gold); title.text = "BUSCAR ITENS";
            title.rectTransform.offsetMin = new Vector2(14,Height-18); title.rectTransform.offsetMax = new Vector2(-180,-3);
            count = Label("Resultados", rect, font, 10, Gold); count.alignment = TextAnchor.UpperRight;
            count.rectTransform.offsetMin = new Vector2(110,Height-18); count.rectTransform.offsetMax = new Vector2(-94,-3);
            var clearRect = new GameObject("Limpar busca", typeof(RectTransform)).GetComponent<RectTransform>();
            clearRect.SetParent(rect,false); clearRect.anchorMin = clearRect.anchorMax = new Vector2(1,.5f);
            clearRect.sizeDelta = new Vector2(32,36); clearRect.anchoredPosition = new Vector2(-68,0);
            var clearImage = clearRect.gameObject.AddComponent<Image>(); clearImage.color = new Color(.17f,.14f,.095f,1);
            var clear = clearRect.gameObject.AddComponent<Button>(); clear.targetGraphic = clearImage;
            clear.navigation = new Navigation { mode = Navigation.Mode.None };
            var x = Label("Limpar",clearRect,font,20,Parchment); x.text = "×"; x.alignment = TextAnchor.MiddleCenter;
            var doneRect = new GameObject("Encerrar digitação", typeof(RectTransform)).GetComponent<RectTransform>();
            doneRect.SetParent(rect,false); doneRect.anchorMin = doneRect.anchorMax = new Vector2(1,.5f);
            doneRect.sizeDelta = new Vector2(40,36); doneRect.anchoredPosition = new Vector2(-24,0);
            var doneImage = doneRect.gameObject.AddComponent<Image>(); doneImage.color = new Color(.22f,.18f,.11f,1);
            var done = doneRect.gameObject.AddComponent<Button>(); done.targetGraphic = doneImage;
            done.navigation = new Navigation { mode = Navigation.Mode.None };
            var ok = Label("OK",doneRect,font,14,Parchment); ok.text = "OK"; ok.alignment = TextAnchor.MiddleCenter;
            done.onClick.AddListener(FinishEditing);
            Input.onValueChanged.AddListener(value => {
                if (suppress) return; Query.Set(value); pending = true; refreshAt = Time.unscaledTime + .07f; messageUntil = 0;
            });
            Input.onEndEdit.AddListener(value => {
                if (!suppress && !releasing) FinishEditing();
            });
            clear.onClick.AddListener(() => {
                Input.text = ""; Query.Set(""); Refresh();
                FinishEditing();
            });
            root.SetActive(false);
        }

        private static Text Label(string name, Transform parent, Font font, int size, Color color)
        {
            var rect = new GameObject(name, typeof(RectTransform)).GetComponent<RectTransform>();
            rect.SetParent(parent,false); rect.anchorMin = Vector2.zero; rect.anchorMax = Vector2.one;
            rect.offsetMin = rect.offsetMax = Vector2.zero;
            var text = rect.gameObject.AddComponent<Text>(); text.font = font; text.fontSize = size;
            text.color = color; text.supportRichText = false; text.raycastTarget = false; text.verticalOverflow = VerticalWrapMode.Truncate;
            return text;
        }
        internal void Show()
        {
            if (!viewport || !root) return;
            bool entering = state.Show();
            if (entering)
            {
                previousMax = viewport.offsetMax;
                var rect = (RectTransform)root.transform;
                rect.anchorMin = new Vector2(viewport.anchorMin.x,viewport.anchorMax.y); rect.anchorMax = viewport.anchorMax;
                rect.offsetMin = new Vector2(viewport.offsetMin.x,previousMax.y-Height);
                rect.offsetMax = previousMax;
                viewport.offsetMax = new Vector2(previousMax.x,previousMax.y-Height-Gap);
                pending = true; refreshAt = 0;
            }
            if (entering || !root.activeSelf)
            { root.SetActive(true); root.transform.SetAsLastSibling(); CountDirty = true; }
        }
        internal void Tick()
        {
            bool key = UnityEngine.Input.GetKeyDown(KeyCode.Escape) || UnityEngine.Input.GetKeyDown(KeyCode.Return)
                || UnityEngine.Input.GetKeyDown(KeyCode.KeypadEnter);
            bool click = UnityEngine.Input.GetMouseButtonDown(0) || UnityEngine.Input.GetMouseButtonDown(1);
            var canvas = root.GetComponentInParent<Canvas>();
            var camera = canvas && canvas.renderMode != RenderMode.ScreenSpaceOverlay ? canvas.worldCamera : null;
            bool outside = click && !RectTransformUtility.RectangleContainsScreenPoint(
                (RectTransform)root.transform, UnityEngine.Input.mousePosition, camera);
            bool selected = Owner.CharacterUI && (Owner.CharacterUI.CurrentSelectedGameObject == Input.gameObject
                || Owner.CharacterUI.EventSystemCurrentSelectedGo == Input.gameObject);
            if (state.ShouldRelease(Input.isFocused, selected, key, outside)) FinishEditing();
            if (pending && Time.unscaledTime >= refreshAt) Refresh();
            if (CountDirty && Time.unscaledTime >= messageUntil)
            {
                int visible = Lists().Where(l => l && l.gameObject.activeInHierarchy).Sum(l => l.ActiveItemCount);
                count.text = visible == 0 && !Query.IsEmpty ? "Nenhum resultado" : visible + " visíveis";
                CountDirty = false;
            }
        }
        private void FinishEditing()
        {
            if (releasing) return;
            releasing = true;
            try
            {
                state.EndEdit();
                SearchFocus.Release(Owner ? Owner.CharacterUI : null, Input);
                if (!suppress && pending) Refresh();
            }
            finally { releasing = false; }
        }
        private void Refresh()
        {
            pending = false;
            // Refresh from full, live container data. Never save a filtered inventory copy.
            foreach (var list in Lists()) if (list && list.StartDone && list.ReferencedContainer && list.gameObject.activeInHierarchy) list.Refresh();
            CountDirty = true;
        }
        internal void RefreshVisible() { Refresh(); }
        internal void ExplainTakeAll()
        {
            if (Time.unscaledTime >= messageUntil && Owner && Owner.CharacterUI)
                Owner.CharacterUI.ShowInfoNotification("Limpe a busca para usar Pegar tudo.");
            count.text = "Limpe a busca para pegar tudo"; messageUntil = Time.unscaledTime + 3; CountDirty = true;
        }
        internal void Hide()
        {
            bool restore = state.Hide();
            if (!root) { if (restore && viewport) viewport.offsetMax = previousMax; return; }
            if (!restore && !root.activeSelf) return;
            suppress = true;
            try
            {
                FinishEditing();
                if (Input) Input.text = "";
                Query.Set(""); pending = false; messageUntil = 0;
            }
            finally
            {
                root.SetActive(false); suppress = false;
                if (restore && viewport) viewport.offsetMax = previousMax;
            }
        }
        public void Dispose()
        {
            try { Hide(); } finally { if (root) Object.Destroy(root); }
        }
    }
}

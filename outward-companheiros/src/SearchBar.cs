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
        public override void OnSelect(BaseEventData eventData)
        {
            base.OnSelect(eventData);
            if (OwnerUI) OwnerUI.SetSelectedGameObject(gameObject);
        }
    }

    internal sealed class SearchBar : IDisposable
    {
        internal readonly UIElement Owner;
        internal readonly ItemSearchQuery Query = new ItemSearchQuery();
        internal readonly Func<ContainerDisplay[]> Lists;
        internal SearchInputField Input;
        internal bool CountDirty;
        internal bool CapturingText { get { return Input && (Input.isFocused || Time.unscaledTime < releaseGuardUntil); } }
        internal bool Active { get { return root && root.activeSelf && Owner && Owner.IsDisplayed; } }
        internal bool Exists { get { return root && viewport; } }
        private readonly RectTransform viewport;
        private readonly GameObject root;
        private readonly Text count;
        private Vector2 previousMax;
        private bool reserved, suppress, pending;
        private float refreshAt, messageUntil, releaseGuardUntil;
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
            Input.targetGraphic = image; Input.characterLimit = ItemSearchQuery.Limit;
            Input.lineType = InputField.LineType.SingleLine; Input.contentType = InputField.ContentType.Standard;
            Input.customCaretColor = true; Input.caretColor = Parchment; Input.selectionColor = new Color(.56f,.42f,.20f,.55f);
            Input.navigation = new Navigation { mode = Navigation.Mode.None };
            var text = Label("Texto", rect, font, 17, Parchment);
            text.rectTransform.offsetMin = new Vector2(14,4); text.rectTransform.offsetMax = new Vector2(-48,-18);
            text.alignment = TextAnchor.MiddleLeft; text.horizontalOverflow = HorizontalWrapMode.Overflow;
            Input.textComponent = text;
            var hint = Label("Exemplo", rect, font, 15, new Color(.66f,.61f,.50f,.95f));
            hint.rectTransform.offsetMin = text.rectTransform.offsetMin; hint.rectTransform.offsetMax = text.rectTransform.offsetMax;
            hint.alignment = TextAnchor.MiddleLeft; hint.fontStyle = FontStyle.Italic; hint.text = placeholder; Input.placeholder = hint;
            var title = Label("Título", rect, font, 10, Gold); title.text = "BUSCAR ITENS";
            title.rectTransform.offsetMin = new Vector2(14,Height-18); title.rectTransform.offsetMax = new Vector2(-100,-3);
            count = Label("Resultados", rect, font, 10, Gold); count.alignment = TextAnchor.UpperRight;
            count.rectTransform.offsetMin = new Vector2(100,Height-18); count.rectTransform.offsetMax = new Vector2(-45,-3);
            var clearRect = new GameObject("Limpar busca", typeof(RectTransform)).GetComponent<RectTransform>();
            clearRect.SetParent(rect,false); clearRect.anchorMin = clearRect.anchorMax = new Vector2(1,.5f);
            clearRect.sizeDelta = new Vector2(32,36); clearRect.anchoredPosition = new Vector2(-22,0);
            var clearImage = clearRect.gameObject.AddComponent<Image>(); clearImage.color = new Color(.17f,.14f,.095f,1);
            var clear = clearRect.gameObject.AddComponent<Button>(); clear.targetGraphic = clearImage;
            clear.navigation = new Navigation { mode = Navigation.Mode.None };
            var x = Label("Limpar",clearRect,font,20,Parchment); x.text = "×"; x.alignment = TextAnchor.MiddleCenter;
            Input.onValueChanged.AddListener(value => {
                if (suppress) return; Query.Set(value); pending = true; refreshAt = Time.unscaledTime + .07f; messageUntil = 0;
            });
            Input.onEndEdit.AddListener(value => {
                if (suppress) return;
                releaseGuardUntil = Time.unscaledTime + .15f;
                if (pending) Refresh();
                if (Owner && Owner.CharacterUI && Owner.CharacterUI.CurrentSelectedGameObject == Input.gameObject)
                    Owner.CharacterUI.SetSelectedGameObject(null);
            });
            clear.onClick.AddListener(() => {
                Input.text = ""; Query.Set(""); Refresh();
                if (Owner && Owner.CharacterUI) Owner.CharacterUI.SetSelectedGameObject(Input.gameObject);
                Input.ActivateInputField();
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
            if (!reserved)
            {
                previousMax = viewport.offsetMax;
                var rect = (RectTransform)root.transform;
                rect.anchorMin = new Vector2(viewport.anchorMin.x,viewport.anchorMax.y); rect.anchorMax = viewport.anchorMax;
                rect.offsetMin = new Vector2(viewport.offsetMin.x,previousMax.y-Height);
                rect.offsetMax = previousMax;
                viewport.offsetMax = new Vector2(previousMax.x,previousMax.y-Height-Gap);
                reserved = true;
            }
            root.SetActive(true); root.transform.SetAsLastSibling(); CountDirty = true;
        }
        internal void Tick()
        {
            if (pending && Time.unscaledTime >= refreshAt) Refresh();
            if (CountDirty && Time.unscaledTime >= messageUntil)
            {
                int visible = Lists().Where(l => l && l.gameObject.activeInHierarchy).Sum(l => l.ActiveItemCount);
                count.text = visible == 0 && !Query.IsEmpty ? "Nenhum resultado" : visible + " visíveis";
                CountDirty = false;
            }
        }
        private void Refresh()
        {
            pending = false;
            // Refresh from full, live container data. Never save a filtered inventory copy.
            foreach (var list in Lists()) if (list && list.ReferencedContainer && list.gameObject.activeInHierarchy) list.Refresh();
            CountDirty = true;
        }
        internal void ExplainTakeAll()
        {
            if (Time.unscaledTime >= messageUntil && Owner && Owner.CharacterUI)
                Owner.CharacterUI.ShowInfoNotification("Limpe a busca para usar Pegar tudo.");
            count.text = "Limpe a busca para pegar tudo"; messageUntil = Time.unscaledTime + 3; CountDirty = true;
        }
        internal void Hide()
        {
            if (!root) return;
            if (!reserved && !root.activeSelf) return;
            suppress = true;
            if (Input)
            {
                bool focused = Input.isFocused; Input.DeactivateInputField(); Input.text = "";
                if (focused && Owner && Owner.CharacterUI && Owner.CharacterUI.CurrentSelectedGameObject == Input.gameObject)
                    Owner.CharacterUI.SetSelectedGameObject(null);
            }
            Query.Set(""); pending = false; messageUntil = 0; releaseGuardUntil = 0;
            if (Owner && Owner.IsDisplayed && Owner.gameObject.activeInHierarchy) Refresh();
            root.SetActive(false); suppress = false;
            if (reserved && viewport) viewport.offsetMax = previousMax;
            reserved = false;
        }
        public void Dispose()
        {
            Hide(); if (root) Object.Destroy(root);
        }
    }
}

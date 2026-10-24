using System;
using System.Collections.Generic;
using System.Linq;
using HarmonyLib;
using UnityEngine;
using UnityEngine.UI;
using Object = UnityEngine.Object;

namespace Companheiros
{
    internal sealed class MapOverlay : IDisposable
    {
        private sealed class Pin
        {
            internal GameObject Root, ProbeObject, Row;
            internal MapWorldMarker Probe;
            internal Character Character;
            internal Text Label, RowName, Status;
            internal Image Portrait, RowPortrait;
            internal RectTransform Arrow;
        }
        private readonly Dictionary<string, Pin> pins = new Dictionary<string, Pin>();
        private readonly HashSet<string> live = new HashSet<string>();
        private MapDisplay map;
        private GameObject roster;
        private RectTransform content;
        private Text title, note;
        private Font font;
        private float nextRoster, nextPins;
        private Sprite[] sprites;
        private Sprite arrowSprite;
        private readonly List<Texture2D> textures = new List<Texture2D>();
        private static readonly Color[] Colors = {
            new Color(.22f,.87f,.78f), new Color(.98f,.70f,.29f), new Color(.98f,.42f,.39f),
            new Color(.72f,.57f,1f), new Color(.35f,.73f,1f), new Color(1f,.50f,.74f)
        };
        private static readonly System.Reflection.FieldInfo HasMap = AccessTools.Field(typeof(MapDisplay), "m_currentAreaHasMap");
        private static readonly System.Reflection.FieldInfo Zoom = AccessTools.Field(typeof(MapDisplay), "m_zoomLevelSmooth");

        internal void Show(MapDisplay display)
        {
            if (!Plugin.Instance.MapEnabled.Value) { Hide(); return; }
            if (map != display || !roster) { ClearView(); map = display; CreateView(); }
            roster.SetActive(true);
            nextRoster = 0;
            nextPins = 0;
            Update(display);
        }

        internal void Update(MapDisplay display)
        {
            if (map != display || !roster || !roster.activeSelf) return;
            if (!Plugin.Instance.MapEnabled.Value || !display.IsDisplayed) { Hide(); return; }
            if (Time.unscaledTime >= nextRoster)
            {
                RefreshRoster();
                nextRoster = Time.unscaledTime + .25f;
            }
            if (Time.unscaledTime < nextPins) return;
            nextPins = Time.unscaledTime + .05f;
            var settings = Settings();
            float zoom = (float)Zoom.GetValue(display) * 1.0351562f;
            bool valid = settings != null && !float.IsNaN(zoom) && !float.IsInfinity(zoom) && zoom > 0;
            foreach (var pin in pins.Values)
            {
                // Network player objects may live in Unity's DontDestroyOnLoad scene.
                // Scene-name equality would incorrectly hide those players.
                bool positioned = valid && pin.Character && pin.Character.IsLateInitDone
                    && pin.Character.gameObject.activeInHierarchy && pin.Probe
                    && (!NetworkLevelLoader.Instance || NetworkLevelLoader.Instance.IsOverallLoadingDone);
                pin.Root.SetActive(positioned);
                if (!positioned) continue;
                // Retains the native underground/warp projection. Probe is DISABLED and was
                // created inactive, so it never joins the game's finite marker-display pool.
                pin.Probe.CalculateMapPosition(settings, 0, zoom);
                Vector2 p = pin.Probe.MapPosition;
                if (float.IsNaN(p.x) || float.IsNaN(p.y) || float.IsInfinity(p.x) || float.IsInfinity(p.y))
                { pin.Root.SetActive(false); continue; }
                pin.Root.transform.localPosition = new Vector3(p.x, p.y, 0);
                pin.Arrow.localEulerAngles = new Vector3(0, 0, -(pin.Character.transform.eulerAngles.y + settings.Rotation));
                pin.Status.text = pin.Character.IsDead ? "Caído • precisa de ajuda" : "Na região • posição ao vivo";
            }
            note.text = valid ? "Reúna o grupo no caravaneiro para viajar.\n200 pratas + 3 rações por viagem."
                : "Este local não tem mapa calibrado.\nA lista de jogadores continua disponível.";
        }

        private MapDependingScene Settings()
        {
            if (!(bool)HasMap.GetValue(map)) return null;
            var original = map.CurrentMapScene;
            if (original == null) return null;
            // Regional calibration from More Map Details (MIT, Sinai).
            // Use scene Area IDs rather than fragile inspector-array indexes.
            Vector2 offset, scale; float rotation;
            switch ((int)original.Area)
            {
                case 101: offset = new Vector2(-531, -543); scale = Vector2.one * .526f; rotation = 0; break;
                case 201: offset = new Vector2(-573, -515); scale = Vector2.one * .553f; rotation = 90; break;
                case 301: offset = new Vector2(3, -5); scale = Vector2.one * .534f; rotation = -90; break;
                case 501: offset = new Vector2(-500, -500); scale = Vector2.one * .5f; rotation = 0; break;
                case 401: offset = new Vector2(-504, -505); scale = Vector2.one * .5f; rotation = 0; break;
                case 602: offset = new Vector2(-504, -500); scale = Vector2.one * .503f; rotation = -90; break;
                default: return original.MarkerScale != Vector2.zero ? original : null;
            }
            // Never mutate the game's serialized MapDependingScene asset or other mods' calibration.
            return new MapDependingScene { Area = original.Area, MarkerOffset = offset, MarkerScale = scale, Rotation = rotation };
        }

        private void RefreshRoster()
        {
            live.Clear();
            var players = Global.Lobby?.PlayersInLobby;
            int row = 0;
            if (players != null)
            foreach (var player in players.OrderBy(p => p ? p.UID : "", StringComparer.Ordinal))
            {
                if (!player || string.IsNullOrEmpty(player.UID) || !live.Add(player.UID)) continue;
                Character c = player.ControlledCharacter;
                if (!pins.TryGetValue(player.UID, out var pin)) { pin = CreatePin(); pins.Add(player.UID, pin); }
                if (pin.Character != c || c && !pin.Probe)
                {
                    if (pin.ProbeObject) Object.Destroy(pin.ProbeObject);
                    pin.Character = c;
                    if (c)
                    {
                        pin.ProbeObject = new GameObject("Companheiros-coordinate-probe");
                        pin.ProbeObject.SetActive(false);
                        pin.ProbeObject.transform.SetParent(c.transform, false);
                        pin.Probe = pin.ProbeObject.AddComponent<MapWorldMarker>();
                        pin.Probe.Text = "";
                        pin.Probe.enabled = false;
                        pin.ProbeObject.SetActive(true);
                    }
                }
                string name = c ? Policy.DisplayName(c.Name) : "Aventureiro em conexão";
                bool self = c && map.CharacterUI && map.CharacterUI.TargetCharacter == c;
                int color = Policy.ColorIndex(string.IsNullOrEmpty(player.CharUID) ? player.UID : player.CharUID);
                pin.Label.text = name + (self ? " • VOCÊ" : "");
                pin.RowName.text = pin.Label.text;
                pin.Label.color = pin.RowName.color = Colors[color];
                pin.Portrait.sprite = pin.RowPortrait.sprite = sprites[color];
                pin.Portrait.color = c && c.IsDead ? new Color(.6f,.6f,.6f,.8f) : Color.white;
                pin.Status.text = c ? c.IsDead ? "Caído • precisa de ajuda" : "Conectado" : "Carregando personagem…";
                ((RectTransform)pin.Row.transform).anchoredPosition = new Vector2(0, -row++ * 52);
            }
            foreach (string key in pins.Keys.Where(k => !live.Contains(k)).ToArray()) { Remove(pins[key]); pins.Remove(key); }
            content.sizeDelta = new Vector2(0, Math.Max(row * 52, 1));
            title.text = "COMPANHEIROS  /  " + row;
        }

        private Pin CreatePin()
        {
            var pin = new Pin();
            pin.Root = Rect("Aventureiro", map.WorldMapMarkers, new Vector2(44, 44)).gameObject;
            pin.Portrait = Image("Retrato", pin.Root.transform, new Vector2(38, 38), Color.white);
            var arrow = Image("Direção", pin.Root.transform, new Vector2(18, 12), new Color(1,.85f,.46f));
            arrow.sprite = arrowSprite;
            pin.Arrow = arrow.rectTransform;
            // Rotate the whole radial pointer around the character instead of the portrait.
            var pointer = Rect("Rumo", pin.Root.transform, Vector2.zero);
            arrow.transform.SetParent(pointer, false); arrow.rectTransform.anchoredPosition = new Vector2(0, 28);
            pin.Arrow = pointer;
            pin.Label = Text("Nome", pin.Root.transform, new Vector2(240, 32), 15, TextAnchor.MiddleCenter);
            pin.Label.rectTransform.anchoredPosition = new Vector2(0, -31);
            pin.Row = Rect("Jogador conectado", content, new Vector2(230, 50)).gameObject;
            var rr = (RectTransform)pin.Row.transform; rr.anchorMin = rr.anchorMax = new Vector2(.5f,1); rr.pivot = new Vector2(.5f,1);
            pin.RowPortrait = Image("Retrato", rr, new Vector2(34, 34), Color.white);
            pin.RowPortrait.rectTransform.anchorMin = pin.RowPortrait.rectTransform.anchorMax = new Vector2(.5f,1);
            pin.RowPortrait.rectTransform.anchoredPosition = new Vector2(-94,-24);
            pin.RowName = Text("Nome", rr, new Vector2(174,26), 14, TextAnchor.MiddleLeft);
            pin.RowName.rectTransform.anchorMin = pin.RowName.rectTransform.anchorMax = new Vector2(.5f,1);
            pin.RowName.rectTransform.anchoredPosition = new Vector2(19,-15);
            pin.Status = Text("Estado", rr, new Vector2(174,22), 11, TextAnchor.MiddleLeft);
            pin.Status.rectTransform.anchorMin = pin.Status.rectTransform.anchorMax = new Vector2(.5f,1);
            pin.Status.rectTransform.anchoredPosition = new Vector2(19,-34);
            pin.Status.color = new Color(.72f,.77f,.81f);
            return pin;
        }

        private void CreateView()
        {
            var sample = map.GetComponentsInChildren<Text>(true).FirstOrDefault(t => t.font);
            font = sample ? sample.font : Resources.GetBuiltinResource<Font>("Arial.ttf");
            if (sprites == null) { sprites = Enumerable.Range(0,6).Select(i => Load("player-" + i + ".png")).ToArray(); arrowSprite = Load("player-arrow.png"); }
            roster = Rect("Companheiros de Aurai", map.transform, new Vector2(254, 350)).gameObject;
            var panel = (RectTransform)roster.transform;
            panel.anchorMin = panel.anchorMax = panel.pivot = new Vector2(1,1);
            panel.anchoredPosition = new Vector2(-24,-88);
            var bg = roster.AddComponent<Image>(); bg.color = new Color(.035f,.065f,.09f,.95f); bg.raycastTarget = false;
            var trim = Image("Filete dourado", panel, new Vector2(254,3), new Color(.79f,.63f,.34f)); trim.rectTransform.anchoredPosition = new Vector2(0,174);
            title = Text("Título", panel, new Vector2(230,35), 14, TextAnchor.MiddleLeft); title.rectTransform.anchoredPosition = new Vector2(0,148); title.color = new Color(1,.84f,.51f);
            var viewport = Rect("Jogadores", panel, new Vector2(230,236)); viewport.anchoredPosition = new Vector2(0,6);
            viewport.gameObject.AddComponent<Image>().color = new Color(0,0,0,.02f);
            viewport.gameObject.AddComponent<RectMask2D>();
            content = Rect("Lista", viewport, Vector2.zero); content.anchorMin = new Vector2(0,1); content.anchorMax = new Vector2(1,1); content.pivot = new Vector2(.5f,1);
            content.anchoredPosition = Vector2.zero;
            var scroll = viewport.gameObject.AddComponent<ScrollRect>(); scroll.viewport = viewport; scroll.content = content;
            scroll.horizontal = false; scroll.vertical = true; scroll.scrollSensitivity = 24; scroll.movementType = ScrollRect.MovementType.Clamped;
            note = Text("Caravana", panel, new Vector2(232,48), 11, TextAnchor.MiddleLeft); note.rectTransform.anchoredPosition = new Vector2(0,-143);
            note.color = new Color(.78f,.82f,.84f);
        }
        private Sprite Load(string name)
        {
            using (var stream = typeof(Plugin).Assembly.GetManifestResourceStream("Companheiros." + name))
            {
                if (stream == null) throw new InvalidOperationException("Retrato ausente: " + name);
                var bytes = new byte[checked((int)stream.Length)];
                int count = 0, read;
                while (count < bytes.Length && (read = stream.Read(bytes, count, bytes.Length-count)) > 0) count += read;
                if (count != bytes.Length) throw new InvalidOperationException("Retrato incompleto");
                var texture = new Texture2D(2,2,TextureFormat.RGBA32,false);
                if (!ImageConversion.LoadImage(texture, bytes)) { Object.Destroy(texture); throw new InvalidOperationException("PNG inválido"); }
                texture.wrapMode = TextureWrapMode.Clamp; textures.Add(texture);
                return Sprite.Create(texture, new Rect(0,0,texture.width,texture.height), new Vector2(.5f,.5f), 100);
            }
        }
        private static RectTransform Rect(string name, Transform parent, Vector2 size)
        {
            var r = new GameObject(name, typeof(RectTransform)).GetComponent<RectTransform>();
            r.SetParent(parent, false); r.anchorMin = r.anchorMax = r.pivot = new Vector2(.5f,.5f); r.sizeDelta = size; return r;
        }
        private static Image Image(string name, Transform parent, Vector2 size, Color color)
        {
            var image = Rect(name,parent,size).gameObject.AddComponent<Image>(); image.color = color; image.raycastTarget = false; return image;
        }
        private Text Text(string name, Transform parent, Vector2 size, int points, TextAnchor alignment)
        {
            var text = Rect(name,parent,size).gameObject.AddComponent<Text>();
            text.font = font; text.fontSize = points; text.alignment = alignment; text.supportRichText = false; text.raycastTarget = false;
            text.horizontalOverflow = HorizontalWrapMode.Wrap; text.verticalOverflow = VerticalWrapMode.Truncate;
            var shadow = text.gameObject.AddComponent<Shadow>(); shadow.effectColor = new Color(0,0,0,.95f); shadow.effectDistance = new Vector2(1,-1);
            return text;
        }
        internal void Hide()
        {
            if (roster) roster.SetActive(false);
            foreach (var p in pins.Values) if (p.Root) p.Root.SetActive(false);
        }
        private static void Remove(Pin p)
        {
            if (p.Root) Object.Destroy(p.Root);
            if (p.ProbeObject) Object.Destroy(p.ProbeObject);
            if (p.Row) Object.Destroy(p.Row);
        }
        private void ClearView()
        {
            foreach (var p in pins.Values) Remove(p); pins.Clear();
            if (roster) Object.Destroy(roster);
            map = null;
        }
        public void Dispose()
        {
            ClearView();
            if (sprites != null) foreach (var s in sprites) if (s) Object.Destroy(s);
            if (arrowSprite) Object.Destroy(arrowSprite);
            foreach (var t in textures) if (t) Object.Destroy(t);
        }
    }
}

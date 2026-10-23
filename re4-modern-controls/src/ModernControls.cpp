// Experimental extension of re4_tweaks, not an official upstream feature.
#include "dllmain.h"
#include "Patches.h"
#include "input.hpp"
#include "ConsoleWnd.h"
#include "ModernControls.h"
#include "MovementCore.h"
#include "CameraContinuity.h"
#include "NativeDiagnostics.h"
#include <array>
#include <cstdlib>
#include <cstring>

extern bool bImGuiUIFocus;

namespace {
using Routine = void(__cdecl*)(cPlayer*);
std::array<Routine, 7> originals{};
std::uint64_t* keyOn{};
std::uint64_t* keyTrigger{};
bool installed{};
bool enabled{};
bool diagnosticEnabled{};
float aimSpeed = 35.0f;
re4mc::CameraBasisTracker cameraBasis;
std::array<unsigned, 5> keyCodes{'W', 'S', 'A', 'D', VK_SHIFT};

bool executableAddress(const void* address) {
    MEMORY_BASIC_INFORMATION info{};
    if (!VirtualQuery(address, &info, sizeof(info)) || info.State != MEM_COMMIT ||
        (info.Protect & (PAGE_GUARD | PAGE_NOACCESS))) return false;
    return (info.Protect & (PAGE_EXECUTE | PAGE_EXECUTE_READ |
                          PAGE_EXECUTE_READWRITE | PAGE_EXECUTE_WRITECOPY)) != 0;
}

// Validate the *whole* dispatch-table candidate before changing a single byte.
// Walk and Run are independently resolved, using existing upstream signatures.
Routine* findTable(Routine walk, Routine run) {
    const auto base = reinterpret_cast<std::uint8_t*>(GetModuleHandleW(nullptr));
    const auto dos = reinterpret_cast<IMAGE_DOS_HEADER*>(base);
    const auto nt = reinterpret_cast<IMAGE_NT_HEADERS*>(base + dos->e_lfanew);
    const auto sections = IMAGE_FIRST_SECTION(nt);
    Routine* found = nullptr;
    for (unsigned s = 0; s < nt->FileHeader.NumberOfSections; ++s) {
        const auto& section = sections[s];
        if (!(section.Characteristics & IMAGE_SCN_MEM_READ) ||
             (section.Characteristics & IMAGE_SCN_MEM_EXECUTE)) continue;
        const auto start = base + section.VirtualAddress;
        const auto length = section.Misc.VirtualSize;
        for (std::size_t i = 0; i + 7 * sizeof(Routine) <= length; i += sizeof(Routine)) {
            auto candidate = reinterpret_cast<Routine*>(start + i);
            if (candidate[1] != walk || candidate[3] != run) continue;
            bool valid = true;
            for (int j = 0; j < 7; ++j) {
                if (!executableAddress(reinterpret_cast<const void*>(candidate[j]))) valid = false;
                for (int k = 0; k < j; ++k)
                    if (candidate[k] == candidate[j]) valid = false;
            }
            if (!valid) continue;
            if (found) return nullptr; // Ambiguity must disable this extension.
            found = candidate;
        }
    }
    return found;
}

re4mc::Context context(cPlayer* player) {
    re4mc::Context c;
    c.enabled = enabled && installed;
    c.focused = hWindow && GetForegroundWindow() == hWindow;
    c.keyboardMouse = isKeyboardMouse();
    c.blocked = bCfgMenuOpen || bConsoleOpen || bImGuiUIFocus || OptionOpenFlag();
    auto g = GlobalPtr();
    if (!g || !player) { c.mainLoop = false; return c; }
    if (player != PlayerPtr()) { c.playerObject = false; return c; }
    c.mainLoop = g->Rno0_20 == 3;
    c.blocked |= (g->flags_STOP_0_170[0] & 0x92000000U) != 0; // KEY, PL, CTRL
    c.blocked |= SubScreenWk && SubScreenWk->open_flag_2C != SS_OPEN_NULL;
    c.playerObject = player == PlayerPtr() && g->pl_type_4FC8 == PlayerCharacter::Leon;
    c.alive = g->playerHpCur_4FB4 > 0;
    c.routine0 = player->r_no_0_FC;
    c.routine1 = player->r_no_1_FD;
    c.collisionEnabled = (player->atari_2B4.m_flag_1A & (SAT_SCA_ENABLE | SAT_OBA_ENABLE))
                      == (SAT_SCA_ENABLE | SAT_OBA_ENABLE);
    c.scoped = SubScreenWk && (SubScreenWk->scope_flag_1B0 || SubScreenWk->binocular_flag_1B1);
    c.aiming = keyOn && (*keyOn & std::uint64_t(KEY_BTN::KEY_KAMAE));
    // A held knife retains the original routine, including its transitions.
    c.blocked |= keyOn && (*keyOn & std::uint64_t(KEY_BTN::KEY_KAMAE_KNIFE));
    return c;
}

re4mc::Keys readKeys(re4mc::Context& c) {
    if (!pInput) { c.blocked = true; return {}; }
    const auto lock = pInput->lock();
    c.blocked |= pInput->is_blocking_keyboard_input() || pInput->is_blocking_mouse_input();
    return {pInput->is_key_down(keyCodes[0]), pInput->is_key_down(keyCodes[1]),
            pInput->is_key_down(keyCodes[2]), pInput->is_key_down(keyCodes[3]),
            pInput->is_key_down(keyCodes[4])};
}

void setRoutine(cPlayer* player, unsigned routine) {
    if (player->r_no_1_FD == routine) return;
    player->r_no_1_FD = std::uint8_t(routine);
    player->r_no_2_FE = 0;
    player->r_no_3_FF = 0;
}

template<unsigned entry> void __cdecl dispatch(cPlayer* player) {
    re4mc::diagnostics::mark(1, entry); // Entering context/input collection.
    auto c = context(player);
    const auto keys = readKeys(c); // Release the input lock before calling native code.
    const auto decision = re4mc::decide(c, keys);
    const bool walking = decision.action == re4mc::Action::Walk || decision.action == re4mc::Action::Run;
    const bool cameraCanMove = CamCtrl && GlobalPtr() && !(GlobalPtr()->flags_STOP_0_170[0] & 0x40000000U);
    const bool newCameraBasis = cameraBasis.observe(reinterpret_cast<std::uintptr_t>(player),
                                                    decision.direction, walking && cameraCanMove);
    static std::array<unsigned, 5> observations{};
    const auto action = unsigned(decision.action);
    const bool report = observations[action]++ < 2;
    const auto invoke = [&](unsigned next) {
        if (report) re4mc::diagnostics::trace("before_native", entry, action, player,
            reinterpret_cast<const void*>(originals[next]));
        re4mc::diagnostics::mark(2, entry);
        originals[next](player);
        re4mc::diagnostics::mark(3, entry);
        if (report) re4mc::diagnostics::trace("after_native", entry, action, player,
            reinterpret_cast<const void*>(originals[next]));
    };
    if (decision.action == re4mc::Action::Native) { invoke(entry); return; }
    if (decision.action == re4mc::Action::AimMove) {
        // Keep native aim, fire cadence, ammunition, reload and hit detection.
        invoke(entry);
        // A shot/interaction can exit the weapon state. Never drag another action.
        const auto after = context(player);
        if (re4mc::decide(after, keys).action != re4mc::Action::AimMove) return;
        const auto& camera = GlobalPtr()->Camera_74;
        const auto step = re4mc::aimDisplacement(decision.direction,
            {-camera.Look_D0.x, -camera.Look_D0.z},
            {camera.Right_DC.x, camera.Right_DC.z}, aimSpeed, GlobalPtr()->deltaTime_70);
        // Called from the movement dispatch, BEFORE cPlayer's native collision
        // and floor resolution. Never update from EndScene or disable collision.
        player->pos_94.x += step.x;
        player->pos_94.z += step.z;
        return;
    }
    if (!fMousePosX || !fCameraPosX) { invoke(entry); return; }
    re4mc::diagnostics::mark(4, entry); // Input override and orbit update.
    re4mc::ScopedInput input(*keyOn, *keyTrigger, decision.action);
    unsigned next = 0;
    bool rebase = false;
    if (decision.action != re4mc::Action::Idle) {
        const auto orbit = re4mc::faceDirection(
            // PadRead has already updated mouseX this tick. The camera's cached
            // yaw can still be from the previous tick; do not discard new input.
            {player->ang_A0.y, re4mc::wrap(-re4mc::cameraOrbitScale * *fMousePosX),
             *fMousePosX}, decision.direction);
        rebase = newCameraBasis && std::abs(re4mc::wrap(orbit.playerYaw - player->ang_A0.y)) > 0.0001f;
        player->ang_A0.y = orbit.playerYaw;
        *fCameraPosX = orbit.cameraYaw;
        *fMousePosX = orbit.mouseX;
        next = decision.action == re4mc::Action::Run ? 3 : 1;
    }
    setRoutine(player, next);
    invoke(next); // Original walking/running animation and root motion.
    if (rebase && CamCtrl) {
        const auto after = re4mc::decide(context(player), keys).action;
        if (after == re4mc::Action::Walk || after == re4mc::Action::Run)
            re4mc::rebaseCamera(CamCtrl->m_QuasiFPS_278, player->ang_A0.y);
    }
}

void readConfig() {
    const auto path = rootPath + L"re4-modern-controls.ini";
    enabled = GetPrivateProfileIntW(L"CONTROLS", L"Enabled", 1, path.c_str()) != 0;
    diagnosticEnabled = GetPrivateProfileIntW(L"CONTROLS", L"Diagnostics", 0, path.c_str()) != 0;
    wchar_t number[80]{};
    GetPrivateProfileStringW(L"CONTROLS", L"AimMoveUnitsPerTick", L"35", number, 80, path.c_str());
    wchar_t* end{};
    const auto speed = std::wcstof(number, &end);
    if (end != number && *end == 0 && std::isfinite(speed)) aimSpeed = std::clamp(speed, 0.0f, 80.0f);
    const wchar_t* names[]{L"Forward", L"Back", L"Left", L"Right", L"Run"};
    for (unsigned i = 0; i < keyCodes.size(); ++i) {
        const auto key = GetPrivateProfileIntW(L"KEYS", names[i], keyCodes[i], path.c_str());
        if (key > 0 && key < 256) keyCodes[i] = key;
    }
}
} // namespace

bool ModernControls_OwnsLocomotion() {
    auto player = PlayerPtr();
    if (!player) return false;
    auto c = context(player);
    const auto keys = readKeys(c);
    const auto action = re4mc::decide(c, keys).action;
    return action == re4mc::Action::Walk || action == re4mc::Action::Run || action == re4mc::Action::Idle;
}

void re4t::init::ModernControls() {
    static_assert(sizeof(void*) == 4, "RE4 Steam UHD is a 32-bit game");
    readConfig();
    if (diagnosticEnabled) {
        const auto diagnosticPath = rootPath + L"re4-modern-controls-diagnostico.log";
        re4mc::diagnostics::initialize(diagnosticPath.c_str(), g_module_handle);
    }
    if (!enabled) return;
    const auto version = GameVersion();
    if (version != "1.1.0" && version != "1.0.6") {
        spd::log()->warn("ModernControls -> Unsupported regional/debug build {}; disabled", version);
        enabled = false; return;
    }
    // Resolve BEFORE the upstream inline hooks modify either function prologue.
    auto walk = hook::pattern("55 8B EC 83 EC ? 53 56 8B 75 ? 33 DB 38 9E");
    auto run = hook::pattern("55 8B EC 83 EC ? 53 56 8B 75 ? 0F B6 86 ? ? ? ? 33 DB");
    auto on = hook::pattern("A1 ? ? ? ? 33 C9 83 E0 ? 83 3D");
    auto trigger = hook::pattern("A1 ? ? ? ? 25 ? ? ? ? 0B C1 74 ? 88 0D");
    if (walk.size() != 1 || run.size() != 1 || on.size() != 1 || trigger.size() != 1) {
        spd::log()->error("ModernControls -> Signature mismatch; no dispatch hooks installed");
        enabled = false; return;
    }
    auto table = findTable(reinterpret_cast<Routine>(walk.get(0).get<void>(0)),
                           reinterpret_cast<Routine>(run.get(0).get<void>(0)));
    if (!table) {
        spd::log()->error("ModernControls -> Missing or ambiguous movement table; disabled");
        enabled = false; return;
    }
    keyOn = *on.get(0).get<std::uint64_t*>(1);
    keyTrigger = *trigger.get(0).get<std::uint64_t*>(1);
    std::memcpy(originals.data(), table, sizeof(originals));
    const std::array<Routine, 7> replacements{
        dispatch<0>, dispatch<1>, dispatch<2>, dispatch<3>, dispatch<4>, dispatch<5>, dispatch<6>};
    DWORD protection{};
    if (!VirtualProtect(table, sizeof(replacements), PAGE_READWRITE, &protection)) {
        spd::log()->error("ModernControls -> Could not write movement table; disabled");
        enabled = false; return;
    }
    std::memcpy(table, replacements.data(), sizeof(replacements));
    DWORD ignored{};
    VirtualProtect(table, sizeof(replacements), protection, &ignored);
    installed = true;
    re4t::cfg->bCameraImprovements = true;
    re4t::cfg->bUseMouseTurning = false;
    re4t::cfg->bResetCameraWhenRunning = false;
    re4t::cfg->bResetCameraAfterUsingWeapons = false;
    re4t::cfg->bResetCameraAfterUsingKnife = false;
    re4t::cfg->bUseSprintToggle = false;
    spd::log()->info("ModernControls -> EXPERIMENTAL 0.1.2, KB/M enabled; camera basis synchronization enabled; diagnostics={}; native Walk/Run prologues preserved; game validation pending", diagnosticEnabled);
}

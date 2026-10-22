#include "MovementCore.h"
#include <cstdlib>
#include <iostream>
#include <limits>
#include <stdexcept>

namespace {
int checks{};
void check(bool ok, const char* message) {
    ++checks;
    if (!ok) throw std::runtime_error(message);
}
bool near(float a, float b) { return std::abs(a - b) < 0.0001f; }
void directionAndCamera() {
    for (unsigned bits = 0; bits < 16; ++bits) {
        re4mc::Keys k{bool(bits & 1), bool(bits & 2), bool(bits & 4), bool(bits & 8)};
        const auto d = re4mc::direction(k);
        const bool moving = (k.forward != k.back) || (k.left != k.right);
        check(d.moving == moving, "opposite keys must cancel independently");
        check(near(std::hypot(d.right, d.forward), moving ? 1.0f : 0.0f), "diagonal speed");
        if (!moving) continue;
        for (int angle = -180; angle <= 180; angle += 15) {
            const float yaw = angle * re4mc::pi / 180;
            const float orbit = 0.7f;
            const auto result = re4mc::faceDirection({yaw, orbit, -orbit / 3}, d);
            check(near(re4mc::wrap(result.playerYaw + result.cameraYaw - yaw - orbit), 0),
                  "camera world heading must not follow player turns");
            check(near(re4mc::wrap(result.playerYaw - 3 * result.mouseX - yaw - orbit), 0),
                  "mouse orbit must preserve the same world heading");
            const auto repeated = re4mc::faceDirection(result, d);
            check(near(re4mc::wrap(repeated.playerYaw - result.playerYaw), 0),
                  "held WASD must not rotate again each frame");
        }
    }
    check(near(re4mc::headingOffset(re4mc::direction({true})), 0), "W points forward");
    check(near(re4mc::headingOffset(re4mc::direction({false, false, true})), re4mc::pi / 2), "A turns left");
    check(near(re4mc::headingOffset(re4mc::direction({false, false, false, true})), -re4mc::pi / 2), "D turns right");
}
void stateGates() {
    using namespace re4mc;
    Context c;
    check(decide(c, {true}).action == Action::Walk, "W walks");
    check(decide(c, {true, false, false, false, true}).action == Action::Run, "Shift runs");
    check(decide(c, {}).action == Action::Idle, "release stops");
    for (unsigned routine = 0; routine < 256; ++routine) {
        c.routine1 = std::uint8_t(routine);
        auto expected = routine < 6 ? Action::Walk : Action::Native;
        check(decide(c, {true}).action == expected, "special animations must retain native control");
    }
    c = {}; c.aiming = true;
    check(decide(c, {true}).action == Action::Native, "let the game raise its weapon");
    c.routine1 = 6;
    check(decide(c, {true}).action == Action::AimMove, "walking while aiming");
    check(decide(c, {}).action == Action::Native, "no aim drift without a key");
    check(decide(c, {true, true, true, true}).action == Action::Native, "opposite aim keys cancel");
    c.scoped = true;
    check(decide(c, {true}).action == Action::Native, "scope keeps original control");
    for (unsigned block = 0; block < 10; ++block) {
        c = {}; c.aiming = true; c.routine1 = 6;
        switch (block) {
        case 0: c.enabled = false; break;
        case 1: c.focused = false; break;
        case 2: c.keyboardMouse = false; break;
        case 3: c.blocked = true; break;
        case 4: c.mainLoop = false; break;
        case 5: c.alive = false; break;
        case 6: c.playerObject = false; break;
        case 7: c.collisionEnabled = false; break;
        case 8: c.routine0 = 1; break;
        case 9: c.routine1 = 11; break;
        }
        check(decide(c, {true}).action == Action::Native, "menu/focus/cinematic/death gate");
    }
}
void aimAndInput() {
    using namespace re4mc;
    const auto diagonal = direction({true, false, false, true});
    const XZ forward{0, 0.2f}, right{1, 0.01f};
    const auto full = aimDisplacement(diagonal, forward, right, 35, 1);
    const auto half = aimDisplacement(diagonal, forward, right, 35, 0.5f);
    check(near(std::hypot(full.x, full.z), 35), "pitch/diagonal must not affect ground speed");
    check(near(full.x, half.x * 2) && near(full.z, half.z * 2), "30/60 FPS equivalence");
    for (int hz : {30, 60, 120}) {
        XZ position{};
        for (int frame = 0; frame < hz; ++frame) {
            const auto step = aimDisplacement(diagonal, forward, right, 35, 30.0f / hz);
            position.x += step.x; position.z += step.z;
        }
        check(std::abs(std::hypot(position.x, position.z) - 1050) < 0.01f, "one-second travel equivalence");
    }
    const auto stalled = aimDisplacement(diagonal, forward, right, 35, 200);
    check(near(std::hypot(stalled.x, stalled.z), 35), "stalls must not cause a huge position jump");
    for (float bad : {0.0f, -1.0f, std::numeric_limits<float>::infinity(),
                      std::numeric_limits<float>::quiet_NaN()}) {
        auto step = aimDisplacement(diagonal, forward, right, 35, bad);
        check(step.x == 0 && step.z == 0, "invalid time cannot move the player");
    }
    check(aimDisplacement(diagonal, {}, right, 35, 1).x == 0, "degenerate camera");
    std::uint64_t on = 0x123456789ABCDEF0ULL, trigger = ~on;
    const auto savedOn = on, savedTrigger = trigger;
    {
        ScopedInput scope(on, trigger, Action::Run);
        check((on & directionMask) == 1, "native root motion sees only forward");
        check((on & runMask) != 0, "native run key");
        check((on & 0x890ULL) == (savedOn & 0x890ULL), "aim, fire and knife bits preserved");
        check((on & quickturnMask) == 0, "Shift+S must not cause a tank quickturn");
    }
    check(on == savedOn && trigger == savedTrigger, "original input restored after native call");
    try { ScopedInput scope(on, trigger, Action::Walk); throw 123; } catch (int) {}
    check(on == savedOn && trigger == savedTrigger, "input restored even if a callback throws");
}
}
int main() {
    try {
        directionAndCamera(); stateGates(); aimAndInput();
        std::cout << "PASS: " << checks << " movement assertions (no game runtime).\n";
        return EXIT_SUCCESS;
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return EXIT_FAILURE; }
}

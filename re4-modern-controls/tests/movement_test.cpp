#include "MovementCore.h"
#include "CameraContinuity.h"
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
            const auto result = re4mc::faceDirection({yaw, orbit, -orbit / re4mc::cameraOrbitScale}, d);
            check(near(re4mc::wrap(result.playerYaw + result.cameraYaw - yaw - orbit), 0),
                  "camera world heading must not follow player turns");
            check(near(re4mc::wrap(result.playerYaw - re4mc::cameraOrbitScale * result.mouseX - yaw - orbit), 0),
                  "mouse orbit must preserve the same world heading");
            check(std::abs(result.mouseX) < 1.0f, "mouse coordinate must remain within native limits");
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
    on = savedOn | 0x80ULL; trigger = savedTrigger | 0x80ULL;
    {
        ScopedInput scope(on, trigger, Action::Walk);
        on &= ~0x80ULL; trigger &= ~0x80ULL; // Native code consumes the fire input.
    }
    check(on == (savedOn & ~0x80ULL) && trigger == (savedTrigger & ~0x80ULL),
          "restoring locomotion must not resurrect native fire input");
}
void cameraContinuity() {
    using namespace re4mc;
    CameraBasisTracker tracker;
    const auto w = direction({true});
    const auto a = direction({false, false, true});
    check(tracker.observe(1, w, true), "entering locomotion initializes the camera basis");
    for (int frame = 0; frame < 120; ++frame)
        check(!tracker.observe(1, w, true), "held WASD must retain ordinary mouse interpolation");
    check(tracker.observe(1, a, true), "W to A requires a new camera basis");
    check(!tracker.observe(1, a, true), "a direction change is handled once");
    check(!tracker.observe(1, a, false), "aim/menu/cinematic must not modify the camera");
    check(tracker.observe(1, a, true), "returning from native control rearms synchronization");
    check(tracker.observe(2, a, true), "a different player instance cannot reuse camera state");
    check(!tracker.observe(2, {}, true), "idle does not reset camera smoothing");
    check(tracker.observe(2, a, true), "restarting after idle rearms synchronization");
    check(!tracker.observe(0, w, true), "null owner cannot queue a camera correction");

    struct Camera {
        struct Vec { float x{}, y{}, z{}; } m_pl_dir_16C;
        unsigned char m_init_flag_1B6{};
        float pitch{0.3f}, fov{60}, position{123};
    };
    const auto rotate = [](XZ point, float yaw) {
        return XZ{std::cos(yaw) * point.x + std::sin(yaw) * point.z,
                  -std::sin(yaw) * point.x + std::cos(yaw) * point.z};
    };
    const XZ worldCamera{400, -2000};
    unsigned oldJolts{};
    for (int degrees : {-180, -135, -90, -45, 45, 90, 135, 180}) {
        const float yaw = degrees * pi / 180;
        Camera camera;
        check(rebaseCamera(camera, yaw), "valid locomotion transition queues a camera correction");
        // Independently model native lateral history in player space. An old
        // shoulder offset must not be blended after rotating that space.
        const auto goal = rotate(worldCamera, -yaw);
        const XZ oldBlended{0.6f * worldCamera.x + 0.4f * goal.x, goal.z};
        const auto oldWorld = rotate(oldBlended, yaw);
        if (std::hypot(oldWorld.x - worldCamera.x, oldWorld.z - worldCamera.z) > 100)
            ++oldJolts;
        const auto local = camera.m_init_flag_1B6 ? goal : oldBlended;
        // Native one-shot forward direction selects the *current* actor basis,
        // even when the previous camera matrix is stale for this update.
        const float cameraYaw = std::atan2(camera.m_pl_dir_16C.x, camera.m_pl_dir_16C.z);
        const auto actual = rotate(local, cameraYaw);
        check(std::hypot(actual.x - worldCamera.x, actual.z - worldCamera.z) < 0.002f,
              "WASD changes must retain the camera's world position across coordinate frames");
        check(camera.pitch == 0.3f && camera.fov == 60 && camera.position == 123,
              "rebase cannot alter pitch, FOV or translation");
    }
    check(oldJolts >= 6, "fixture must reproduce the old camera jump before checking the fix");
    Camera scripted;
    scripted.m_pl_dir_16C = {0, 1, 0};
    check(!rebaseCamera(scripted, 0.4f) && scripted.m_pl_dir_16C.y == 1 &&
          scripted.m_init_flag_1B6 == 0, "scripted camera direction takes precedence");
    for (float bad : {std::numeric_limits<float>::infinity(), std::numeric_limits<float>::quiet_NaN()}) {
        Camera invalid;
        check(!rebaseCamera(invalid, bad) && invalid.m_init_flag_1B6 == 0,
              "nonfinite actor yaw cannot reach the camera");
        invalid.m_pl_dir_16C.x = bad;
        check(!rebaseCamera(invalid, 0) && invalid.m_init_flag_1B6 == 0,
              "nonfinite native direction must remain untouched");
    }
}
}
int main() {
    try {
        directionAndCamera(); stateGates(); aimAndInput(); cameraContinuity();
        std::cout << "PASS: " << checks << " movement assertions (no game runtime).\n";
        return EXIT_SUCCESS;
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return EXIT_FAILURE; }
}

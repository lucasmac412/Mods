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
            re4mc::FreeCameraFrame camera;
            check(camera.begin(1, 2, 3, 0, yaw), "valid camera acquisition");
            const float mouseX = 0.7f / re4mc::cameraOrbitScale;
            const float body = camera.movementYaw(d, mouseX);
            check(near(re4mc::wrap(body - yaw + 0.7f - re4mc::headingOffset(d)), 0),
                  "WASD heading must be relative to the independent camera");
            check(camera.begin(1, 2, 3, 1, body), "next update accepts turned body");
            check(near(re4mc::wrap(camera.movementYaw(d, mouseX) - body), 0),
                  "held WASD must not rotate again each frame");
            check(near(re4mc::wrap(camera.basisYaw() - yaw), 0),
                  "body turning must not rotate the camera coordinate frame");
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
    c.routine1 = 6; c.routine2 = 1;
    check(decide(c, {true}).action == Action::AimMove, "walking while aiming");
    check(decide(c, {}).action == Action::Native, "no aim drift without a key");
    check(decide(c, {true, true, true, true}).action == Action::Native, "opposite aim keys cancel");
    c.scoped = true;
    check(decide(c, {true}).action == Action::Native, "scope keeps original control");
    for (unsigned block = 0; block < 10; ++block) {
        c = {}; c.aiming = true; c.routine1 = 6; c.routine2 = 1;
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
void allCharacters() {
    using namespace re4mc;
    const Character characters[]{Character::Leon, Character::Ashley, Character::Ada,
                                 Character::Hunk, Character::Krauser, Character::Wesker};
    for (auto character : characters) {
        Context c;
        c.character = character;
        for (unsigned bits = 0; bits < 16; ++bits) {
            Keys keys{bool(bits & 1), bool(bits & 2), bool(bits & 4), bool(bits & 8)};
            const bool moving = (keys.forward != keys.back) || (keys.left != keys.right);
            check(decide(c, keys).action == (moving ? Action::Walk : Action::Idle),
                  "every playable character supports camera-relative WASD and idle");
            keys.run = true;
            check(decide(c, keys).action == (moving ? Action::Run : Action::Idle),
                  "every playable character uses their own native run routine");
        }
        for (unsigned routine = 7; routine < 256; ++routine) {
            c.routine1 = std::uint8_t(routine);
            check(decide(c, {true}).action == Action::Native,
                  "melee, grappling hook, ladders, crouch and interactions remain native for every character");
        }
        c.routine1 = 6; c.aiming = true;
        for (unsigned state = 0; state < 256; ++state) {
            c.routine2 = std::uint8_t(state);
            const bool canMove = character != Character::Ashley && (state == 1 || state == 2);
            check(decide(c, {true}).action == (canMove ? Action::AimMove : Action::Native),
                  "aim/fire can move; Ashley and draw/reload/holster/special weapon states stay native");
        }
        for (unsigned weapon : {0x13U, 0x16U, 0x17U, 0x19U, 0x1EU, 0x1FU, 0x20U, 0x29U, 0x2AU}) {
            c.weapon = std::uint8_t(weapon); c.routine2 = 2;
            check(decide(c, {true}).action == Action::Native, "throw animation cannot be translated by aim movement");
            c.routine2 = 1;
            check(decide(c, {true}).action == (character == Character::Ashley ? Action::Native : Action::AimMove),
                  "armed characters can move before an item throw");
        }
        c.weapon = 2; c.routine2 = 1;
        c.playerObject = false;
        check(decide(c, {true}).action == Action::Native, "companion Ashley and other NPCs are never driven by WASD");
        c.playerObject = true;
        for (unsigned routine = 0; routine <= 6; ++routine) {
            c.routine1 = std::uint8_t(routine);
            c.specialAction = true;
            check(decide(c, {true}).action == Action::Native, "transformation, scripted events and special attacks own all movement");
            c.specialAction = false;
            c.routine0 = 1;
            check(decide(c, {true}).action == Action::Native, "damage owns native movement");
            c.routine0 = 2;
            check(decide(c, {true}).action == Action::Native, "death owns native movement");
            c.routine0 = 0;
        }
    }
    for (unsigned type = 6; type < 256; ++type) {
        Context c; c.character = static_cast<Character>(type);
        check(decide(c, {true}).action == Action::Native, "internal composite and unknown character identifiers keep native control");
    }
    // Switching characters can reuse the same memory addresses in another mode.
    FreeCameraFrame frame;
    check(frame.begin(1, 2, 0x101, 5, 0.4f), "Leon camera acquisition");
    check(frame.begin(1, 2, 0x101 | (2U << 16), 6, -0.7f), "Ada reuses addresses in another character context");
    check(near(frame.basisYaw(), -0.7f), "character switch cannot retain Leon's camera basis");
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
    FreeCameraFrame frame;
    check(frame.begin(1, 2, 3, 10, 0.4f), "enter free camera");
    check(frame.current(1, 2, 3, 11), "same and adjacent native ticks are current");
    check(!frame.current(1, 2, 3, 12), "missed native ticks require a fresh handoff");
    check(frame.begin(1, 2, 3, 11, -2), "turn actor while following the same camera");
    check(near(frame.basisYaw(), 0.4f), "WASD must leave the camera frame unchanged");
    check(frame.begin(1, 2, 3, 30, -2), "reacquire after native interruption");
    check(near(frame.basisYaw(), -2), "reacquire the current native actor reference");
    check(frame.begin(1, 2, 4, 31, 1), "enter a different room");
    check(near(frame.basisYaw(), 1), "rooms cannot reuse a stale camera basis");
    check(frame.begin(1, 9, 4, 32, 2), "camera instance change");
    check(near(frame.basisYaw(), 2), "camera instance cannot reuse another camera state");
    check(frame.begin(9, 9, 4, 33, 0), "player instance change");
    check(near(frame.basisYaw(), 0), "player instance cannot reuse another player's frame");
    frame.reset();
    check(!frame.belongsTo(9, 9), "release returns control to native camera");
    check(!frame.begin(0, 9, 4, 34, 0), "null player rejected");
    check(!frame.begin(9, 0, 4, 34, 0), "null camera rejected");
    check(!frame.begin(9, 9, 4, 34, std::numeric_limits<float>::quiet_NaN()), "invalid yaw rejected");
    check(frame.begin(1, 2, 3, 0xFFFFFFFFU, 0.5f), "acquire before counter wrap");
    check(frame.begin(1, 2, 3, 0, 1), "native frame counter wraps");
    check(near(frame.basisYaw(), 0.5f), "counter wrap must not snap the camera");

    struct Camera {
        struct Vec { float x{}, y{}, z{}; } m_pl_dir_16C;
        unsigned char m_init_flag_1B6{};
        float pitch{0.3f}, fov{60}, position{123};
    };
    const auto rotate = [](XZ point, float yaw) {
        return XZ{std::cos(yaw) * point.x + std::sin(yaw) * point.z,
                  -std::sin(yaw) * point.x + std::cos(yaw) * point.z};
    };
    const XZ shoulder{400, -2000};
    for (int hz : {30, 60, 120}) {
        FreeCameraFrame follow;
        float actor = 0.3f;
        for (int tick = 0; tick < hz * 2; ++tick) {
            const unsigned bits = unsigned(tick) % 16;
            const auto d = direction({bool(bits & 1), bool(bits & 2), bool(bits & 4), bool(bits & 8)});
            // Mouse moves even as WASD changes every tick, including idle.
            const float mouse = -0.8f + 1.6f * float(tick) / float(hz * 2 - 1);
            check(follow.begin(1, 2, 3, std::uint32_t(tick), actor), "continuous acquisition");
            if (d.moving) actor = follow.movementYaw(d, mouse);
            Camera camera;
            camera.m_init_flag_1B6 = 7; // Native-owned state, not ours to reset.
            check(queueCameraBasis(camera, follow.basisYaw()), "queue independent camera basis");
            check(queueCameraBasis(camera, follow.basisYaw()), "repeat same native update is harmless");
            const float basis = std::atan2(camera.m_pl_dir_16C.x, camera.m_pl_dir_16C.z);
            const auto actual = rotate(rotate(shoulder, -cameraOrbitScale * mouse), basis);
            const auto expected = rotate(shoulder, 0.3f - cameraOrbitScale * mouse);
            check(std::hypot(actual.x - expected.x, actual.z - expected.z) < 0.002f,
                  "WASD and body direction must not disturb mouse-controlled camera position");
            check(camera.m_init_flag_1B6 == 7 && camera.pitch == 0.3f && camera.fov == 60 && camera.position == 123,
                  "camera update preserves interpolation, pitch, FOV and translation");
            for (bool aiming : {false, true}) {
                const auto handoff = follow.handoff(actor, mouse, aiming);
                check(near(wrap(handoff.playerYaw + handoff.cameraYaw - follow.viewYaw(mouse)), 0),
                      "native handoff retains world view heading");
                check(near(wrap(handoff.playerYaw - cameraOrbitScale * handoff.mouseX - follow.viewYaw(mouse)), 0),
                      "native mouse accumulator receives the same world heading");
                if (aiming) check(handoff.cameraYaw == 0 && handoff.mouseX == 0,
                                  "native aim starts facing the saved view with zero orbit");
                else check(handoff.playerYaw == actor, "menu handoff cannot turn the actor");
            }
        }
    }
    Camera scripted;
    scripted.m_pl_dir_16C = {0, 1, 0};
    check(!queueCameraBasis(scripted, 0.4f) && scripted.m_pl_dir_16C.y == 1 &&
          scripted.m_init_flag_1B6 == 0, "scripted camera direction takes precedence");
    for (float bad : {std::numeric_limits<float>::infinity(), std::numeric_limits<float>::quiet_NaN()}) {
        Camera invalid;
        check(!queueCameraBasis(invalid, bad) && invalid.m_init_flag_1B6 == 0,
              "nonfinite basis cannot reach the camera");
        invalid.m_pl_dir_16C.x = bad;
        check(!queueCameraBasis(invalid, 0) && invalid.m_init_flag_1B6 == 0,
              "nonfinite native direction must remain untouched");
    }
}

}
int main() {
    try {
        directionAndCamera(); stateGates(); allCharacters(); aimAndInput(); cameraContinuity();
        std::cout << "PASS: " << checks << " movement assertions (no game runtime).\n";
        return EXIT_SUCCESS;
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return EXIT_FAILURE; }
}

// Independent camera reference for keyboard locomotion. No game code included.
#pragma once
#include "MovementCore.h"

namespace re4mc {
struct CameraHandoff { float playerYaw{}, cameraYaw{}, mouseX{}; };
class FreeCameraFrame {
    std::uintptr_t player_{}, camera_{};
    std::uint32_t room_{}, frame_{};
    float baseYaw_{};
    bool active_{};
public:
    bool belongsTo(std::uintptr_t player, std::uintptr_t camera) const {
        return active_ && player_ == player && camera_ == camera;
    }
    bool current(std::uintptr_t player, std::uintptr_t camera, std::uint32_t room,
                 std::uint32_t frame) const {
        return belongsTo(player, camera) && room_ == room && std::uint32_t(frame - frame_) <= 1;
    }
    bool sameRoom(std::uint32_t room) const { return active_ && room_ == room; }
    bool begin(std::uintptr_t player, std::uintptr_t camera, std::uint32_t room,
               std::uint32_t frame, float actorYaw) {
        if (!player || !camera || !std::isfinite(actorYaw)) { reset(); return false; }
        // Unsigned subtraction also handles the engine frame counter wrapping.
        if (!current(player, camera, room, frame)) {
            baseYaw_ = wrap(actorYaw);
        }
        player_ = player; camera_ = camera; room_ = room; frame_ = frame;
        active_ = true;
        return true;
    }
    void reset() { active_ = false; }
    float basisYaw() const { return baseYaw_; }
    float viewYaw(float mouseX) const { return wrap(baseYaw_ - cameraOrbitScale * mouseX); }
    float movementYaw(Direction d, float mouseX) const { return wrap(viewYaw(mouseX) + headingOffset(d)); }
    CameraHandoff handoff(float actorYaw, float mouseX, bool aiming) const {
        const float view = viewYaw(mouseX);
        const float body = aiming ? view : actorYaw;
        const float relative = wrap(view - body);
        return {body, relative, -relative / cameraOrbitScale};
    }
};

// Keep the native camera in one coordinate frame across ALL WASD directions,
// including idle. Only mouse input changes its orbit; actor turns do not.
// The engine consumes this direction while computing its collision-aware camera.
// Do not reset m_init_flag or overwrite interpolation history.
template<class Camera> bool queueCameraBasis(Camera& camera, float basisYaw) {
    auto& queued = camera.m_pl_dir_16C;
    if (!std::isfinite(basisYaw) || !std::isfinite(queued.x) ||
        !std::isfinite(queued.y) || !std::isfinite(queued.z)) return false;
    const float x = std::sin(basisYaw), z = std::cos(basisYaw);
    if (std::abs(queued.x) + std::abs(queued.y) + std::abs(queued.z) > 0.0001f) {
        // A repeat invocation in the same tick can see our own pending vector.
        // An unrelated native/scripted direction always takes precedence.
        return std::abs(queued.x - x) < 0.0001f && std::abs(queued.y) < 0.0001f &&
               std::abs(queued.z - z) < 0.0001f;
    }
    queued.x = x; queued.y = 0; queued.z = z;
    return true;
}
} // namespace re4mc

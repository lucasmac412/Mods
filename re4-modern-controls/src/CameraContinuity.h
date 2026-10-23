// Preserve the camera's coordinate frame when keyboard locomotion turns Leon.
#pragma once
#include "MovementCore.h"

namespace re4mc {
class CameraBasisTracker {
    std::uintptr_t owner_{};
    float heading_{};
    bool active_{};
public:
    bool observe(std::uintptr_t owner, Direction d, bool freeLocomotion) {
        if (!freeLocomotion || !d.moving || owner == 0) {
            active_ = false;
            return false;
        }
        const float heading = headingOffset(d);
        const bool changed = !active_ || owner_ != owner ||
                             std::abs(wrap(heading - heading_)) > 0.0001f;
        owner_ = owner; heading_ = heading; active_ = true;
        return changed;
    }
};

// CameraQuasiFPS applies shoulder offsets in a player-relative frame. Its
// cached lateral interpolation must not mix offsets from two different frames.
// Use the engine's one-shot direction and initialization fields; preserve its
// collision, pitch, FOV and subsequent ordinary mouse interpolation.
template<class Camera> bool rebaseCamera(Camera& camera, float playerYaw) {
    auto& queued = camera.m_pl_dir_16C;
    if (!std::isfinite(playerYaw) || !std::isfinite(queued.x) ||
        !std::isfinite(queued.y) || !std::isfinite(queued.z)) return false;
    // A native scripted direction takes precedence over this optional correction.
    if (std::abs(queued.x) + std::abs(queued.y) + std::abs(queued.z) > 0.0001f) return false;
    queued.x = std::sin(playerYaw);
    queued.y = 0;
    queued.z = std::cos(playerYaw);
    camera.m_init_flag_1B6 = 1;
    return true;
}
} // namespace re4mc

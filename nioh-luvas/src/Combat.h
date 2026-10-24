// Original combat simulation. No connection to Nioh's engine has been installed.
#pragma once
#include "Moves.generated.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace moonfist {
enum class Command : unsigned { Light, Heavy, Caps };
struct Gate { bool player{true}, gameplay{true}, alive{true}, equipped{true}, interrupted{}; };
struct Impact { unsigned hit; const Move* move; std::uint64_t attack; };
class Combat {
    const Move* active_{};
    double elapsed_{}, cooldown_{}, ki_{100};
    unsigned stance_{1}, nextHit_{};
    std::uint64_t attack_{};
    std::array<bool,3> held_{};
    bool allowed_{true};
public:
    void gate(Gate g) {
        allowed_ = g.player && g.gameplay && g.alive && g.equipped && !g.interrupted;
        if (!allowed_) { active_ = nullptr; elapsed_ = 0; }
    }
    bool stance(unsigned value) {
        if (value > 2 || active_) return false;
        stance_ = value; return true;
    }
    bool input(Command command, bool down) {
        const auto i = unsigned(command);
        if (i >= held_.size()) return false;
        const bool edge = down && !held_[i];
        held_[i] = down;
        if (!edge || !allowed_ || active_ || (command == Command::Caps && cooldown_ > 0)) return false;
        for (const auto& move : moves) {
            if (unsigned(move.input) != i || (move.stance >= 0 && unsigned(move.stance) != stance_)) continue;
            if (ki_ < move.ki) return false;
            ki_ -= move.ki; active_ = &move; elapsed_ = 0; nextHit_ = 0; ++attack_;
            if (command == Command::Caps) cooldown_ = move.cooldown;
            return true;
        }
        return false;
    }
    std::vector<Impact> tick(double seconds) {
        std::vector<Impact> result;
        if (!std::isfinite(seconds) || seconds <= 0 || !allowed_) return result;
        // A stall must not discharge all remaining hit windows in one update.
        const double dt = std::min(seconds, 0.1);
        cooldown_ = std::max(0.0, cooldown_ - dt);
        if (!active_) { ki_ = std::min(100.0, ki_ + 16 * dt); return result; }
        elapsed_ += dt;
        while (nextHit_ < active_->hitCount && elapsed_ + 1e-9 >= active_->hits[nextHit_]) {
            result.push_back({nextHit_, active_, attack_}); ++nextHit_;
        }
        if (elapsed_ + 1e-9 >= active_->duration) active_ = nullptr;
        return result;
    }
    double ki() const { return ki_; }
    double cooldown() const { return cooldown_; }
    const Move* active() const { return active_; }
};
// Per-impact target deduplication. Future engine adapters must supply valid
// actor handles; this module never guesses or dereferences game addresses.
class HitLedger {
    std::uint64_t attack_{};
    std::array<std::vector<std::uint64_t>,8> targets_;
public:
    bool accept(const Impact& impact, std::uint64_t target, std::uint64_t owner,
                bool ally, double distance) {
        if (!impact.move || !impact.attack || impact.attack < attack_ ||
            impact.hit >= impact.move->hitCount || impact.hit >= targets_.size() ||
            !target || target == owner || ally || !std::isfinite(distance) ||
            distance < 0 || distance > impact.move->range) return false;
        if (attack_ != impact.attack) {
            for (auto& targets : targets_) targets.clear();
            attack_ = impact.attack;
        }
        auto& targets = targets_[impact.hit];
        if (std::find(targets.begin(), targets.end(), target) != targets.end()) return false;
        targets.push_back(target); return true;
    }
};
} // namespace moonfist

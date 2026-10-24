// Generated from assets/weapon.json. Authoring units, not Nioh engine IDs.
#pragma once
#include <array>
namespace moonfist {
struct Move { const char* id; int stance, input; double ki, duration, damage, range, cooldown; std::array<double,8> hits; unsigned hitCount; };
inline constexpr std::array<Move, 7> moves{{
{"low_light", 0, 0, 8, 0.55, 12, 1.8, 0, {0.14, 0.27, 0, 0, 0, 0, 0, 0}, 2},
{"low_heavy", 0, 1, 14, 0.78, 28, 2.5, 0, {0.32, 0, 0, 0, 0, 0, 0, 0}, 1},
{"mid_light", 1, 0, 13, 0.82, 16, 2.2, 0, {0.19, 0.36, 0.54, 0, 0, 0, 0, 0}, 3},
{"mid_heavy", 1, 1, 21, 1.03, 44, 7.0, 0, {0.49, 0, 0, 0, 0, 0, 0, 0}, 1},
{"high_light", 2, 0, 19, 1.0, 27, 2.8, 0, {0.34, 0.6, 0, 0, 0, 0, 0, 0}, 2},
{"high_heavy", 2, 1, 30, 1.3, 68, 4.5, 0, {0.73, 0, 0, 0, 0, 0, 0, 0}, 1},
{"caps_cut", -1, 2, 45, 1.65, 14, 9.0, 8.0, {0.65, 0.72, 0.79, 0.86, 0.93, 1.0, 1.07, 1.14}, 8}
}};
}

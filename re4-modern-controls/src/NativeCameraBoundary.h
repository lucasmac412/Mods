// The injector preserves GP registers, but a mid-function hook can also have
// live x87/SSE values. Keep those intact while running the camera callback.
#pragma once
#include <intrin.h>

namespace re4mc {
template<class Callback> __declspec(noinline) void preserveCameraFP(Callback callback) {
    alignas(16) unsigned char state[512];
    _fxsave(state);
    callback();
    _fxrstor(state);
}
} // namespace re4mc

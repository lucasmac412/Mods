// Exercise the actual x86 floating-point boundary used by the camera hook.
#include "NativeCameraBoundary.h"
#include <cstring>
#include <iostream>

__declspec(noinline) void clobberCameraFP() {
    __asm {
        fninit
        fld1
        fldpi
        pcmpeqd xmm0, xmm0
        pcmpeqd xmm1, xmm1
        pcmpeqd xmm2, xmm2
        pcmpeqd xmm3, xmm3
        pcmpeqd xmm4, xmm4
        pcmpeqd xmm5, xmm5
        pcmpeqd xmm6, xmm6
        pcmpeqd xmm7, xmm7
    }
    _mm_setcsr(0x5F80); // All exceptions masked; different rounding mode.
}

int main() {
    static_assert(sizeof(void*) == 4, "Native camera fixture requires x86");
    alignas(16) unsigned char original[512]{}, expected[512]{}, broken[512]{}, actual[512]{};
    _fxsave(original);
    __asm {
        fninit
        fldz
        fldln2
        pxor xmm0, xmm0
        pxor xmm1, xmm1
        pxor xmm2, xmm2
        pxor xmm3, xmm3
        pxor xmm4, xmm4
        pxor xmm5, xmm5
        pxor xmm6, xmm6
        pxor xmm7, xmm7
    }
    _mm_setcsr(0x1F80);
    _fxsave(expected);
    clobberCameraFP();
    _fxsave(broken);
    const bool reproduced = std::memcmp(expected, broken, 512) != 0;
    _fxrstor(expected);
    unsigned calls{};
    bool preserved = true;
    for (unsigned n = 0; n < 128; ++n) {
        re4mc::preserveCameraFP([&]() { clobberCameraFP(); ++calls; });
        _fxsave(actual);
        preserved &= std::memcmp(expected, actual, 512) == 0;
    }
    _fxrstor(original);
    if (!reproduced || !preserved || calls != 128) {
        std::cerr << "FAIL: native camera FP/SSE boundary\n";
        return 1;
    }
    std::cout << "PASS: unprotected callback changes FP/SSE state; camera boundary preserves x87, XMM0-7 and MXCSR in 128 calls. No gameplay executed.\n";
    return 0;
}

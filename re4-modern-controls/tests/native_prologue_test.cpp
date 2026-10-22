// Real x86 execution test using the pinned injector and extracted old callbacks.
// The probe restores its frame out of band so the broken hook can be measured
// without executing its corrupted epilogue. It does not contain RE4 game code.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <Windows.h>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <iterator>
#include <injector/assembly.hpp>
#include "NativeDiagnostics.h"

void MouseTurn(float = 10.0f) {} // Camera work is irrelevant to the stack bug.
#include "LegacyPrologueHooks.h"

namespace {
volatile DWORD entryEsp{}, entryEbp{}, seenEsp{}, seenEbp{}, seenSavedEbp{}, seenArg{};
constexpr DWORD sentinel = 0xABCDEF00, argument = 0x13572468;

__declspec(naked) DWORD __cdecl probe(DWORD) {
    __asm {
        push ebp
        mov ebp, esp
        sub esp, 8
        // Observe the actual machine frame after the optional five-byte hook.
        mov seenEbp, ebp
        mov seenEsp, esp
        mov eax, entryEsp
        mov ecx, [eax - 4]
        mov seenSavedEbp, ecx
        mov eax, [ebp + 8]
        mov seenArg, eax
        // Deliberate out-of-band recovery, not part of the production patch.
        mov esp, entryEsp
        mov ebp, entryEbp
        mov eax, [esp + 4]
        ret
    }
}

__declspec(naked) DWORD __cdecl callProbe(DWORD, DWORD) {
    __asm {
        push ebp
        mov ebp, esp
        push [ebp + 12]
        mov entryEsp, esp
        sub entryEsp, 4
        mov entryEbp, ebp
        call probe
        add esp, 4
        mov esp, ebp
        pop ebp
        ret
    }
}

bool frameValid() {
    if (callProbe(sentinel, argument) != argument) return false;
    return seenEbp == entryEsp - 4 && seenEsp == entryEsp - 12 &&
           seenSavedEbp == entryEbp && seenArg == argument;
}

bool restore(std::uint8_t* at, const std::uint8_t* bytes) {
    DWORD protection{}, ignored{};
    if (!VirtualProtect(at, 5, PAGE_EXECUTE_READWRITE, &protection)) return false;
    std::memcpy(at, bytes, 5);
    FlushInstructionCache(GetCurrentProcess(), at, 5);
    return VirtualProtect(at, 5, protection, &ignored) != FALSE;
}

template<class Legacy> bool regression(const char* name) {
    auto start = reinterpret_cast<std::uint8_t*>(probe);
    const std::uint8_t expected[]{0x55, 0x8B, 0xEC, 0x83, 0xEC, 0x08};
    if (std::memcmp(start, expected, sizeof(expected)) != 0 || !frameValid()) return false;
    injector::MakeInline<Legacy>(start + 1, start + 6);
    FlushInstructionCache(GetCurrentProcess(), start, 6);
    const bool broken = !frameValid() && seenEbp != entryEsp - 4 &&
                        seenEsp != entryEsp - 12 && seenSavedEbp != entryEbp &&
                        seenArg == sentinel;
    const bool restored = restore(start + 1, expected + 1);
    if (!broken || !restored) return false;
    for (unsigned n = 0; n < 64; ++n) if (!frameValid()) return false;
    std::cout << "PASS: " << name << " old callback corrupts EBP/ESP/argument; "
                 "native prologue remains valid in 64 repeated calls.\n";
    return true;
}

bool handledException{};
void exceptionProbe() {
    __try { RaiseException(EXCEPTION_ACCESS_VIOLATION, 0, 0, nullptr); }
    __except (EXCEPTION_EXECUTE_HANDLER) { handledException = true; }
}
bool diagnosticsTest() {
    re4mc::diagnostics::initialize(L"native-prologue-diagnostics.log", GetModuleHandleW(nullptr));
    re4mc::diagnostics::mark(77, 3);
    exceptionProbe();
    re4mc::diagnostics::close();
    std::ifstream file("native-prologue-diagnostics.log");
    const std::string text{std::istreambuf_iterator<char>(file), {}};
    const bool ok = handledException && text.find("exception code=C0000005") != std::string::npos &&
                    text.find("phase=77 entry=3") != std::string::npos;
    if (ok) std::cout << "PASS: exception observation writes native registers and preserves SEH handling.\n";
    return ok;
}
}

int main() {
    static_assert(sizeof(void*) == 4, "Execute this regression as x86");
    if (!regression<TurnHookWalk>("Walk") || !regression<TurnHookRun>("Run") || !diagnosticsTest()) {
        std::cerr << "FAIL: native prologue/diagnostic regression.\n";
        return 1;
    }
    std::cout << "PASS: native-boundary regressions; no RE4 gameplay was executed.\n";
}

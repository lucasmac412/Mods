// Small local diagnostics for the Win32 boundary. No data is sent anywhere.
#pragma once
#include <Windows.h>
#include <cstdio>

namespace re4mc::diagnostics {
inline HANDLE file = INVALID_HANDLE_VALUE;
inline PVOID exceptionHandler{};
inline volatile LONG phase{}, entry{}, handling{}, exceptionCount{};
inline void mark(LONG next, LONG routine) {
    if (file == INVALID_HANDLE_VALUE) return;
    InterlockedExchange(&entry, routine);
    InterlockedExchange(&phase, next);
}
inline void write(const char* text) {
    if (file == INVALID_HANDLE_VALUE) return;
    DWORD length = 0;
    while (text[length]) ++length;
    DWORD written{};
    WriteFile(file, text, length, &written, nullptr);
}
inline LONG CALLBACK onException(EXCEPTION_POINTERS* error) {
    const auto code = error->ExceptionRecord->ExceptionCode;
    if (code != EXCEPTION_ACCESS_VIOLATION && code != EXCEPTION_ILLEGAL_INSTRUCTION &&
        code != EXCEPTION_IN_PAGE_ERROR) return EXCEPTION_CONTINUE_SEARCH;
    if (InterlockedIncrement(&exceptionCount) > 8 ||
        InterlockedCompareExchange(&handling, 1, 0)) return EXCEPTION_CONTINUE_SEARCH;
    char line[512]{};
    _snprintf_s(line, sizeof(line), _TRUNCATE,
        "exception code=%08lX address=%p EIP=%08lX ESP=%08lX EBP=%08lX phase=%ld entry=%ld thread=%lu\r\n",
        code, error->ExceptionRecord->ExceptionAddress, error->ContextRecord->Eip,
        error->ContextRecord->Esp, error->ContextRecord->Ebp,
        InterlockedCompareExchange(&phase, 0, 0), InterlockedCompareExchange(&entry, 0, 0),
        GetCurrentThreadId());
    write(line);
    if (file != INVALID_HANDLE_VALUE) FlushFileBuffers(file);
    InterlockedExchange(&handling, 0);
    // Observation only. Never swallow an exception or retry an invalid instruction.
    return EXCEPTION_CONTINUE_SEARCH;
}
inline void initialize(const wchar_t* path, HMODULE mod) {
    if (file != INVALID_HANDLE_VALUE) return;
    file = CreateFileW(path, GENERIC_WRITE, FILE_SHARE_READ, nullptr, CREATE_ALWAYS,
                       FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return;
    char line[256]{};
    _snprintf_s(line, sizeof(line), _TRUNCATE,
        "RE4 Modern Controls 0.1.4 experimental; game=%p mod=%p\r\n"
        "First-chance exceptions can be handled elsewhere; this is not a minidump.\r\n",
        static_cast<void*>(GetModuleHandleW(nullptr)), static_cast<void*>(mod));
    write(line);
    exceptionHandler = AddVectoredExceptionHandler(1, onException);
}
inline void trace(const char* message, unsigned routine, unsigned action,
                  const void* player, const void* target) {
    if (file == INVALID_HANDLE_VALUE) return;
    char line[320]{};
    _snprintf_s(line, sizeof(line), _TRUNCATE,
        "%s entry=%u action=%u player=%p native=%p\r\n", message, routine, action, player, target);
    write(line);
}
inline void close() {
    if (exceptionHandler) RemoveVectoredExceptionHandler(exceptionHandler);
    exceptionHandler = nullptr;
    if (file != INVALID_HANDLE_VALUE) CloseHandle(file);
    file = INVALID_HANDLE_VALUE;
}
} // namespace re4mc::diagnostics

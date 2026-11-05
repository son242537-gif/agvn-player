/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
/*
 * agvn-winhandler.exe /heappad: starts a 32-bit game whose HeapAlloc(HEAP_ZERO_MEMORY) blocks get two more zero bytes,
 * as Windows' own compatibility shim HeapPadAllocation pads them. SRPG Studio's game.exe reads a UTF-16 script into
 * size + 1 zeroed bytes and hands it to JScript, whose end needs two: the byte after the block keeps an old block's
 * text under Wine's heap, and the game stopped with "Syntax error" on the line after a script's last (06/11/2026).
 *
 * No instruction of the game changes, which a phone's x86 emulator could keep translated: the game starts suspended,
 * and a new page holds a thunk and a TLS callback (heap-pad-stub.s). The game's TLS directory lists the callback first.
 * The loader calls it once the imports are filled, before the game's own code, and it points the game's HeapAlloc
 * import at the thunk. Anything that fails leaves the game as it was.
 */
#include <windows.h>
#include <winternl.h>
#include <stddef.h>
#include <stdio.h>

#include "heap-pad-stub.h" /* heap_pad_stub_bin[], from heap-pad-stub.s */

#define PAD_DATA 0x200
#define PAD_CALLBACKS 0x300
#define PAD_CALLBACK 0x40
#define PAD_MAX_CALLBACKS 30

/* At PAD_DATA in the page, as heap-pad-stub.s reads it */
typedef struct {
  DWORD heapAllocSlot, getModuleHandleSlot, getProcAddressSlot, unused[2], state, oldProtect, scratch;
  WCHAR kernel32[16];
  char virtualProtect[16];
} PadData;

static BOOL readAt(HANDLE p, DWORD64 at, void *to, SIZE_T size) {
  SIZE_T got = 0;
  return ReadProcessMemory(p, (LPCVOID)(ULONG_PTR)at, to, size, &got) && got == size;
}

static BOOL writeAt(HANDLE p, DWORD64 at, const void *from, SIZE_T size) {
  DWORD old;
  SIZE_T done = 0;
  LPVOID to = (LPVOID)(ULONG_PTR)at;
  if (!VirtualProtectEx(p, to, size, PAGE_READWRITE, &old))
    return FALSE;
  BOOL ok = WriteProcessMemory(p, to, from, size, &done) && done == size;
  VirtualProtectEx(p, to, size, old, &old);
  return ok;
}

/* The 32-bit game's image base, from its 32-bit PEB (or the 64-bit one); 0 while Wine still maps it */
static DWORD imageBase(HANDLE p) {
  ULONG_PTR peb32 = 0;
  DWORD base32 = 0;
  DWORD64 base64 = 0;
  WORD mz = 0;
  if (!NtQueryInformationProcess(p, ProcessWow64Information, &peb32, sizeof(peb32), NULL) && peb32 &&
      readAt(p, peb32 + 8, &base32, 4) && base32 && readAt(p, base32, &mz, 2) && mz == IMAGE_DOS_SIGNATURE)
    return base32;
  PROCESS_BASIC_INFORMATION info;
  if (!NtQueryInformationProcess(p, ProcessBasicInformation, &info, sizeof(info), NULL) && info.PebBaseAddress &&
      readAt(p, (ULONG_PTR)info.PebBaseAddress + 0x10, &base64, 8) && base64 && base64 < 0x80000000 &&
      readAt(p, base64, &mz, 2) && mz == IMAGE_DOS_SIGNATURE)
    return (DWORD)base64;
  return 0;
}

/* The import slot of kernel32's function name in the PE32 image at base, or 0 */
static DWORD importSlot(HANDLE p, DWORD base, const IMAGE_NT_HEADERS32 *nt, const char *function) {
  DWORD imports = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT].VirtualAddress;
  IMAGE_IMPORT_DESCRIPTOR d;
  for (DWORD at = imports; imports && readAt(p, base + at, &d, sizeof(d)) && d.Name; at += sizeof(d)) {
    char dll[16] = {0};
    if (!readAt(p, base + d.Name, dll, sizeof(dll) - 1) || _stricmp(dll, "kernel32.dll") != 0)
      continue;
    DWORD names = d.OriginalFirstThunk ? d.OriginalFirstThunk : d.FirstThunk;
    for (DWORD i = 0;; i++) {
      DWORD thunk = 0;
      char name[32] = {0};
      if (!readAt(p, base + names + 4 * i, &thunk, 4) || !thunk)
        break;
      if (!(thunk & IMAGE_ORDINAL_FLAG32) && readAt(p, base + thunk + 2, name, sizeof(name) - 1) &&
          strcmp(name, function) == 0)
        return base + d.FirstThunk + 4 * i;
    }
  }
  return 0;
}

/* A new page below 2 GB, for 32-bit code; 0 for none */
static DWORD newPage(HANDLE p) {
  for (DWORD at = 0x10000000; at < 0x7FFF0000; at += 0x10000) {
    LPVOID mem = VirtualAllocEx(p, (LPVOID)(ULONG_PTR)at, 4096, MEM_RESERVE | MEM_COMMIT, PAGE_EXECUTE_READWRITE);
    if (mem && (ULONG_PTR)mem == at)
      return at;
    if (mem)
      VirtualFreeEx(p, mem, 0, MEM_RELEASE);
  }
  return 0;
}

/* Sets the pad up in a game started with CREATE_SUSPENDED; the page's address, and NULL or why it could not */
static const char *padSetUp(HANDLE p, DWORD *page) {
  BOOL wow64 = FALSE;
  if (!IsWow64Process(p, &wow64) || !wow64)
    return "not a 32-bit game";
  DWORD base = 0;
  for (int i = 0; i < 300 && !(base = imageBase(p)); i++)
    Sleep(10);
  IMAGE_DOS_HEADER dos;
  IMAGE_NT_HEADERS32 nt;
  if (!base || !readAt(p, base, &dos, sizeof(dos)) || !readAt(p, base + dos.e_lfanew, &nt, sizeof(nt)) ||
      nt.Signature != IMAGE_NT_SIGNATURE || nt.OptionalHeader.Magic != IMAGE_NT_OPTIONAL_HDR32_MAGIC)
    return "no 32-bit image";
  PadData data = {0};
  data.heapAllocSlot = importSlot(p, base, &nt, "HeapAlloc");
  data.getModuleHandleSlot = importSlot(p, base, &nt, "GetModuleHandleW");
  data.getProcAddressSlot = importSlot(p, base, &nt, "GetProcAddress");
  if (!data.heapAllocSlot || !data.getModuleHandleSlot || !data.getProcAddressSlot)
    return "no HeapAlloc, GetModuleHandleW or GetProcAddress import";
  DWORD tlsAt = nt.OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_TLS].VirtualAddress;
  IMAGE_TLS_DIRECTORY32 tls;
  if (!tlsAt || !readAt(p, base + tlsAt, &tls, sizeof(tls)))
    return "no TLS directory";
  DWORD callbacks[PAD_MAX_CALLBACKS + 2] = {0};
  for (int i = 0; tls.AddressOfCallBacks && i < PAD_MAX_CALLBACKS; i++)
    if (!readAt(p, tls.AddressOfCallBacks + 4 * i, &callbacks[i + 1], 4) || !callbacks[i + 1])
      break;
  if (!(*page = newPage(p)))
    return "no memory for the pad";
  callbacks[0] = *page + PAD_CALLBACK;
  wcscpy(data.kernel32, L"kernel32.dll");
  strcpy(data.virtualProtect, "VirtualProtect");
  DWORD pointer = *page + 0x14, list = *page + PAD_CALLBACKS;
  SIZE_T done = 0;
  if (!WriteProcessMemory(p, (LPVOID)(ULONG_PTR)*page, heap_pad_stub_bin, sizeof(heap_pad_stub_bin), &done) ||
      !WriteProcessMemory(p, (LPVOID)(ULONG_PTR)(*page + 0x0E), &pointer, 4, &done) ||
      !WriteProcessMemory(p, (LPVOID)(ULONG_PTR)(*page + PAD_DATA), &data, sizeof(data), &done) ||
      !WriteProcessMemory(p, (LPVOID)(ULONG_PTR)list, callbacks, sizeof(callbacks), &done))
    return "pad not written";
  FlushInstructionCache(p, (LPCVOID)(ULONG_PTR)*page, 4096);
  if (!writeAt(p, base + tlsAt + offsetof(IMAGE_TLS_DIRECTORY32, AddressOfCallBacks), &list, 4))
    return "TLS directory not writable";
  return NULL;
}

static HANDLE padProcess;

/* The callback's word on it, for Wine's log: it runs while the game's loader starts it */
static DWORD WINAPI padConfirm(LPVOID page) {
  DWORD state = 0;
  for (int i = 0; i < 1000 && state < 2; i++) {
    Sleep(10);
    if (!padProcess || !readAt(padProcess, (ULONG_PTR)page + PAD_DATA + offsetof(PadData, state), &state, 4))
      break;
  }
  fprintf(stderr, "agvn-winhandler: heap pad %s\n", state == 2 ? "on" : "set, not confirmed");
  return 0;
}

/* CreateProcessW of exe (args as one string, may be NULL) in dir, padded; FALSE when it could not start at all */
BOOL agvnStartPadded(LPCWSTR exe, LPCWSTR args, LPCWSTR dir, PROCESS_INFORMATION *pi) {
  static WCHAR path[MAX_PATH * 2], line[8192];
  size_t dirLen = dir ? wcslen(dir) : 0;
  if (dirLen && !wcschr(exe, L'\\') && !wcschr(exe, L'/'))
    _snwprintf(path, MAX_PATH * 2 - 1, L"%ls%ls%ls", dir, wcschr(L"\\/", dir[dirLen - 1]) ? L"" : L"\\", exe);
  else
    _snwprintf(path, MAX_PATH * 2 - 1, L"%ls", exe);
  _snwprintf(line, 8191, L"\"%ls\" %ls", path, args ? args : L"");
  STARTUPINFOW si = {sizeof(si)};
  if (!CreateProcessW(path, line, NULL, NULL, FALSE, CREATE_SUSPENDED, NULL, dir, &si, pi))
    return FALSE;
  DWORD page = 0;
  const char *failed = padSetUp(pi->hProcess, &page);
  padProcess = pi->hProcess;
  ResumeThread(pi->hThread);
  if (failed)
    fprintf(stderr, "agvn-winhandler: heap pad off: %s\n", failed);
  else
    CreateThread(NULL, 0, padConfirm, (LPVOID)(ULONG_PTR)page, 0, NULL);
  return TRUE;
}

# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
# The 32-bit code agvn-winhandler.exe /heappad writes into a game (app/src/main/cpp/agvn/heap-pad.c): at 0x00 the
# HeapAlloc thunk, at 0x40 a TLS callback that points the game's HeapAlloc import at it. The starter fills the data
# at 0x200 (heap-pad.c, PadData) and the thunk's jump at 0x0E. scripts/agvn/winhandler/build-winhandler.sh assembles
# it.
        .intel_syntax noprefix
        .text
        .set DATA, 0x200
page:
        # 0x00: HeapAlloc thunk: a HEAP_ZERO_MEMORY request asks two bytes more
        test byte ptr [esp+8], 8
        jz 1f
        add dword ptr [esp+12], 2
1:      .byte 0xFF, 0x25
        .long 0                 # absolute address of page+0x14, filled by the starter
        .long 0                 # 0x14: HeapAlloc as the loader found it, filled by the callback
        .balign 0x40, 0xCC
        # 0x40: TLS callback (stdcall: module, reason, reserved)
cb:
        cmp dword ptr [esp+8], 1
        jne 9f
        push ebx
        push esi
        call 2f
2:      pop ebx
        sub ebx, offset 2b - page
        cmp dword ptr [ebx+DATA+20], 0
        jne 8f
        mov dword ptr [ebx+DATA+20], 1
        lea eax, [ebx+DATA+32]
        push eax
        mov eax, [ebx+DATA+4]
        call dword ptr [eax]            # GetModuleHandleW(L"kernel32.dll")
        test eax, eax
        jz 8f
        lea ecx, [ebx+DATA+64]
        push ecx
        push eax
        mov eax, [ebx+DATA+8]
        call dword ptr [eax]            # GetProcAddress(kernel32, "VirtualProtect")
        test eax, eax
        jz 8f
        mov esi, eax
        lea eax, [ebx+DATA+24]
        push eax
        push 4                          # PAGE_READWRITE
        push 4
        push dword ptr [ebx+DATA]
        call esi
        test eax, eax
        jz 8f
        mov eax, [ebx+DATA]
        mov ecx, [eax]
        mov [ebx+0x14], ecx
        mov [eax], ebx                  # the game's HeapAlloc slot -> the thunk
        lea eax, [ebx+DATA+28]
        push eax
        push dword ptr [ebx+DATA+24]
        push 4
        push dword ptr [ebx+DATA]
        call esi
        mov dword ptr [ebx+DATA+20], 2  # done, for the starter's note
8:      pop esi
        pop ebx
9:      ret 12
end:

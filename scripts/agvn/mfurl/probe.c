/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
 * Opens each URL of its command line as a byte stream through Media Foundation's source resolver and prints the
 * result, as AVPro Video's Media Engine does for a movie's URL (run-test.sh). */
#define COBJMACROS
#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <stdio.h>
int wmain(int argc, wchar_t **argv) {
    CoInitializeEx(NULL, COINIT_MULTITHREADED);
    MFStartup(MF_VERSION, MFSTARTUP_FULL);
    IMFSourceResolver *r; MFCreateSourceResolver(&r);
    for (int i = 1; i < argc; i++) {
        MF_OBJECT_TYPE t; IUnknown *o = NULL;
        HRESULT hr = IMFSourceResolver_CreateObjectFromURL(r, argv[i], MF_RESOLUTION_BYTESTREAM, NULL, &t, &o);
        wprintf(L"%ls -> 0x%08lx\n", argv[i], hr);
        if (o) IUnknown_Release(o);
    }
    return 0;
}

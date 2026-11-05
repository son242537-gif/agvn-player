/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
/* Loads .js files as SRPG Studio's game.exe does (0x4749b0) and parses each with JScript (0x456480). */
#define COBJMACROS
#include <windows.h>
#include <activscp.h>
#include <stdio.h>

static const IID iid_script = {0xbb1a2ae1, 0xa4f9, 0x11cf, {0x8f, 0x20, 0x00, 0x80, 0x5f, 0x2c, 0xd0, 0x64}};
static const IID iid_parse = {0xbb1a2ae2, 0xa4f9, 0x11cf, {0x8f, 0x20, 0x00, 0x80, 0x5f, 0x2c, 0xd0, 0x64}};
static const IID iid_site = {0xdb01a1e3, 0xa42b, 0x11cf, {0x8f, 0x20, 0x00, 0x80, 0x5f, 0x2c, 0xd0, 0x64}};

static int errors, extra, keep;

static HRESULT WINAPI qi(IActiveScriptSite *s, REFIID r, void **p) {
    if (IsEqualIID(r, &IID_IUnknown) || IsEqualIID(r, &iid_site)) { *p = s; return S_OK; }
    *p = NULL; return E_NOINTERFACE;
}
static ULONG WINAPI ref(IActiveScriptSite *s) { return 1; }
static HRESULT WINAPI lcid(IActiveScriptSite *s, LCID *l) { return E_NOTIMPL; }
static HRESULT WINAPI item(IActiveScriptSite *s, LPCOLESTR n, DWORD m, IUnknown **u, ITypeInfo **t) {
    return TYPE_E_ELEMENTNOTFOUND;
}
static HRESULT WINAPI doc(IActiveScriptSite *s, BSTR *v) { return E_NOTIMPL; }
static HRESULT WINAPI term(IActiveScriptSite *s, const VARIANT *v, const EXCEPINFO *e) { return S_OK; }
static HRESULT WINAPI state(IActiveScriptSite *s, SCRIPTSTATE st) { return S_OK; }
static HRESULT WINAPI err(IActiveScriptSite *s, IActiveScriptError *e) {
    DWORD ctx = 0; ULONG line = 0; LONG ch = 0; BSTR text = NULL; EXCEPINFO ei = {0};
    IActiveScriptError_GetSourcePosition(e, &ctx, &line, &ch);
    IActiveScriptError_GetSourceLineText(e, &text);
    IActiveScriptError_GetExceptionInfo(e, &ei);
    wprintf(L"  file %lu: line %lu: %ls | %.60ls\n", ctx, line + 1, ei.bstrDescription ? ei.bstrDescription : L"?",
            text ? text : L"");
    errors++;
    return S_OK;
}
static HRESULT WINAPI enter(IActiveScriptSite *s) { return S_OK; }
static IActiveScriptSiteVtbl vt = {qi, ref, ref, lcid, item, doc, term, state, err, enter, enter};
static IActiveScriptSite site = {&vt};

/* game.exe 0x4749b0: size+1 zeroed bytes, the file, one 0 byte; UTF-16 text is used as it is */
static WCHAR *load(const WCHAR *path) {
    HANDLE h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return NULL;
    DWORD size = GetFileSize(h, NULL), n = size + 1, got = 0;
    BYTE *buf = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, n + extra);
    ReadFile(h, buf, n, &got, NULL);
    buf[n - 1] = 0;
    CloseHandle(h);
    if (!IsTextUnicode(buf, got, NULL)) wprintf(L"  not UTF-16: %ls\n", path);
    return (WCHAR *)buf;
}

int wmain(int argc, WCHAR **argv) {
    CLSID clsid; IActiveScript *script; IActiveScriptParse32 *parse; int first = 1;
    if (argc > 1 && !wcscmp(argv[1], L"--file")) { _wfreopen(L"probe-out.txt", L"w", stdout); first = 2; }
    if (argc > first && !wcscmp(argv[first], L"--plus2")) { extra = 1; first++; } /* two 0 bytes after the text */
    if (argc > first && !wcscmp(argv[first], L"--keep")) { keep = 1; first++; } /* buffers never freed */
    CoInitialize(NULL);
    CLSIDFromProgID(L"jScript", &clsid);
    if (FAILED(CoCreateInstance(&clsid, NULL, CLSCTX_INPROC_SERVER, &iid_script, (void **)&script))) return 2;
    IActiveScript_SetScriptSite(script, &site);
    IActiveScript_QueryInterface(script, &iid_parse, (void **)&parse);
    IActiveScriptParse32_InitNew(parse);
    for (int i = first; i < argc; i++) {
        WCHAR *code = load(argv[i]);
        if (!code) continue;
        IActiveScriptParse32_ParseScriptText(parse, code, NULL, NULL, NULL, i, 0, 0x42, NULL, NULL);
        if (!keep) HeapFree(GetProcessHeap(), 0, code);
    }
    wprintf(L"%d files, %d errors\n", argc - first, errors);
    fflush(stdout);
    return 0;
}

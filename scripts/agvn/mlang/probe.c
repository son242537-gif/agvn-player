/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
 *
 * probe.exe for scripts/agvn/mlang/run-test.sh: asks AGVN's mlang.dll (beside it, WINEDLLOVERRIDES=mlang=n,b) and
 * Wine's own (system folder) what GDI+'s font linking asks, and prints PASS or FAIL for each check.
 */
#define COBJMACROS
#include <windows.h>
#include <mlang.h>
#include <stdio.h>

static const GUID IID_Factory = {0x00000001, 0, 0, {0xc0, 0, 0, 0, 0, 0, 0, 0x46}};
static const GUID IID_FontLink = {0x359f3441, 0xbd4a, 0x11d0, {0xb1, 0x88, 0x00, 0xaa, 0x00, 0x38, 0xc9, 0x69}};
static const GUID CLSID_MultiLanguage = {0x275c23e2, 0x3747, 0x11d0, {0x9f, 0xea, 0x00, 0xaa, 0x00, 0x3f, 0x86, 0x46}};
static int failed;

static void check(const char *what, int ok) {
    printf("%s %s\n", ok ? "PASS" : "FAIL", what);
    if (!ok) failed = 1;
}

static IMLangFontLink *wine_own(void) {
    WCHAR path[MAX_PATH];
    GetSystemDirectoryW(path, MAX_PATH - 16);
    lstrcatW(path, L"\\mlang.dll");
    HMODULE m = LoadLibraryW(path);
    HRESULT (WINAPI *cls)(REFCLSID, REFIID, void **) = (void *)GetProcAddress(m, "DllGetClassObject");
    IClassFactory *f = NULL;
    IMLangFontLink *link = NULL;
    if (cls && SUCCEEDED(cls(&CLSID_MultiLanguage, &IID_Factory, (void **)&f)))
        IClassFactory_CreateInstance(f, NULL, &IID_FontLink, (void **)&link);
    return link;
}

int main(void) {
    WCHAR name[MAX_PATH], exe[MAX_PATH];
    HMODULE m = LoadLibraryW(L"mlang.dll");
    GetModuleFileNameW(m, name, MAX_PATH);
    GetModuleFileNameW(NULL, exe, MAX_PATH);
    *wcsrchr(exe, L'\\') = 0;
    check("mlang.dll is AGVN's, beside the exe", m && _wcsnicmp(name, exe, wcslen(exe)) == 0);
    HRESULT (WINAPI *get)(void **) = (void *)GetProcAddress(m, "GetGlobalFontLinkObject");
    IMLangFontLink *link = NULL, *own = wine_own();
    check("GetGlobalFontLinkObject gives an object", get && SUCCEEDED(get((void **)&link)) && link);
    check("Wine's own object for comparison", own != NULL);
    if (!link || !own) return 1;
    DWORD font = FS_LATIN1 | FS_LATIN2 | FS_VIETNAMESE, cps = 0, w = 0;
    LONG done = 0, wd = 0;
    IMLangFontLink_GetCharCodePages(link, 0x1EAF, &cps);
    check("U+1EAF (ắ) is in no code page", cps == 0);
    IMLangFontLink_GetStrCodePages(link, L"\x1EAF", 1, font, &cps, &done);
    check("\"ắ\" alone keeps the game's font", (cps & font) && done == 1);
    IMLangFontLink_GetStrCodePages(link, L"\x1EAF\x1EC7", 2, font, &cps, &done);
    check("\"ắệ\" is one run with the game's font", (cps & font) && done == 2);
    IMLangFontLink_GetStrCodePages(link, L"\x1EAF\x1EC7", 2, 0, &cps, &done);
    check("\"ắệ\" for a font of no code page: a code page MapFont can map", cps == FS_LATIN1 && done == 2);
    IMLangFontLink_GetStrCodePages(link, L"\x65e5\x672c", 2, font, &cps, &done);
    IMLangFontLink_GetStrCodePages(own, L"\x65e5\x672c", 2, font, &w, &wd);
    check("\"日本\" gets Japanese code pages, as Wine's does", (cps & FS_JISJAPAN) && !(cps & font) && cps == w && done == wd);
    IMLangFontLink_GetStrCodePages(link, L"\x65e5\x1EAF", 2, font, &cps, &done);
    check("\"日ắ\" ends the Japanese run before ắ", (cps & FS_JISJAPAN) && done == 1);
    IMLangFontLink_GetStrCodePages(link, L"ab", 2, font, &cps, &done);
    IMLangFontLink_GetStrCodePages(own, L"ab", 2, font, &w, &wd);
    check("\"ab\" as Wine's", cps == w && done == wd);
    HDC dc = CreateCompatibleDC(NULL);
    HFONT f = CreateFontW(-16, 0, 0, 0, FW_NORMAL, 0, 0, 0, DEFAULT_CHARSET, 0, 0, 0, 0, L"Tahoma");
    HGDIOBJ old = SelectObject(dc, f);
    HFONT mapped = NULL;
    HRESULT hr = IMLangFontLink_MapFont(own, dc, 0, f, &mapped);
    check("Wine's MapFont fails for no code page (GDI+ then reads a NULL font)", FAILED(hr));
    mapped = NULL;
    hr = IMLangFontLink_MapFont(link, dc, 0, f, &mapped);
    check("AGVN's MapFont gives back the game's font", SUCCEEDED(hr) && mapped == f);
    IMLangFontLink_ReleaseFont(link, mapped);
    check("the game's font is still there after ReleaseFont", GetObjectType(f) == OBJ_FONT);
    mapped = NULL;
    hr = IMLangFontLink_MapFont(link, dc, FS_JISJAPAN, f, &mapped);
    check("MapFont still maps a Japanese font", SUCCEEDED(hr) && mapped && mapped != f);
    if (mapped) IMLangFontLink_ReleaseFont(link, mapped);
    IMLangFontLink_GetFontCodePages(link, dc, f, &cps);
    IMLangFontLink_GetFontCodePages(own, dc, f, &w);
    check("a font's code pages as Wine's (or all, when Wine names none)", cps == (w ? w : ~0u));
    SelectObject(dc, old);
    WCHAR tag[16] = {0};
    HRESULT (WINAPI *lcid)(LCID, LPWSTR, int) = (void *)GetProcAddress(m, "LcidToRfc1766W");
    check("LcidToRfc1766W goes to Wine's", lcid && SUCCEEDED(lcid(0x042a, tag, 16)) && _wcsnicmp(tag, L"vi", 2) == 0);
    check("ordinal 121 is LcidToRfc1766W", GetProcAddress(m, (LPCSTR)121) == (FARPROC)lcid);
    IMLangFontLink_Release(link);
    printf(failed ? "FAILED\n" : "ALL PASSED\n");
    return failed;
}

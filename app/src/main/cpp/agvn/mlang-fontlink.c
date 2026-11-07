/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
 *
 * agvn-mlang32.dll / agvn-mlang64.dll, put beside a game's exe as mlang.dll by "Tự sửa lỗi" (AgvnMlangFix): Wine's own
 * mlang.dll with the font linking of GDI+ (System.Drawing of .NET) fixed. Proton 9's gdiplus asks mlang which code
 * pages a run of text has and maps a font for it when the game's font lacks them; it checks neither MapFont nor the
 * font it makes. A Vietnamese letter of its own (U+1EA0-U+1EF9: "ắ", "ệ") is in no code page, so a string that is one
 * such letter, or ends with one, gets no code page at all, MapFont fails, and GDI+ reads a NULL font: the game ends
 * with 0xC0000005 at its first Vietnamese menu (a .NET game, POCO F8 Pro, 07/11/2026). Wine 10 links a font only for
 * a glyph the font lacks. Here:
 *  - a run of letters no code page has keeps the game's font (no font of a code page shows them better);
 *  - a letter's code pages are those that really hold it (Wine's check read a flag the call did not always set);
 *  - MapFont that finds nothing gives back the game's font instead of nothing;
 *  - a font that names no code page holds them all, so its text is drawn with it, not with another font.
 * Text of a code page the font lacks (Japanese in a Latin font) still gets a font that has it. Everything else is
 * Wine's mlang.dll, loaded from the system folder. Built by scripts/agvn/mlang/build-mlang.sh, tested in Wine by
 * scripts/agvn/mlang/run-test.sh.
 */
#define COBJMACROS
#include <windows.h>
#include <mlang.h>

static const GUID IID_CodePages = {0x359f3443, 0xbd4a, 0x11d0, {0xb1, 0x88, 0x00, 0xaa, 0x00, 0x38, 0xc9, 0x69}};
static const GUID IID_FontLink = {0x359f3441, 0xbd4a, 0x11d0, {0xb1, 0x88, 0x00, 0xaa, 0x00, 0x38, 0xc9, 0x69}};
static const GUID IID_Unknown = {0x00000000, 0x0000, 0x0000, {0xc0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46}};
static const GUID IID_Factory = {0x00000001, 0x0000, 0x0000, {0xc0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46}};
static const GUID CLSID_MultiLanguage = {0x275c23e2, 0x3747, 0x11d0, {0x9f, 0xea, 0x00, 0xaa, 0x00, 0x3f, 0x86, 0x46}};
/* Wine's mlang_data family code pages, Unicode left out */
static const UINT FAMILIES[] = {1256, 1257, 936, 950, 1250, 1251, 1253, 1255, 932, 949, 874, 1254, 1258, 1252};

static HMODULE self, wine;
static INIT_ONCE once = INIT_ONCE_STATIC_INIT;

static BOOL CALLBACK load_wine(PINIT_ONCE o, PVOID p, PVOID *c) {
    WCHAR path[MAX_PATH];
    UINT n = GetSystemDirectoryW(path, MAX_PATH - 16);
    if (n == 0 || n >= MAX_PATH - 16) return TRUE;
    lstrcpyW(path + n, L"\\mlang.dll");
    wine = LoadLibraryW(path);
    if (wine == self) wine = NULL; /* never this file again */
    return TRUE;
}

static FARPROC wine_proc(const char *name) {
    InitOnceExecuteOnce(&once, load_wine, NULL, NULL);
    return wine ? GetProcAddress(wine, name) : NULL;
}

#define FORWARD(name, params, args) \
    HRESULT WINAPI name params { \
        typedef HRESULT (WINAPI *fn_t) params; \
        fn_t fn = (fn_t)wine_proc(#name); \
        return fn ? fn args : E_FAIL; \
    }

FORWARD(IsConvertINetStringAvailable, (DWORD src, DWORD dst), (src, dst))
FORWARD(ConvertINetString, (LPDWORD mode, DWORD src, DWORD dst, LPCSTR in, LPINT in_n, LPSTR out, LPINT out_n),
        (mode, src, dst, in, in_n, out, out_n))
FORWARD(ConvertINetUnicodeToMultiByte, (LPDWORD mode, DWORD enc, LPCWSTR in, LPINT in_n, LPSTR out, LPINT out_n),
        (mode, enc, in, in_n, out, out_n))
FORWARD(ConvertINetMultiByteToUnicode, (LPDWORD mode, DWORD enc, LPCSTR in, LPINT in_n, LPWSTR out, LPINT out_n),
        (mode, enc, in, in_n, out, out_n))
FORWARD(LcidToRfc1766A, (LCID lcid, LPSTR out, int n), (lcid, out, n))
FORWARD(LcidToRfc1766W, (LCID lcid, LPWSTR out, int n), (lcid, out, n))
FORWARD(Rfc1766ToLcidA, (LCID *lcid, LPCSTR in), (lcid, in))
FORWARD(Rfc1766ToLcidW, (LCID *lcid, LPCWSTR in), (lcid, in))
FORWARD(DllGetClassObject, (REFCLSID clsid, REFIID iid, LPVOID *out), (clsid, iid, out))
FORWARD(DllRegisterServer, (void), ())
FORWARD(DllUnregisterServer, (void), ())

HRESULT WINAPI ConvertINetReset(void) { return S_OK; }
HRESULT WINAPI DllCanUnloadNow(void) { return S_FALSE; }

typedef struct { IMLangFontLink iface; LONG ref; IMLangFontLink *wine; } Link;
static Link *impl(IMLangFontLink *i) { return (Link *)i; }

static HRESULT WINAPI link_qi(IMLangFontLink *i, REFIID iid, void **out) {
    if (IsEqualGUID(iid, &IID_Unknown) || IsEqualGUID(iid, &IID_CodePages) || IsEqualGUID(iid, &IID_FontLink)) {
        *out = i;
        InterlockedIncrement(&impl(i)->ref);
        return S_OK;
    }
    return IMLangFontLink_QueryInterface(impl(i)->wine, iid, out);
}
static ULONG WINAPI link_addref(IMLangFontLink *i) { return InterlockedIncrement(&impl(i)->ref); }
static ULONG WINAPI link_release(IMLangFontLink *i) {
    Link *l = impl(i);
    ULONG ref = InterlockedDecrement(&l->ref);
    if (!ref) {
        IMLangFontLink_Release(l->wine);
        HeapFree(GetProcessHeap(), 0, l);
    }
    return ref;
}

/* The code pages that hold ch: each family code page that writes it without its default character */
static DWORD char_cps(Link *l, WCHAR ch) {
    DWORD all = 0;
    for (int i = 0; i < (int)(sizeof(FAMILIES) / sizeof(FAMILIES[0])); i++) {
        BOOL used = TRUE;
        char buf[8];
        int n = WideCharToMultiByte(FAMILIES[i], WC_NO_BEST_FIT_CHARS, &ch, 1, buf, sizeof(buf), NULL, &used);
        DWORD cps = 0;
        if (n > 0 && !used && SUCCEEDED(IMLangFontLink_CodePageToCodePages(l->wine, FAMILIES[i], &cps))) all |= cps;
    }
    return all;
}

static HRESULT WINAPI link_char(IMLangFontLink *i, WCHAR ch, DWORD *cps) {
    if (!cps) return E_INVALIDARG;
    *cps = char_cps(impl(i), ch);
    return S_OK;
}

/* Wine's run of letters with code pages in common, which never ends up with none */
static HRESULT WINAPI link_str(IMLangFontLink *i, const WCHAR *src, LONG len, DWORD prio, DWORD *out, LONG *done) {
    Link *l = impl(i);
    LONG n = 1;
    if (out) *out = 0;
    if (done) *done = 0;
    if (!src || len <= 0) return E_INVALIDARG;
    DWORD cps = char_cps(l, src[0]);
    if (!cps) {
        while (n < len && !char_cps(l, src[n])) n++;
        cps = prio ? prio : FS_LATIN1;
    } else {
        for (; n < len; n++) {
            DWORD cp = char_cps(l, src[n]);
            if (!(cps & cp) || ((prio & cps) ^ (prio & cp))) break;
            cps &= cp;
        }
    }
    if (out) *out = cps;
    if (done) *done = n;
    return S_OK;
}

static HRESULT WINAPI link_cp_to_cps(IMLangFontLink *i, UINT cp, DWORD *cps) {
    return IMLangFontLink_CodePageToCodePages(impl(i)->wine, cp, cps);
}
static HRESULT WINAPI link_cps_to_cp(IMLangFontLink *i, DWORD cps, UINT def, UINT *cp) {
    return IMLangFontLink_CodePagesToCodePage(impl(i)->wine, cps, def, cp);
}
static HRESULT WINAPI link_font_cps(IMLangFontLink *i, HDC dc, HFONT font, DWORD *cps) {
    HRESULT hr = IMLangFontLink_GetFontCodePages(impl(i)->wine, dc, font, cps);
    if (SUCCEEDED(hr) && cps && !*cps) *cps = ~0u;
    return hr;
}
static HRESULT WINAPI link_map(IMLangFontLink *i, HDC dc, DWORD cps, HFONT src, HFONT *dst) {
    HRESULT hr = IMLangFontLink_MapFont(impl(i)->wine, dc, cps, src, dst);
    if (FAILED(hr) && src && dst) {
        *dst = src; /* GDI+ selects what it gets unchecked; ReleaseFont leaves a font Wine did not make */
        hr = S_OK;
    }
    return hr;
}
static HRESULT WINAPI link_release_font(IMLangFontLink *i, HFONT font) {
    return IMLangFontLink_ReleaseFont(impl(i)->wine, font);
}
static HRESULT WINAPI link_reset(IMLangFontLink *i) { return IMLangFontLink_ResetFontMapping(impl(i)->wine); }

static IMLangFontLinkVtbl vtbl = {link_qi, link_addref, link_release, link_char, link_str, link_cp_to_cps,
                                  link_cps_to_cp, link_font_cps, link_map, link_release_font, link_reset};

/* Wine's object: its global one, or a new one where GetGlobalFontLinkObject is a stub (Wine 9.0) */
static IMLangFontLink *wine_link(void) {
    typedef HRESULT (WINAPI *get_t)(void **);
    typedef HRESULT (WINAPI *class_t)(REFCLSID, REFIID, void **);
    get_t get = (get_t)wine_proc("GetGlobalFontLinkObject");
    class_t cls = (class_t)wine_proc("DllGetClassObject");
    IMLangFontLink *link = NULL;
    IClassFactory *factory = NULL;
    if (get && SUCCEEDED(get((void **)&link)) && link) return link;
    link = NULL;
    if (!cls || FAILED(cls(&CLSID_MultiLanguage, &IID_Factory, (void **)&factory)) || !factory) return NULL;
    if (FAILED(IClassFactory_CreateInstance(factory, NULL, &IID_FontLink, (void **)&link))) link = NULL;
    IClassFactory_Release(factory);
    return link;
}

HRESULT WINAPI GetGlobalFontLinkObject(void **obj) {
    if (!obj) return E_INVALIDARG;
    *obj = NULL;
    IMLangFontLink *link = wine_link();
    if (!link) return E_FAIL;
    Link *l = HeapAlloc(GetProcessHeap(), 0, sizeof(*l));
    if (!l) {
        IMLangFontLink_Release(link);
        return E_OUTOFMEMORY;
    }
    l->iface.lpVtbl = &vtbl;
    l->ref = 1;
    l->wine = link;
    *obj = &l->iface;
    return S_OK;
}

BOOL WINAPI DllMain(HINSTANCE inst, DWORD reason, LPVOID reserved) {
    if (reason == DLL_PROCESS_ATTACH) {
        self = inst;
        DisableThreadLibraryCalls(inst);
    }
    return TRUE;
}

/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
 *
 * agvn-mfurl.dll: Media Foundation's handler for "file:" URLs, put before Wine's own. Wine's handler (mfplat, Proton 9
 * and Wine 9.0) drops "file://" and opens the rest, so "file:///F:/AGVN/A-0.mp4", the URL Windows itself writes, becomes
 * "/F:/AGVN/A-0.mp4" and MFCreateFile fails with 0x80070003. AVPro Video's Media Engine then reports "Failed to create
 * source object" and the game waits on "buffering" for a movie of any format (NZGY, POCO F8 Pro, 30/10/2026).
 *
 * This handler turns such a URL ("file:///X:...", '%20' and the like included) into the path Windows reads in it
 * (PathCreateFromUrlW: "F:\AGVN\A-0.mp4") and hands it to Wine's handler; every other URL goes there unchanged. It is
 * registered in HKCU, which Wine's source resolver reads before HKLM (AgvnMfFileUrl). Built by
 * scripts/agvn/mfurl/build-mfurl.sh, tested in Wine by scripts/agvn/mfurl/run-test.sh.
 */
#define COBJMACROS
#include <windows.h>
#include <objbase.h>
#include <propsys.h>
#include <mfobjects.h>
#include <shlwapi.h>
#include <wctype.h>

/* IMFSchemeHandler (mfidl.h of the Windows SDK; mingw-w64 lacks it). */
typedef struct IMFSchemeHandler IMFSchemeHandler;
typedef struct {
    HRESULT (STDMETHODCALLTYPE *QueryInterface)(IMFSchemeHandler *, REFIID, void **);
    ULONG (STDMETHODCALLTYPE *AddRef)(IMFSchemeHandler *);
    ULONG (STDMETHODCALLTYPE *Release)(IMFSchemeHandler *);
    HRESULT (STDMETHODCALLTYPE *BeginCreateObject)(IMFSchemeHandler *, LPCWSTR, DWORD, IPropertyStore *, IUnknown **,
                                                   IMFAsyncCallback *, IUnknown *);
    HRESULT (STDMETHODCALLTYPE *EndCreateObject)(IMFSchemeHandler *, IMFAsyncResult *, DWORD *, IUnknown **);
    HRESULT (STDMETHODCALLTYPE *CancelObjectCreation)(IMFSchemeHandler *, IUnknown *);
} IMFSchemeHandlerVtbl;
struct IMFSchemeHandler { const IMFSchemeHandlerVtbl *lpVtbl; };

static const GUID IID_SchemeHandler = {0x6d4c7b74, 0x52a0, 0x4bb7, {0xb0, 0xdb, 0x55, 0xf2, 0x9f, 0x47, 0xa6, 0x68}};
/* Wine's (and Windows') CLSID_FileSchemePlugin. */
static const GUID CLSID_WineFileScheme = {0x477ec299, 0x1421, 0x4bdd, {0x97, 0x1f, 0x7c, 0xcb, 0x93, 0x3f, 0x21, 0xad}};
/* {D241A047-AB6F-43EF-8C31-78A96B9D1EA2}, AgvnMfFileUrl.CLSID */
static const GUID CLSID_AgvnFileScheme = {0xd241a047, 0xab6f, 0x43ef, {0x8c, 0x31, 0x78, 0xa9, 0x6b, 0x9d, 0x1e, 0xa2}};

static LONG locks;

struct handler {
    IMFSchemeHandler iface;
    LONG refcount;
    IMFSchemeHandler *inner;
};

static struct handler *impl(IMFSchemeHandler *iface) { return (struct handler *)iface; }

static HRESULT STDMETHODCALLTYPE h_QueryInterface(IMFSchemeHandler *iface, REFIID riid, void **out) {
    if (IsEqualIID(riid, &IID_SchemeHandler) || IsEqualIID(riid, &IID_IUnknown)) {
        *out = iface;
        iface->lpVtbl->AddRef(iface);
        return S_OK;
    }
    *out = NULL;
    return E_NOINTERFACE;
}

static ULONG STDMETHODCALLTYPE h_AddRef(IMFSchemeHandler *iface) { return InterlockedIncrement(&impl(iface)->refcount); }

static ULONG STDMETHODCALLTYPE h_Release(IMFSchemeHandler *iface) {
    struct handler *h = impl(iface);
    ULONG ref = InterlockedDecrement(&h->refcount);
    if (!ref) {
        h->inner->lpVtbl->Release(h->inner);
        HeapFree(GetProcessHeap(), 0, h);
        InterlockedDecrement(&locks);
    }
    return ref;
}

/* "file:///X:" or "file:///X|", a local file as Windows writes its URL. */
static BOOL drive_url(LPCWSTR url) {
    return url && !_wcsnicmp(url, L"file:///", 8) && iswalpha(url[8]) && (url[9] == ':' || url[9] == '|');
}

static HRESULT STDMETHODCALLTYPE h_BeginCreateObject(IMFSchemeHandler *iface, LPCWSTR url, DWORD flags,
        IPropertyStore *props, IUnknown **cancel, IMFAsyncCallback *callback, IUnknown *state) {
    IMFSchemeHandler *inner = impl(iface)->inner;
    WCHAR path[MAX_PATH * 4];
    DWORD len = ARRAYSIZE(path);
    if (drive_url(url) && SUCCEEDED(PathCreateFromUrlW(url, path, &len, 0)))
        return inner->lpVtbl->BeginCreateObject(inner, path, flags, props, cancel, callback, state);
    return inner->lpVtbl->BeginCreateObject(inner, url, flags, props, cancel, callback, state);
}

static HRESULT STDMETHODCALLTYPE h_EndCreateObject(IMFSchemeHandler *iface, IMFAsyncResult *result, DWORD *type,
        IUnknown **object) {
    IMFSchemeHandler *inner = impl(iface)->inner;
    return inner->lpVtbl->EndCreateObject(inner, result, type, object);
}

static HRESULT STDMETHODCALLTYPE h_CancelObjectCreation(IMFSchemeHandler *iface, IUnknown *cancel) {
    IMFSchemeHandler *inner = impl(iface)->inner;
    return inner->lpVtbl->CancelObjectCreation(inner, cancel);
}

static const IMFSchemeHandlerVtbl handler_vtbl = {
    h_QueryInterface, h_AddRef, h_Release, h_BeginCreateObject, h_EndCreateObject, h_CancelObjectCreation,
};

static HRESULT STDMETHODCALLTYPE f_QueryInterface(IClassFactory *iface, REFIID riid, void **out) {
    if (IsEqualIID(riid, &IID_IClassFactory) || IsEqualIID(riid, &IID_IUnknown)) {
        *out = iface;
        return S_OK;
    }
    *out = NULL;
    return E_NOINTERFACE;
}

static ULONG STDMETHODCALLTYPE f_AddRef(IClassFactory *iface) { return 2; }
static ULONG STDMETHODCALLTYPE f_Release(IClassFactory *iface) { return 1; }

static HRESULT STDMETHODCALLTYPE f_CreateInstance(IClassFactory *iface, IUnknown *outer, REFIID riid, void **out) {
    struct handler *h;
    HRESULT hr;
    *out = NULL;
    if (outer) return CLASS_E_NOAGGREGATION;
    if (!(h = HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, sizeof(*h)))) return E_OUTOFMEMORY;
    hr = CoCreateInstance(&CLSID_WineFileScheme, NULL, CLSCTX_INPROC_SERVER, &IID_SchemeHandler, (void **)&h->inner);
    if (FAILED(hr)) {
        HeapFree(GetProcessHeap(), 0, h);
        return hr;
    }
    h->iface.lpVtbl = &handler_vtbl;
    h->refcount = 1;
    InterlockedIncrement(&locks);
    hr = h_QueryInterface(&h->iface, riid, out);
    h_Release(&h->iface);
    return hr;
}

static HRESULT STDMETHODCALLTYPE f_LockServer(IClassFactory *iface, BOOL lock) {
    if (lock) InterlockedIncrement(&locks);
    else InterlockedDecrement(&locks);
    return S_OK;
}

static IClassFactoryVtbl factory_vtbl = {f_QueryInterface, f_AddRef, f_Release, f_CreateInstance, f_LockServer};
static IClassFactory factory = {&factory_vtbl};

HRESULT WINAPI DllGetClassObject(REFCLSID clsid, REFIID riid, void **out) {
    if (!IsEqualCLSID(clsid, &CLSID_AgvnFileScheme)) {
        *out = NULL;
        return CLASS_E_CLASSNOTAVAILABLE;
    }
    return f_QueryInterface(&factory, riid, out);
}

HRESULT WINAPI DllCanUnloadNow(void) { return locks ? S_FALSE : S_OK; }

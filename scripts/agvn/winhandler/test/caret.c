/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
/* Test game for agvn-winhandler.exe 6's caret rule: a caret in its own window, as KiriKiri, Unity and GameMaker put
   one to place the IME's box with no field to type in; then a text field of Windows (an EDIT control) with the focus;
   then the focus back on its window, which puts its own caret there again. */
#include <windows.h>
#include <stdio.h>
static FILE *out;
static void phase(const char *name) { fprintf(out, "phase %s @%.1f\n", name, GetTickCount() / 1000.0); fflush(out); }
int WINAPI WinMain(HINSTANCE inst, HINSTANCE p, LPSTR c, int n) {
  out = fopen("caret.log", "w");
  WNDCLASSW wc = {0}; wc.lpfnWndProc = DefWindowProcW; wc.hInstance = inst; wc.lpszClassName = L"TVPMainWindow";
  RegisterClassW(&wc);
  HWND hwnd = CreateWindowW(L"TVPMainWindow", L"caret", WS_OVERLAPPEDWINDOW | WS_VISIBLE, 0, 0, 640, 480,
                            NULL, NULL, inst, NULL);
  SetForegroundWindow(hwnd); SetFocus(hwnd);
  CreateCaret(hwnd, NULL, 1, 16); SetCaretPos(20, 400); ShowCaret(hwnd);
  phase("own-window-caret");
  DWORD start = GetTickCount();
  int step = 0;
  HWND edit = NULL;
  MSG msg;
  while (1) {
    while (PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE)) { TranslateMessage(&msg); DispatchMessageW(&msg); }
    DWORD t = GetTickCount() - start;
    if (step == 0 && t > 3000) {
      edit = CreateWindowW(L"EDIT", L"", WS_CHILD | WS_VISIBLE | WS_BORDER, 10, 10, 200, 24, hwnd, NULL, inst, NULL);
      SetFocus(edit); phase("edit-field"); step++;
    } else if (step == 1 && t > 6000) {
      SetFocus(hwnd); CreateCaret(hwnd, NULL, 1, 16); SetCaretPos(20, 400); ShowCaret(hwnd);
      phase("own-window-caret-again"); step++;
    } else if (step == 2 && t > 9000) { fclose(out); return 0; }
    Sleep(10);
  }
}

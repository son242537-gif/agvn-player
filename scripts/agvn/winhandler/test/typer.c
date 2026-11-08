/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
/* Test game for AGVN_STATE_TEXT_INPUT: turns its text input off and on as SDL does (SDL_StopTextInput and
   SDL_StartTextInput give and take its window's input context), then focuses a child window that has the thread's
   input context from the start, as a dialog box does. */
#include <windows.h>
#include <imm.h>
#include <stdio.h>
static FILE *out;
static void phase(const char *name) { fprintf(out, "phase %s\n", name); fflush(out); }
int WINAPI WinMain(HINSTANCE inst, HINSTANCE p, LPSTR c, int n) {
  out = fopen("typer.log", "w");
  WNDCLASSW wc = {0}; wc.lpfnWndProc = DefWindowProcW; wc.hInstance = inst; wc.lpszClassName = L"typer";
  RegisterClassW(&wc);
  HWND hwnd = CreateWindowW(L"typer", L"typer", WS_OVERLAPPEDWINDOW | WS_VISIBLE, 0, 0, 640, 480, NULL, NULL, inst, NULL);
  SetForegroundWindow(hwnd); SetFocus(hwnd);
  HIMC himc = ImmGetContext(hwnd); ImmReleaseContext(hwnd, himc);
  DWORD start = GetTickCount();
  int step = 0;
  HWND other = NULL;
  MSG msg;
  phase("on-from-start");
  while (1) {
    while (PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE)) { TranslateMessage(&msg); DispatchMessageW(&msg); }
    DWORD t = GetTickCount() - start;
    if (step == 0 && t > 2000) { ImmAssociateContext(hwnd, NULL); phase("off"); step++; }
    else if (step == 1 && t > 4500) { ImmAssociateContext(hwnd, himc); phase("on"); step++; }
    else if (step == 2 && t > 7000) { ImmAssociateContext(hwnd, NULL); phase("off"); step++; }
    else if (step == 3 && t > 7300) { ImmAssociateContext(hwnd, himc); phase("on-blink"); step++; }
    else if (step == 4 && t > 7400) { ImmAssociateContext(hwnd, NULL); phase("off"); step++; }
    else if (step == 5 && t > 9500) {
      other = CreateWindowW(L"typer", L"", WS_CHILD | WS_VISIBLE, 10, 10, 100, 40, hwnd, NULL, inst, NULL);
      SetFocus(other); phase("other-window"); step++;
    } else if (step == 6 && t > 12000) { fclose(out); return 0; }
    Sleep(10);
  }
}

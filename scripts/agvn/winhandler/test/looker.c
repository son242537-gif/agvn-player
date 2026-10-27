/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
/* Test game: hides and clips the cursor like Unity's CursorLockMode.Locked, logs raw mouse deltas, then shows an edit box,
   then holds the cursor in ways that are not a lock. */
#include <windows.h>
#include <stdio.h>
static FILE *out;
static HWND edit;
static LRESULT CALLBACK proc(HWND h, UINT m, WPARAM w, LPARAM l) {
  if (m == WM_INPUT) {
    RAWINPUT ri; UINT size = sizeof(ri);
    if (GetRawInputData((HRAWINPUT)l, RID_INPUT, &ri, &size, sizeof(RAWINPUTHEADER)) != (UINT)-1 && ri.header.dwType == RIM_TYPEMOUSE) {
      fprintf(out, "raw %ld %ld flags %u\n", ri.data.mouse.lLastX, ri.data.mouse.lLastY, ri.data.mouse.usFlags); fflush(out);
    }
  }
  if (m == WM_DESTROY) PostQuitMessage(0);
  return DefWindowProcW(h, m, w, l);
}
int WINAPI WinMain(HINSTANCE inst, HINSTANCE p, LPSTR c, int n) {
  out = fopen("looker.log", "w");
  WNDCLASSW wc = {0}; wc.lpfnWndProc = proc; wc.hInstance = inst; wc.lpszClassName = L"looker"; wc.hCursor = LoadCursor(NULL, IDC_ARROW);
  RegisterClassW(&wc);
  HWND hwnd = CreateWindowW(L"looker", L"looker", WS_OVERLAPPEDWINDOW | WS_VISIBLE, 0, 0, 640, 480, NULL, NULL, inst, NULL);
  RAWINPUTDEVICE rid = {1, 2, RIDEV_INPUTSINK, hwnd};
  RegisterRawInputDevices(&rid, 1, sizeof(rid));
  SetForegroundWindow(hwnd);
  DWORD start = GetTickCount();
  int phase = 0;
  MSG msg;
  while (1) {
    while (PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE)) { if (msg.message == WM_QUIT) return 0; TranslateMessage(&msg); DispatchMessageW(&msg); }
    DWORD t = GetTickCount() - start;
    if (phase == 0 && t > 1500) { /* lock: hidden + clipped to the centre point */
      ShowCursor(FALSE); POINT c = {320, 240}; ClientToScreen(hwnd, &c); RECT r = {c.x, c.y, c.x + 1, c.y + 1}; ClipCursor(&r);
      fprintf(out, "phase lock\n"); fflush(out); phase = 1;
    } else if (phase == 1 && t > 5000) {
      ClipCursor(NULL); ShowCursor(TRUE); fprintf(out, "phase free\n"); fflush(out); phase = 2;
    } else if (phase == 2 && t > 6500) {
      edit = CreateWindowW(L"EDIT", L"", WS_CHILD | WS_VISIBLE | WS_BORDER, 10, 10, 200, 24, hwnd, NULL, inst, NULL);
      SetFocus(edit); fprintf(out, "phase edit\n"); fflush(out); phase = 3;
    } else if (phase == 3 && t > 9000) {
      DestroyWindow(edit); SetFocus(hwnd); fprintf(out, "phase noedit\n"); fflush(out); phase = 4;
    } else if (phase == 4 && t > 10500) { /* a game drawing its own cursor: hidden, not clipped */
      ShowCursor(FALSE); fprintf(out, "phase hidden-only\n"); fflush(out); phase = 5;
    } else if (phase == 5 && t > 12000) { /* confined to the window, cursor shown */
      ShowCursor(TRUE); RECT r; GetWindowRect(hwnd, &r); ClipCursor(&r); fprintf(out, "phase clip-only\n"); fflush(out); phase = 6;
    } else if (phase == 6 && t > 13500) { /* a game drawing its own cursor, confined to its window (Unity's Confined) */
      ShowCursor(FALSE); RECT r; GetClientRect(hwnd, &r); MapWindowPoints(hwnd, NULL, (POINT *)&r, 2); ClipCursor(&r);
      fprintf(out, "phase hidden-confined\n"); fflush(out); phase = 7;
    } else if (phase == 7 && t > 15000) { ClipCursor(NULL); fclose(out); return 0; }
    Sleep(10);
  }
}

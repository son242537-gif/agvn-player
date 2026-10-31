
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0600
#endif

#include <psapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <tlhelp32.h>
#include <windows.h>
#include <winsock2.h>


#define SERVER_PORT 7946
#define CLIENT_PORT 7947
#define BUFFER_SIZE 64

#define RC_EXIT 0
#define RC_INIT 1
#define RC_EXEC 2
#define RC_KILL_PROCESS 3
#define RC_LIST_PROCESSES 4
#define RC_GET_PROCESS 5
#define RC_SET_PROCESS_AFFINITY 6
#define RC_MOUSE_EVENT 7
#define RC_GET_GAMEPAD                                                         \
  8
#define RC_GET_GAMEPAD_STATE                                                   \
  9
#define RC_RELEASE_GAMEPAD                                                     \
  10
#define RC_KEYBOARD_EVENT 11
#define RC_BRING_TO_FRONT 12
#define RC_CURSOR_POS_FEEDBACK 13
/* AGVN: the mouse at a pixel of the screen, with its buttons; and this winhandler's version, sent after RC_INIT */
#define RC_AGVN_POINTER 14
/* AGVN 2: what the game asks of the mouse and the keyboard, sent to the app
   when it changes and every second: bit 0, the cursor is hidden and held in a
   part of the screen (a camera that turns with the mouse); bit 1, a window
   shows a text caret (a field that takes typing) */
#define RC_AGVN_STATE 15
#define AGVN_STATE_MOUSE_LOCKED 1
#define AGVN_STATE_TEXT_CARET 2
/* AGVN 2: in RC_AGVN_POINTER's flags, (x, y) is a move by that much, not a pixel */
#define AGVN_POINTER_RELATIVE 0x40000000
/* AGVN 3: the game and every process it started have ended, with the game's
   exit code (DWORD) after the code; this winhandler then ends, and Wine with it */
#define RC_AGVN_GAME_ENDED 16
#define AGVN_VERSION 3

#pragma pack(push, 1)
typedef struct {
  BYTE code;
  DWORD padding;
  WORD numProcesses;
  WORD index;
  DWORD pid;
  DWORD64 memory;
  DWORD affinity;
  BYTE isWow64;
  char name[32];
} ProcessPacket;
#pragma pack(pop)

SOCKET sock;
struct sockaddr_in clientAddr;
int clientAddrLen = sizeof(clientAddr);
volatile BOOL running = TRUE;

typedef struct PidNode {
  DWORD pid;
  struct PidNode *next;
} PidNode;

PidNode *seenPidsHead = NULL;

void addSeenPid(DWORD pid) {
  PidNode *newNode = (PidNode *)malloc(sizeof(PidNode));
  newNode->pid = pid;
  newNode->next = seenPidsHead;
  seenPidsHead = newNode;
}

BOOL isPidSeen(DWORD pid) {
  PidNode *current = seenPidsHead;
  while (current) {
    if (current->pid == pid)
      return TRUE;
    current = current->next;
  }
  return FALSE;
}

void sendCursorFeedback() {
  POINT ptr;
  if (GetCursorPos(&ptr)) {
    BYTE buffer[5];
    buffer[0] = RC_CURSOR_POS_FEEDBACK;
    *(short *)(buffer + 1) = (short)ptr.x;
    *(short *)(buffer + 3) = (short)ptr.y;
    sendto(sock, (const char *)buffer, 5, 0, (struct sockaddr *)&clientAddr,
           clientAddrLen);
  }
}

void handleListProcesses() {
  HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (hSnap == INVALID_HANDLE_VALUE)
    return;

  PROCESSENTRY32 pe32;
  pe32.dwSize = sizeof(PROCESSENTRY32);

  int count = 0;
  if (Process32First(hSnap, &pe32)) {
    do {
      count++;
    } while (Process32Next(hSnap, &pe32));
  }

  if (count == 0) {
    CloseHandle(hSnap);
    return;
  }

  Process32First(hSnap, &pe32);
  int index = 0;
  do {

    ProcessPacket packet;
    ZeroMemory(&packet, sizeof(packet));
    packet.code = RC_GET_PROCESS;
    packet.numProcesses = (WORD)count;
    packet.index = (WORD)index;
    packet.pid = pe32.th32ProcessID;
    strncpy(packet.name, pe32.szExeFile, 31);
    packet.name[31] = '\0';

    HANDLE hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ,
                                  FALSE, pe32.th32ProcessID);
    if (hProcess) {
      PROCESS_MEMORY_COUNTERS pmc;
      if (GetProcessMemoryInfo(hProcess, &pmc, sizeof(pmc))) {
        packet.memory = pmc.WorkingSetSize;
      }

      DWORD_PTR procAffinity, sysAffinity;
      if (GetProcessAffinityMask(hProcess, &procAffinity, &sysAffinity)) {
        packet.affinity = (DWORD)procAffinity;
      }

      BOOL isWow64 = FALSE;
      IsWow64Process(hProcess, &isWow64);
      packet.isWow64 = (BYTE)isWow64;

      CloseHandle(hProcess);
    }

    sendto(sock, (const char *)&packet, sizeof(packet), 0,
           (struct sockaddr *)&clientAddr, clientAddrLen);
    index++;
  } while (Process32Next(hSnap, &pe32) && index < count);

  CloseHandle(hSnap);
}

void handleExec(const char *payload, int len) {

  if (len < 8)
    return;
  int filenameLen = *(int *)(payload);
  int paramsLen = *(int *)(payload + 4);

  if (len < 8 + filenameLen)
    return;

  char *filename = (char *)malloc(filenameLen + 1);
  char *params = (char *)malloc(paramsLen + 1);

  if (filename) {
    memcpy(filename, payload + 8, filenameLen);
    filename[filenameLen] = '\0';
  }

  if (params) {
    memcpy(params, payload + 8 + filenameLen, paramsLen);
    params[paramsLen] = '\0';
  }

  ShellExecuteA(NULL, "open", filename, params, NULL, SW_SHOW);

  if (filename)
    free(filename);
  if (params)
    free(params);
}

void handleKillProcess(const char *payload, int len) {

  int nameLen = *(int *)payload;
  char *name = (char *)malloc(nameLen + 1);
  memcpy(name, payload + 4, nameLen);
  name[nameLen] = '\0';

  HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (hSnap != INVALID_HANDLE_VALUE) {
    PROCESSENTRY32 pe32;
    pe32.dwSize = sizeof(PROCESSENTRY32);
    if (Process32First(hSnap, &pe32)) {
      do {
        if (_stricmp(pe32.szExeFile, name) == 0) {
          HANDLE hProcess =
              OpenProcess(PROCESS_TERMINATE, FALSE, pe32.th32ProcessID);
          if (hProcess) {
            TerminateProcess(hProcess, 1);
            CloseHandle(hProcess);
          }
        }
      } while (Process32Next(hSnap, &pe32));
    }
    CloseHandle(hSnap);
  }
  free(name);
}

void handleSetAffinity(const char *buffer, int len) {

  int pid = *(int *)(buffer + 5);
  int mask = *(int *)(buffer + 9);

  if (pid != 0) {
    HANDLE hProcess = OpenProcess(PROCESS_SET_INFORMATION, FALSE, pid);
    if (hProcess) {
      SetProcessAffinityMask(hProcess, mask);
      CloseHandle(hProcess);
    }
  } else {
    int nameLen = (unsigned char)buffer[13];
    char *name = (char *)malloc(nameLen + 1);
    memcpy(name, buffer + 14, nameLen);
    name[nameLen] = '\0';

    HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (hSnap != INVALID_HANDLE_VALUE) {
      PROCESSENTRY32 pe32;
      pe32.dwSize = sizeof(PROCESSENTRY32);
      if (Process32First(hSnap, &pe32)) {
        do {
          if (_stricmp(pe32.szExeFile, name) == 0) {
            HANDLE hProcess =
                OpenProcess(PROCESS_SET_INFORMATION, FALSE, pe32.th32ProcessID);
            if (hProcess) {
              SetProcessAffinityMask(hProcess, mask);
              CloseHandle(hProcess);
            }
          }
        } while (Process32Next(hSnap, &pe32));
      }
      CloseHandle(hSnap);
    }
    free(name);
  }
}

struct FindWindowData {
  DWORD pid;
  HWND hWnd;
};

BOOL CALLBACK EnumWindowsProc(HWND hWnd, LPARAM lParam) {
  struct FindWindowData *data = (struct FindWindowData *)lParam;
  DWORD currPid = 0;
  GetWindowThreadProcessId(hWnd, &currPid);
  if (currPid == data->pid && IsWindowVisible(hWnd) &&
      GetWindow(hWnd, GW_OWNER) == NULL) {
    data->hWnd = hWnd;
    return FALSE;
  }
  return TRUE;
}

void handleBringToFront(const char *buffer, int len) {
  int nameLen = *(int *)(buffer + 1);
  char *name = (char *)malloc(nameLen + 1);
  memcpy(name, buffer + 5, nameLen);
  name[nameLen] = '\0';

  DWORD pid = 0;
  HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (hSnap != INVALID_HANDLE_VALUE) {
    PROCESSENTRY32 pe32;
    pe32.dwSize = sizeof(PROCESSENTRY32);
    if (Process32First(hSnap, &pe32)) {
      do {
        if (_stricmp(pe32.szExeFile, name) == 0) {
          pid = pe32.th32ProcessID;
          break;
        }
      } while (Process32Next(hSnap, &pe32));
    }
    CloseHandle(hSnap);
  }

  if (pid != 0) {
    struct FindWindowData winData = {pid, NULL};
    EnumWindows(EnumWindowsProc, (LPARAM)&winData);
    if (winData.hWnd) {
      if (IsIconic(winData.hWnd))
        ShowWindow(winData.hWnd, SW_RESTORE);
      else
        ShowWindow(winData.hWnd, SW_SHOW);
      SetForegroundWindow(winData.hWnd);
    }
  }

  free(name);
}

void handleInput(const char *buffer, int len) {
  BYTE code = buffer[0];
  if (code == RC_MOUSE_EVENT) {

    int flags = *(int *)(buffer + 5);
    short dx = *(short *)(buffer + 9);
    short dy = *(short *)(buffer + 11);
    short wheel = *(short *)(buffer + 13);
    BYTE feedback = (BYTE)buffer[15];

    mouse_event(flags, dx, dy, wheel, 0);
    if (feedback)
      sendCursorFeedback();
  } else if (code == RC_KEYBOARD_EVENT) {

    BYTE vkey = (BYTE)buffer[1];
    int flags = *(int *)(buffer + 2);
    keybd_event(vkey, 0, flags, 0);
  } else if (code == RC_AGVN_POINTER && len >= 11) {
    /* AGVN: a touch as a real mouse. One SendInput moves the cursor to the
       pixel (x, y) and presses or releases its buttons there, so the game gets
       WM_INPUT too: Unity's Input System reads the mouse only from raw input,
       which Wine makes from SendInput but not from the X server's core pointer
       events (the X server has no XInput2). */
    int flags = *(int *)(buffer + 1);
    short x = *(short *)(buffer + 5);
    short y = *(short *)(buffer + 7);
    short wheel = *(short *)(buffer + 9);
    int w = GetSystemMetrics(SM_CXSCREEN), h = GetSystemMetrics(SM_CYSCREEN);
    INPUT in;
    ZeroMemory(&in, sizeof(in));
    in.type = INPUT_MOUSE;
    in.mi.mouseData = (DWORD)(LONG)wheel;
    if (flags & AGVN_POINTER_RELATIVE) {
      /* AGVN 2: a finger's movement for a game that holds the cursor: Wine
         gives the game that movement as raw input and as DirectInput's, while
         the cursor stays where the game holds it */
      in.mi.dx = x;
      in.mi.dy = y;
      in.mi.dwFlags = ((DWORD)flags & ~(DWORD)AGVN_POINTER_RELATIVE) | MOUSEEVENTF_MOVE;
    } else {
      /* Wine maps 0..65535 to the screen as (v * size) >> 16: rounding up
         lands on the pixel itself */
      in.mi.dx = w > 0 ? (LONG)(((LONGLONG)x * 65536 + w - 1) / w) : 0;
      in.mi.dy = h > 0 ? (LONG)(((LONGLONG)y * 65536 + h - 1) / h) : 0;
      in.mi.dwFlags = (DWORD)flags | MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE;
    }
    SendInput(1, &in, sizeof(in));
  }
}

/* AGVN 2: RC_AGVN_STATE's bits now. The mouse is locked when the foreground
   thread's cursor is hidden and the cursor is held in a small part of the
   screen, a quarter of it each way at most: Unity's CursorLockMode.Locked
   clips it to the window's centre point, SDL's relative mode to a few pixels
   there. A game that hides the cursor to draw its own clips it to its window
   at most (Unity's CursorLockMode.Confined), and its taps stay where the
   finger is. The caret is the one of the foreground thread: a text field of
   Windows (a name box, a launcher). */
static BYTE agvnState(void) {
  BYTE state = 0;
  CURSORINFO cursor;
  RECT clip;
  GUITHREADINFO gui;
  int w = GetSystemMetrics(SM_CXSCREEN), h = GetSystemMetrics(SM_CYSCREEN);
  ZeroMemory(&cursor, sizeof(cursor));
  cursor.cbSize = sizeof(cursor);
  BOOL hidden = GetCursorInfo(&cursor) &&
                (!(cursor.flags & CURSOR_SHOWING) || cursor.hCursor == NULL);
  if (hidden && GetClipCursor(&clip) &&
      (clip.right - clip.left) * 4 <= w && (clip.bottom - clip.top) * 4 <= h)
    state |= AGVN_STATE_MOUSE_LOCKED;
  ZeroMemory(&gui, sizeof(gui));
  gui.cbSize = sizeof(gui);
  if (GetGUIThreadInfo(0, &gui) && gui.hwndCaret != NULL)
    state |= AGVN_STATE_TEXT_CARET;
  return state;
}

/* AGVN 2: a state is told once it held for two reads in a row: a game that
   shows the cursor and clips it in one go is never told as holding it for the
   moment between the two calls */
DWORD WINAPI AgvnWatchThread(LPVOID lpParam) {
  BYTE last = 0xff, seen = 0xff;
  DWORD lastSent = 0;
  while (running) {
    BYTE state = agvnState();
    DWORD now = GetTickCount();
    BOOL steady = state == seen;
    seen = state;
    if ((steady && state != last) || now - lastSent >= 1000) {
      if (steady) last = state;
      BYTE buffer[2] = {RC_AGVN_STATE, last};
      if (last != 0xff)
        sendto(sock, (const char *)buffer, 2, 0, (struct sockaddr *)&clientAddr,
               clientAddrLen);
      lastSent = now;
    }
    Sleep(50);
  }
  return 0;
}

/* AGVN 3: Wine's own processes, which outlive a game. Anything else still
   running is the game's: a launcher's game, an Electron game's renderer. */
static const char *agvnWineProcesses[] = {
    "explorer.exe", "services.exe", "winedevice.exe", "plugplay.exe",
    "svchost.exe", "rpcss.exe", "conhost.exe", "wineboot.exe",
    "winemenubuilder.exe", "start.exe", "mscorsvw.exe", "tabtip.exe",
    "winhandler.exe", "agvn-winhandler.exe"};
static HANDLE agvnGame;

/* AGVN 3: how many processes run besides Wine's own and this one */
static int agvnGameProcesses(void) {
  int count = 0;
  DWORD self = GetCurrentProcessId();
  HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (hSnap == INVALID_HANDLE_VALUE)
    return 1; /* cannot tell: the game may still run */
  PROCESSENTRY32 pe32;
  pe32.dwSize = sizeof(PROCESSENTRY32);
  if (Process32First(hSnap, &pe32)) {
    do {
      BOOL wine = pe32.th32ProcessID == self;
      for (size_t i = 0; !wine && i < sizeof(agvnWineProcesses) / sizeof(agvnWineProcesses[0]); i++)
        wine = _stricmp(pe32.szExeFile, agvnWineProcesses[i]) == 0;
      if (!wine)
        count++;
    } while (Process32Next(hSnap, &pe32));
  }
  CloseHandle(hSnap);
  return count;
}

/* AGVN 3: winhandler outlived the game, so Wine's desktop stayed and the app
   kept a black screen: an Electron game whose processes all ended 6 s after
   it started looked like one still loading (31/10/2026). Once the game has
   ended and no process of it is left for 3 s in a row, the app is told, with
   the game's exit code, and winhandler ends. */
DWORD WINAPI AgvnGameEndThread(LPVOID lpParam) {
  DWORD code = 0;
  if (agvnGame) {
    WaitForSingleObject(agvnGame, INFINITE);
    GetExitCodeProcess(agvnGame, &code);
  } else {
    Sleep(15000); /* started through another process (a shortcut): its game comes up meanwhile */
  }
  int quiet = 0;
  while (running) {
    Sleep(1000);
    quiet = agvnGameProcesses() ? 0 : quiet + 1;
    if (quiet < 3)
      continue;
    BYTE buffer[5] = {RC_AGVN_GAME_ENDED};
    memcpy(buffer + 1, &code, sizeof(code));
    sendto(sock, (const char *)buffer, sizeof(buffer), 0,
           (struct sockaddr *)&clientAddr, clientAddrLen);
    Sleep(200);
    ExitProcess(code);
  }
  return 0;
}

DWORD WINAPI ServerThread(LPVOID lpParam) {
  WSADATA wsaData;
  if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0)
    return 0;

  sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if (sock == INVALID_SOCKET) {
    WSACleanup();
    return 0;
  }

  struct sockaddr_in serverAddr;
  serverAddr.sin_family = AF_INET;
  serverAddr.sin_port = htons(SERVER_PORT);
  serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");

  if (bind(sock, (struct sockaddr *)&serverAddr, sizeof(serverAddr)) ==
      SOCKET_ERROR) {
    closesocket(sock);
    WSACleanup();
    return 0;
  }

  clientAddr.sin_family = AF_INET;
  clientAddr.sin_port = htons(CLIENT_PORT);
  clientAddr.sin_addr.s_addr = inet_addr("127.0.0.1");

  char initBuffer[64];
  initBuffer[0] = RC_INIT;
  initBuffer[1] = AGVN_VERSION; /* AGVN: Winlator's app reads only the code */
  sendto(sock, initBuffer, 2, 0, (struct sockaddr *)&clientAddr, clientAddrLen);
  CreateThread(NULL, 0, AgvnWatchThread, NULL, 0, NULL); /* AGVN 2 */

  char buffer[BUFFER_SIZE];
  while (running) {
    struct sockaddr_in sender;
    int senderLen = sizeof(sender);
    int len = recvfrom(sock, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&sender,
                       &senderLen);

    if (len > 0) {
      BYTE command = buffer[0];

      switch (command) {
      case RC_EXIT:
        running = FALSE;
        ExitProcess(0);
        break;
      case RC_INIT:
        break;
      case RC_EXEC:
        handleExec(buffer + 5, len - 5);
      case RC_KILL_PROCESS:
        handleKillProcess(buffer + 1, len - 1);
        break;
      case RC_LIST_PROCESSES:
        handleListProcesses();
        break;
      case RC_SET_PROCESS_AFFINITY:
        handleSetAffinity(buffer, len);
        break;
      case RC_MOUSE_EVENT:
      case RC_KEYBOARD_EVENT:
      case RC_AGVN_POINTER:
        handleInput(buffer, len);
        break;
      case RC_BRING_TO_FRONT:
        handleBringToFront(buffer, len);
        break;
      }
    }
  }

  closesocket(sock);
  WSACleanup();
  return 0;
}

void handleChildProcesses(int affinityMask) {
  if (affinityMask <= 0)
    return;

  DWORD myPid = GetCurrentProcessId();

  while (running) {
    HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (hSnap != INVALID_HANDLE_VALUE) {
      PROCESSENTRY32 pe32;
      pe32.dwSize = sizeof(PROCESSENTRY32);
      if (Process32First(hSnap, &pe32)) {
        do {
          if (pe32.th32ProcessID > myPid) {
            if (!isPidSeen(pe32.th32ProcessID)) {
              HANDLE hProcess = OpenProcess(PROCESS_SET_INFORMATION, FALSE,
                                            pe32.th32ProcessID);
              if (hProcess) {
                SetProcessAffinityMask(hProcess, affinityMask);
                CloseHandle(hProcess);
              }
              addSeenPid(pe32.th32ProcessID);
            }
          }
        } while (Process32Next(hSnap, &pe32));
      }
      CloseHandle(hSnap);
    }
    Sleep(1000);
  }
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                   LPSTR lpCmdLine, int nCmdShow) {
  int affinity = 0;
  /* AGVN: the command line as Unicode. __argv is in the Windows code page,
     which loses the letters beyond it: a game in ".../Lifeguard Holic Viet
     Hoa/GAMEHUB" (with its accents) got "File not found." (10/10/2026),
     while GameHub opened it. */
  LPCWSTR directory = NULL;
  LPCWSTR executable = L"wfm.exe";

  int argc = 0;
  LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
  if (argv == NULL)
    argc = 0;

  int argIdx = 1;
  while (argIdx < argc) {
    if (wcscmp(argv[argIdx], L"/affinity") == 0) {
      if (argIdx + 1 < argc) {
        affinity = (int)wcstol(argv[argIdx + 1], NULL, 16);
        argIdx += 2;
      } else {
        argIdx++;
      }
    } else if (wcscmp(argv[argIdx], L"/dir") == 0) {
      if (argIdx + 1 < argc) {
        directory = argv[argIdx + 1];
        argIdx += 2;
      } else {
        argIdx++;
      }
    } else {
      executable = argv[argIdx];
      argIdx++;
      break;
    }
  }

  WCHAR execArgs[4096] = {0};
  size_t used = 0;
  for (int i = argIdx; i < argc; i++) {
    size_t len = wcslen(argv[i]);
    if (used + len + 4 > sizeof(execArgs) / sizeof(execArgs[0]))
      break; /* AGVN: the arguments that fit, never past the buffer */
    execArgs[used++] = L'"';
    wcscpy(execArgs + used, argv[i]);
    used += len;
    execArgs[used++] = L'"';
    execArgs[used++] = L' ';
    execArgs[used] = 0;
  }

  /* AGVN 2: no pointer acceleration, so that a finger's movement
     (AGVN_POINTER_RELATIVE) reaches the game as it is: Wine, as Windows,
     doubles a relative move of more than 6 counts by default */
  int noAcceleration[3] = {0, 0, 0};
  SystemParametersInfoW(SPI_SETMOUSE, 0, noAcceleration, 0);

  SHELLEXECUTEINFOW sei = {0};
  sei.cbSize = sizeof(SHELLEXECUTEINFOW);
  sei.fMask = SEE_MASK_NOCLOSEPROCESS;
  sei.lpFile = executable;
  sei.lpParameters = execArgs[0] ? execArgs : NULL;
  sei.lpDirectory = directory;
  sei.nShow = SW_SHOW;

  ShellExecuteExW(&sei);
  HANDLE hThread = CreateThread(NULL, 0, ServerThread, NULL, 0, NULL);
  /* AGVN 3: a game, not the file manager of a container's desktop */
  if (_wcsicmp(executable, L"wfm.exe") != 0) {
    agvnGame = sei.hProcess;
    CreateThread(NULL, 0, AgvnGameEndThread, NULL, 0, NULL);
  } else if (sei.hProcess) {
    CloseHandle(sei.hProcess);
  }

  if (affinity > 0) {
    handleChildProcesses(affinity);
  } else {
    WaitForSingleObject(hThread, INFINITE);
  }

  return 0;
}

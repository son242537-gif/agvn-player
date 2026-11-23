/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
/* Tests app/src/main/cpp/agvn/field-caret.h on the PC: gcc -o /tmp/t field-caret-test.c && /tmp/t */
#include <assert.h>
#include <stdio.h>
#include "../../../../app/src/main/cpp/agvn/field-caret.h"

static int field(const char *ascii) {
  unsigned short name[96];
  int i = 0;
  for (; ascii[i] && i < 95; i++) name[i] = (unsigned char)ascii[i];
  name[i] = 0;
  return agvnFieldClass(name);
}

int main(void) {
  /* text fields of Windows: the keyboard comes up */
  const char *fields[] = {"Edit", "EDIT", "RichEdit20W", "RICHEDIT50W", "WindowsForms10.EDIT.app.0.141b42a_r9_ad1",
                          "TEdit", "TMemo", "TRichEdit", "ThunderRT6TextBox", "ThunderTextBox", 0};
  /* games' own windows that hold a caret to place the IME's box: it does not */
  const char *games[] = {"TVPMainWindow", "UnityWndClass", "YYGameMakerYY", "SDL_app", "RGSS Player",
                         "Chrome_RenderWidgetHostHWND", "", "Edi", 0};
  for (int i = 0; fields[i]; i++) assert(field(fields[i]));
  for (int i = 0; games[i]; i++) assert(!field(games[i]));
  assert(!agvnFieldClass(0));
  puts("field-caret.h: all cases passed");
  return 0;
}

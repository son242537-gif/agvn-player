/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
/* AGVN 6 (agvn-winhandler.exe): whether a window class is a text field of Windows, for the caret rule of
   winhandler.c's agvnState. Edit controls (Edit, RichEdit20W, RICHEDIT50W, WindowsForms10.EDIT..., Delphi's TEdit and
   TMemo) and Visual Basic's text boxes (ThunderRT6TextBox). Pure C, no Windows header: the class name as UTF-16 code
   units; scripts/agvn/winhandler/test/field-caret-test.c tests it on the PC. */
#ifndef AGVN_FIELD_CARET_H
#define AGVN_FIELD_CARET_H

static int agvnHasWord(const unsigned short *name, const char *word) {
  for (int i = 0; name[i]; i++) {
    int j = 0;
    for (; word[j]; j++) {
      unsigned short c = name[i + j];
      if (c >= 'A' && c <= 'Z') c = (unsigned short)(c - 'A' + 'a');
      if (c != (unsigned char)word[j]) break;
    }
    if (!word[j]) return 1;
  }
  return 0;
}

static int agvnFieldClass(const unsigned short *name) {
  return name && (agvnHasWord(name, "edit") || agvnHasWord(name, "memo") || agvnHasWord(name, "textbox"));
}

#endif

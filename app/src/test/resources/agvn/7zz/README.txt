What 7-Zip 25.01's console program (7zz, built by scripts/agvn/7zip/build-7zz.sh --host) really printed, captured on
2026-11-20 with the switches AgvnUnpack7zz uses: "l -slt -ba -sccUTF-8 -p..." for the list-* files and
"x -y -bsp1 -bso0 -sccUTF-8 -snl- -snh- -o... -p..." for the unpack-* files. *.out is its output (the progress is redrawn
with backspaces), *.err its errors. AgvnUnpack7zzParseTest reads them, on every machine. The RAR5 archives were written
by hand, stored without compression (AgvnRar5Fixture); 7-Zip cannot create RAR.

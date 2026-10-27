# Copyright (c) 2026 agvn.io — MIT License (see LICENSE).
# Plays the app's WinHandler: listens on 7947, sends relative RC_AGVN_POINTER moves to 7946 while the test game locks.
import socket, struct, time, sys
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("127.0.0.1", 7947)); s.settimeout(0.05)
t0 = None; log = []; sent = 0; end = time.time() + 60
while time.time() < end:
    try:
        data, addr = s.recvfrom(64)
        now = time.time()
        if data[0] == 1 and t0 is None: t0 = now; log.append("INIT version %d" % (data[1] if len(data) > 1 else 0))
        elif data[0] == 15:
            line = "STATE %d" % data[1]
            if not log or log[-1].split(" @")[0] != line: log.append(line + " @%.1f" % (now - t0))
    except socket.timeout:
        pass
    if t0 is not None:
        el = time.time() - t0
        if 2.5 < el < 4.5 and sent < 20:  # during the lock: a finger dragged 20 x (7, -3)
            s.sendto(struct.pack("<BihhH", 14, 0x40000000, 7, -3, 0), ("127.0.0.1", 7946)); sent += 1
        if el > 16.5: break
print("\n".join(log)); print("relative moves sent:", sent)

/* AGVN Player - <sys/shm.h> for building Mesa against the imagefs. Copyright (c) 2026 agvn.io - MIT License.
 *
 * Android blocks System V shared memory (shmget & co. fail), but X11 MIT-SHM needs it. The imagefs ships
 * Winlator's libandroid-sysvshm, which implements it under the libandroid_shm* names (the interface of Termux's
 * libandroid-shmem). This header takes the place of bionic's and sends the calls there; link with
 * -landroid-sysvshm. Ludashi's Zink build imports exactly these four symbols.
 */
#ifndef _SYS_SHM_H_
#define _SYS_SHM_H_

#include <linux/shm.h>
#include <sys/cdefs.h>
#include <sys/ipc.h>
#include <sys/types.h>

#ifndef shmid_ds
#define shmid_ds shmid64_ds
#endif

__BEGIN_DECLS

#define shmat libandroid_shmat
#define shmctl libandroid_shmctl
#define shmdt libandroid_shmdt
#define shmget libandroid_shmget

void *libandroid_shmat(int shmid, const void *shmaddr, int shmflg);
int libandroid_shmctl(int shmid, int cmd, struct shmid_ds *buf);
int libandroid_shmdt(const void *shmaddr);
int libandroid_shmget(key_t key, size_t size, int shmflg);

__END_DECLS

#endif

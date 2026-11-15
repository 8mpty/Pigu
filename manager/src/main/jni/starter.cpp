#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <ctime>
#include <cstring>
#include <libgen.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <cerrno>
#include <string>
#include <termios.h>
#include "android.h"
#include "misc.h"
#include "selinux.h"
#include "cgroup.h"
#include "logging.h"

#ifdef DEBUG
#define JAVA_DEBUGGABLE
#endif

#define perrorf(...) fprintf(stderr, __VA_ARGS__)

#define EXIT_FATAL_SET_CLASSPATH 3
#define EXIT_FATAL_FORK 4
#define EXIT_FATAL_APP_PROCESS 5
#define EXIT_FATAL_UID 6
#define EXIT_FATAL_PM_PATH 7
#define EXIT_FATAL_KILL 9
#define EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX 10

#define SERVER_NAME "shizuku_server"
#define SERVER_CLASS_PATH "rikka.shizuku.server.ShizukuService"

#if defined(__arm__)
#define ABI "arm"
#elif defined(__i386__)
#define ABI "x86"
#elif defined(__x86_64__)
#define ABI "x86_64"
#elif defined(__aarch64__)
#define ABI "arm64"
#endif

static void run_server(const char *dex_path, const char *main_class, const char *process_name) {
    if (setenv("CLASSPATH", dex_path, true)) {
        LOGE("can't set CLASSPATH\n");
        exit(EXIT_FATAL_SET_CLASSPATH);
    }

#define ARG(v) char **v = nullptr; \
    char buf_##v[PATH_MAX]; \
    size_t v_size = 0; \
    uintptr_t v_current = 0;
#define ARG_PUSH(v, arg) v_size += sizeof(char *); \
if (v == nullptr) { \
    v = (char **) malloc(v_size); \
} else { \
    v = (char **) realloc(v, v_size);\
} \
v_current = (uintptr_t) v + v_size - sizeof(char *); \
*((char **) v_current) = arg ? strdup(arg) : nullptr;

#define ARG_END(v) ARG_PUSH(v, nullptr)

#define ARG_PUSH_FMT(v, fmt, ...) snprintf(buf_##v, PATH_MAX, fmt, __VA_ARGS__); \
    ARG_PUSH(v, buf_##v)

#ifdef JAVA_DEBUGGABLE
#define ARG_PUSH_DEBUG_ONLY(v, arg) ARG_PUSH(v, arg)
#define ARG_PUSH_DEBUG_VM_PARAMS(v) \
    if (android_get_device_api_level() >= 30) { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-XjdwpProvider:adbconnection"); \
        ARG_PUSH(v, "-XjdwpOptions:suspend=n,server=y"); \
    } else if (android_get_device_api_level() >= 28) { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-XjdwpProvider:internal"); \
        ARG_PUSH(v, "-XjdwpOptions:transport=dt_android_adb,suspend=n,server=y"); \
    } else { \
        ARG_PUSH(v, "-Xcompiler-option"); \
        ARG_PUSH(v, "--debuggable"); \
        ARG_PUSH(v, "-agentlib:jdwp=transport=dt_android_adb,suspend=n,server=y"); \
    }
#else
#define ARG_PUSH_DEBUG_VM_PARAMS(v)
#define ARG_PUSH_DEBUG_ONLY(v, arg)
#endif

    char lib_path[PATH_MAX]{0};
    snprintf(lib_path, PATH_MAX, "%s/lib/%s", dirname(dex_path), ABI);

    ARG(argv)
    ARG_PUSH(argv, "/system/bin/app_process")
    ARG_PUSH_FMT(argv, "-Djava.class.path=%s", dex_path)
    ARG_PUSH_FMT(argv, "-Dshizuku.library.path=%s", lib_path)
    ARG_PUSH_DEBUG_VM_PARAMS(argv)
    ARG_PUSH(argv, "/system/bin")
    ARG_PUSH_FMT(argv, "--nice-name=%s", process_name)
    ARG_PUSH(argv, main_class)
    ARG_PUSH_DEBUG_ONLY(argv, "--debug")
    ARG_END(argv)

    LOGD("exec app_process");

    if (execvp((const char *) argv[0], argv)) {
        exit(EXIT_FATAL_APP_PROCESS);
    }
}

// Find the cgroup v2 mount point by scanning /proc/mounts. Returns true and fills
// mount_point on success.
static bool find_cgroup2_mount(char *mount_point, size_t len) {
    FILE *f = fopen("/proc/mounts", "r");
    if (!f) return false;

    char line[512];
    bool found = false;
    while (fgets(line, sizeof(line), f)) {
        char dev[128], mp[256], fstype[64];
        if (sscanf(line, "%127s %255s %63s", dev, mp, fstype) == 3) {
            if (strcmp(fstype, "cgroup2") == 0) {
                strncpy(mount_point, mp, len - 1);
                mount_point[len - 1] = '\0';
                found = true;
                break;
            }
        }
    }
    fclose(f);
    return found;
}

// Move the server out of adbd's per-PID cgroup. On some devices the USB stack
// restarts adbd when the screen turns off in a data-transfer USB mode, and
// Android then kills adbd's cgroup - including this process if it is still in
// that subtree. A sibling cgroup lets the server survive that restart.
static int try_escape_v2_cgroup(int pid) {
    char cg2_root[PATH_MAX] = "/sys/fs/cgroup";
    find_cgroup2_mount(cg2_root, sizeof(cg2_root));
    printf("info: cgroupv2 mount point: %s\n", cg2_root);
    fflush(stdout);

    FILE *f = fopen("/proc/self/cgroup", "r");
    if (!f) {
        printf("warn: can't open /proc/self/cgroup: %s\n", strerror(errno));
        return -1;
    }

    char line[512];
    char v2_path[PATH_MAX] = {0};
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "0::", 3) == 0) {
            char *path_start = line + 3;
            char *nl = strchr(path_start, '\n');
            if (nl) *nl = '\0';
            strncpy(v2_path, path_start, sizeof(v2_path) - 1);
            break;
        }
    }
    fclose(f);

    if (v2_path[0] == '\0') {
        printf("warn: no cgroupv2 entry in /proc/self/cgroup\n");
        return -1;
    }
    printf("info: current cgroupv2 path: %s\n", v2_path);
    fflush(stdout);

    // Strip the pid_XXXXX leaf: /system/uid_2000/pid_12345 -> /system/uid_2000
    char *last_slash = strrchr(v2_path, '/');
    if (!last_slash || last_slash == v2_path) {
        printf("warn: cgroupv2 path too shallow to escape: %s\n", v2_path);
        return -1;
    }
    *last_slash = '\0';

    char parent_path[PATH_MAX];
    if (snprintf(parent_path, sizeof(parent_path), "%s%s", cg2_root, v2_path) >=
        (int) sizeof(parent_path)) {
        printf("warn: cgroupv2 parent path is too long\n");
        return -1;
    }
    printf("info: attempting cgroupv2 escape to parent: %s\n", parent_path);
    fflush(stdout);

    // The parent is often a non-leaf (adbd itself lives there), and cgroup v2
    // refuses to put a process in a non-leaf cgroup. Fall back to a dedicated
    // child cgroup.
    if (cgroup::switch_cgroup(parent_path, pid)) {
        printf("info: cgroupv2 escape succeeded (parent): %s\n", parent_path);
        fflush(stdout);
        return 0;
    }
    printf("info: parent write failed, trying a shizuku leaf cgroup\n");
    fflush(stdout);

    char shizuku_path[PATH_MAX];
    if (snprintf(shizuku_path, sizeof(shizuku_path), "%s/shizuku", parent_path) >=
        (int) sizeof(shizuku_path)) {
        printf("warn: cgroupv2 leaf path is too long\n");
        return -1;
    }
    if (mkdir(shizuku_path, 0755) != 0 && errno != EEXIST) {
        printf("warn: mkdir %s failed: %s\n", shizuku_path, strerror(errno));
        // The directory may already exist from an earlier run; still try to
        // move into it.
    }
    if (cgroup::switch_cgroup(shizuku_path, pid)) {
        printf("info: cgroupv2 escape succeeded (shizuku leaf): %s\n", shizuku_path);
        fflush(stdout);
        return 0;
    }
    printf("warn: cgroupv2 escape failed for pid %d\n", pid);
    fflush(stdout);
    return -1;
}

static void start_server(const char *path, const char *main_class, const char *process_name) {
    int fds[2];
    if (pipe(fds) < 0) {
        perrorf("fatal: can't create pipe\n");
        exit(EXIT_FATAL_FORK);
    }

    pid_t pid = fork();
    switch (pid) {
        case -1: {
            perrorf("fatal: can't fork\n");
            exit(EXIT_FATAL_FORK);
        }
        case 0: {
            LOGD("child");
            close(fds[0]);
            setsid();
            chdir("/");

            // ADB starts are the ones launched inside adbd's cgroup. Root starts
            // already go through switch_cgroup() above, so leave them alone.
            if (getuid() == 2000) {
                try_escape_v2_cgroup(getpid());
            }

            int fd = open("/dev/null", O_RDWR);
            if (fd != -1) {
                dup2(fd, STDIN_FILENO);
                dup2(fd, STDOUT_FILENO);
                dup2(fd, STDERR_FILENO);
                if (fd > 2) close(fd);
            }
            
            char ready = 1;
            write(fds[1], &ready, 1);
            close(fds[1]);

            run_server(path, main_class, process_name);
        }
        default: {
            close(fds[1]);
            char ready;
            read(fds[0], &ready, 1);
            close(fds[0]);

            printf("info: shizuku_server pid is %d\n", pid);
            printf("info: shizuku_starter exit with 0\n");
            exit(EXIT_SUCCESS);
        }
    }
}

static int check_selinux(const char *s, const char *t, const char *c, const char *p) {
    int res = se::selinux_check_access(s, t, c, p, nullptr);
#ifndef DEBUG
    if (res != 0) {
#endif
    printf("info: selinux_check_access %s %s %s %s: %d\n", s, t, c, p, res);
    fflush(stdout);
#ifndef DEBUG
    }
#endif
    return res;
}

static int switch_cgroup() {
    int pid = getpid();
    if (cgroup::switch_cgroup("/acct", pid)) {
        printf("info: switch cgroup succeeded, cgroup in /acct\n");
        return 0;
    }
    if (cgroup::switch_cgroup("/dev/cg2_bpf", pid)) {
        printf("info: switch cgroup succeeded, cgroup in /dev/cg2_bpf\n");
        return 0;
    }
    if (cgroup::switch_cgroup("/sys/fs/cgroup", pid)) {
        printf("info: switch cgroup succeeded, cgroup in /sys/fs/cgroup\n");
        return 0;
    }
    char buf[PROP_VALUE_MAX + 1];
    if (__system_property_get("ro.config.per_app_memcg", buf) > 0 &&
        strncmp(buf, "false", 5) != 0) {
        if (cgroup::switch_cgroup("/dev/memcg/apps", pid)) {
            printf("info: switch cgroup succeeded, cgroup in /dev/memcg/apps\n");
            return 0;
        }
    }
    printf("warn: can't switch cgroup\n");
    fflush(stdout);
    return -1;
}

int main(int argc, char *argv[]) {
    std::string apk_path;
    for (int i = 0; i < argc; ++i) {
        if (strncmp(argv[i], "--apk=", 6) == 0) {
            apk_path = argv[i] + 6;
        }
    }

    uid_t uid = getuid();
    if (uid != 0 && uid != 2000) {
        perrorf("fatal: run Shizuku from non root nor adb user (uid=%d).\n", uid);
        exit(EXIT_FATAL_UID);
    }

    se::init();

    if (uid == 0) {
        switch_cgroup();

        if (android_get_device_api_level() >= 29) {
            printf("info: switching mount namespace to init...\n");
            switch_mnt_ns(1);
        }
    }

    if (uid == 0) {
        char *context = nullptr;
        if (se::getcon(&context) == 0) {
            int res = 0;

            res |= check_selinux("u:r:untrusted_app:s0", context, "binder", "call");
            res |= check_selinux("u:r:untrusted_app:s0", context, "binder", "transfer");

            if (res != 0) {
                perrorf("fatal: the su you are using does not allow app (u:r:untrusted_app:s0) to connect to su (%s) with binder.\n",
                        context);
                exit(EXIT_FATAL_BINDER_BLOCKED_BY_SELINUX);
            }
            se::freecon(context);
        }
    }

    printf("info: starter begin\n");
    fflush(stdout);

    // kill old server
    printf("info: killing old process...\n");
    fflush(stdout);

    foreach_proc([](pid_t pid) {
        if (pid == getpid()) return;

        char name[1024];
        if (get_proc_name(pid, name, 1024) != 0) return;

        if (strcmp(SERVER_NAME, name) != 0)
            return;

        if (kill(pid, SIGKILL) == 0)
            printf("info: killed %d (%s)\n", pid, name);
        else if (errno == EPERM) {
            perrorf("fatal: can't kill %d, please try to stop existing Shizuku from app first.\n", pid);
            exit(EXIT_FATAL_KILL);
        } else {
            printf("warn: failed to kill %d (%s)\n", pid, name);
        }
    });

    if (access(apk_path.c_str(), R_OK) == 0) {
        printf("info: use apk path from argv\n");
        fflush(stdout);
    }

    if (apk_path.empty()) {
        // Use /proc/self/exe to get the executable path
        char buf[PATH_MAX];
        ssize_t len = readlink("/proc/self/exe", buf, sizeof(buf) - 1);
        if (len == -1) {
            perror("readlink");
            return 1;
        }
        buf[len] = '\0';
        std::string exe_path(buf);

        // Find "/lib/" and replace from there with "/base.apk"
        size_t lib_pos = exe_path.find("/lib/");
        if (lib_pos != std::string::npos) {
            apk_path = exe_path.substr(0, lib_pos) + "/base.apk";
        }
    }

    if (apk_path.empty()) {
        perrorf("fatal: can't get path of manager\n");
        exit(EXIT_FATAL_PM_PATH);
    }

    printf("info: apk path is %s\n", apk_path.c_str());
    if (access(apk_path.c_str(), R_OK) != 0) {
        perrorf("fatal: can't access manager %s\n", apk_path.c_str());
        exit(EXIT_FATAL_PM_PATH);
    }

    printf("info: starting server...\n");
    fflush(stdout);
    LOGD("start_server");
    start_server(apk_path.c_str(), SERVER_CLASS_PATH, SERVER_NAME);
}

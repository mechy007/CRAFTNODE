#include <jni.h>
#include <sys/sysinfo.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <cstdint>
#include <sstream>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_app_bedrockhost_mobile_NativeDiagnostics_nativeReport(JNIEnv* env, jobject /* instance */) {
    utsname system_info{};
    const int uname_result = uname(&system_info);

    const long page_size = sysconf(_SC_PAGESIZE);
    const long physical_pages = sysconf(_SC_PHYS_PAGES);
    const long available_pages = sysconf(_SC_AVPHYS_PAGES);

    const std::uint64_t total_bytes =
        (page_size > 0 && physical_pages > 0)
            ? static_cast<std::uint64_t>(page_size) * static_cast<std::uint64_t>(physical_pages)
            : 0;
    const std::uint64_t available_bytes =
        (page_size > 0 && available_pages > 0)
            ? static_cast<std::uint64_t>(page_size) * static_cast<std::uint64_t>(available_pages)
            : 0;

    std::ostringstream report;
    report << "Native diagnostics: C++17\n";
    report << "Process ABI: " << sizeof(void*) * 8 << "-bit\n";
    if (uname_result == 0) {
        report << "Kernel: " << system_info.sysname << " " << system_info.release << "\n";
        report << "Machine: " << system_info.machine << "\n";
    } else {
        report << "Kernel details unavailable\n";
    }

    if (total_bytes > 0) {
        report << "Physical RAM: " << (total_bytes / (1024 * 1024)) << " MB\n";
        report << "Currently available system RAM: " << (available_bytes / (1024 * 1024)) << " MB\n";
    } else {
        report << "System RAM metrics unavailable\n";
    }
    report << "Note: available RAM is a system snapshot, not a guarantee for the server process.";

    const std::string result = report.str();
    return env->NewStringUTF(result.c_str());
}

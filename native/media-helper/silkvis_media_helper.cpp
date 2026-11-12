// SilkVis media helper — reference implementation of the contract in
// docs/adr/0003-media-bridge-windows.md and TZ 7.2/7.3.
//
// NOT COMPILED IN THIS REPOSITORY SNAPSHOT. It documents the expected behaviour and
// exists so the Java side and the protocol have a single written counterpart.
//
// Build: MSVC 2022 + Windows SDK with C++/WinRT (see README.md).

#include <winrt/Windows.Media.Control.h>
#include <winrt/Windows.Storage.Streams.h>
#include <windows.h>

#include <atomic>
#include <iostream>
#include <map>
#include <mutex>
#include <string>
#include <thread>

#include "json_lite.h"  // minimal writer/reader; no third-party dependency

using namespace winrt;
using namespace Windows::Media::Control;

namespace {
std::atomic<bool> g_running{true};
std::mutex g_out_mutex;

// stdout carries protocol lines only (MEDIA-07).
void send_line(const std::string& line) {
    std::lock_guard<std::mutex> lock(g_out_mutex);
    std::cout << line << "\n" << std::flush;
}

void log_stderr(const std::string& message) {
    std::cerr << message << "\n";
}

std::string metadata_string(const GlobalSystemMediaTransportControlsSessionMediaProperties& props) {
    std::wstring value = props.Title().c_str();
    if (value.size() > 4096) {
        value.resize(4096);
    }
    return utf8_of(value);
}

}  // namespace

int wmain(int, wchar_t**) {
    // Explicitly no elevation, no service installation, no shell interpolation.
    try {
        auto manager = GlobalSystemMediaTransportControlsSessionManager::RequestAsync().get();

        // HELLO/READY handshake must be the first exchange on the pipe.
        std::string line;
        if (!std::getline(std::cin, line)) {
            return 0;  // EOF on stdin: helper exits (MEDIA-06), no orphan process.
        }
        if (!is_hello(line)) {
            log_stderr("expected HELLO");
            return 2;
        }
        send_line(R"({"schemaVersion":1,"type":"READY","payload":{"protocolVersion":1}})");

        // Serialize WinRT callbacks onto this dispatcher thread; never call WinRT per frame.
        while (g_running && std::getline(std::cin, line)) {
            handle_command(manager, line);  // COMMAND / SELECT_SESSION / ARTWORK_REQUEST
        }
    } catch (const hresult_error& error) {
        log_stderr("winrt error");
        send_line(R"({"schemaVersion":1,"type":"ERROR","payload":{"code":"provider_unavailable"}})");
        return 1;
    }
    return 0;
}

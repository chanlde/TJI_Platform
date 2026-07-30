#include "tji_speaker_core.h"

#include <cmath>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <vector>

int main(int argc, char **argv)
{
    constexpr int sample_rate = 24000;
    constexpr int duration_ms = 1000;
    std::vector<uint8_t> pcm(sample_rate * duration_ms / 1000 * 2);
    for (int index = 0; index < sample_rate; ++index) {
        const auto sample = static_cast<int16_t>(std::lround(
            std::sin(6.283185307179586 * 440.0 * index / sample_rate) *
            12000.0));
        pcm[static_cast<size_t>(index) * 2U] = static_cast<uint8_t>(sample);
        pcm[static_cast<size_t>(index) * 2U + 1U] =
            static_cast<uint8_t>(static_cast<uint16_t>(sample) >> 8U);
    }

    const std::filesystem::path output =
        argc >= 2 ? argv[1] : "generated/REC_OPUS_SMOKE.opus";
    const char *device_id = argc >= 3 ? argv[2] : "T12345678";
    const char *record_id = argc >= 4 ? argv[3] : "REC_OPUS_SMOKE";
    if (!output.parent_path().empty()) {
        std::filesystem::create_directories(output.parent_path());
    }
    TjiScBuffer file{};
    TjiScOpusMetadata metadata{};
    if (tji_sc_encode_ogg_opus(
            pcm.data(), pcm.size(), record_id,
            sample_rate, 1, 20, 24000,
            &file, &metadata) != TJI_SC_OK) {
        return 1;
    }
    std::ofstream stream(output, std::ios::binary);
    stream.write(reinterpret_cast<const char *>(file.data),
                 static_cast<std::streamsize>(file.size));
    const bool ok = stream.good();
    tji_sc_free(&file);
    if (!ok) {
        return 1;
    }
    std::cout << "file=" << output.string() << '\n'
              << "deviceId=" << device_id << '\n'
              << "recordId=" << record_id << '\n'
              << "name=Opus smoke test\n"
              << "fileSize=" << metadata.file_size << '\n'
              << "crc32=" << metadata.crc32 << '\n'
              << "durationMs=" << metadata.duration_ms << '\n'
              << "container=ogg\n"
              << "codec=opus\n"
              << "sampleRate=" << metadata.sample_rate << '\n'
              << "channels=" << metadata.channels << '\n'
              << "packetMs=" << metadata.packet_ms << '\n'
              << "bitrate=" << metadata.bitrate << '\n';
    return 0;
}

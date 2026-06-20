#include "tji_speaker_core.h"

#include <cmath>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

namespace {

constexpr double kPi = 3.14159265358979323846;

std::vector<uint8_t> generate_sine_pcm16(int sample_rate, int duration_ms, double frequency_hz)
{
    const int samples = sample_rate * duration_ms / 1000;
    std::vector<uint8_t> pcm;
    pcm.reserve(static_cast<size_t>(samples) * 2);
    for (int i = 0; i < samples; ++i) {
        const double phase = 2.0 * kPi * frequency_hz * static_cast<double>(i) / static_cast<double>(sample_rate);
        const auto sample = static_cast<int16_t>(std::sin(phase) * 12000.0);
        pcm.push_back(static_cast<uint8_t>(sample & 0xFF));
        pcm.push_back(static_cast<uint8_t>((sample >> 8) & 0xFF));
    }
    return pcm;
}

bool write_file(const std::filesystem::path &path, const uint8_t *data, size_t size)
{
    std::filesystem::create_directories(path.parent_path());
    std::ofstream out(path, std::ios::binary);
    if (!out) {
        return false;
    }
    out.write(reinterpret_cast<const char *>(data), static_cast<std::streamsize>(size));
    return out.good();
}

} // namespace

int main(int argc, char **argv)
{
    const std::filesystem::path output_path =
        argc >= 2 ? std::filesystem::path(argv[1]) : std::filesystem::path("generated/REC_CPP_CORE_SMOKE.hadp");
    const std::string device_id = argc >= 3 ? argv[2] : "T12345678";
    const std::string record_id = argc >= 4 ? argv[3] : "REC_CPP_CORE_SMOKE";
    constexpr int sample_rate = 8000;
    constexpr int channels = 1;
    constexpr int packet_ms = 40;
    constexpr int duration_ms = 1000;

    const std::vector<uint8_t> pcm = generate_sine_pcm16(sample_rate, duration_ms, 440.0);
    TjiScBuffer hadp{};
    TjiScHadpMetadata metadata{};
    const int status = tji_sc_encode_hadp(
        pcm.data(),
        pcm.size(),
        record_id.c_str(),
        TJI_SC_CODEC_IMA_ADPCM,
        sample_rate,
        channels,
        packet_ms,
        &hadp,
        &metadata
    );
    if (status != TJI_SC_OK) {
        std::cerr << "encode_status=" << status << '\n';
        return 1;
    }

    const bool written = write_file(output_path, hadp.data, hadp.size);
    tji_sc_free(&hadp);
    if (!written) {
        std::cerr << "write_failed=" << output_path << '\n';
        return 1;
    }

    std::cout << "file=" << output_path.string() << '\n';
    std::cout << "deviceId=" << device_id << '\n';
    std::cout << "recordId=" << record_id << '\n';
    std::cout << "name=C++ core smoke sample" << '\n';
    std::cout << "fileSize=" << metadata.file_size << '\n';
    std::cout << "crc32=" << metadata.crc32 << '\n';
    std::cout << "durationMs=" << metadata.duration_ms << '\n';
    std::cout << "codec=ima_adpcm" << '\n';
    std::cout << "sampleRate=" << metadata.sample_rate << '\n';
    std::cout << "channels=" << metadata.channels << '\n';
    std::cout << "packetMs=" << metadata.packet_ms << '\n';
    std::cout << "frameBytes=" << metadata.frame_bytes << '\n';
    std::cout << "samplesPerFrame=" << metadata.samples_per_frame << '\n';
    return 0;
}

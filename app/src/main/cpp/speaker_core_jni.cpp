#include "tji_speaker_core.h"

#include <jni.h>
#include <opus.h>

#include <array>
#include <cstdint>
#include <string>
#include <vector>

namespace {

struct RealtimeOpusDecoder {
    OpusDecoder *decoder = nullptr;
    int sample_rate = 0;
    int samples_per_packet = 0;

    ~RealtimeOpusDecoder() {
        opus_decoder_destroy(decoder);
    }
};

std::vector<uint8_t> to_vector(JNIEnv *env, jbyteArray array)
{
    if (array == nullptr) {
        return {};
    }
    const jsize size = env->GetArrayLength(array);
    std::vector<uint8_t> data(static_cast<size_t>(size));
    if (size > 0) {
        env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte *>(data.data()));
    }
    return data;
}

std::string to_string(JNIEnv *env, jstring value)
{
    if (value == nullptr) {
        return {};
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jbyteArray to_jbyte_array(JNIEnv *env, const TjiScBuffer &buffer)
{
    auto *array = env->NewByteArray(static_cast<jsize>(buffer.size));
    if (array == nullptr) {
        return nullptr;
    }
    if (buffer.size > 0) {
        env->SetByteArrayRegion(array, 0, static_cast<jsize>(buffer.size), reinterpret_cast<const jbyte *>(buffer.data));
    }
    return array;
}

void throw_illegal_state(JNIEnv *env, const char *message)
{
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
    }
}

jbyteArray encode_ogg_opus(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jstring record_id,
    jint sample_rate,
    jint channels,
    jint packet_ms,
    jint bitrate
)
{
    const auto pcm = to_vector(env, pcm16le);
    const auto record = to_string(env, record_id);
    TjiScBuffer out{};
    TjiScOpusMetadata metadata{};
    const int status = tji_sc_encode_ogg_opus(
        pcm.data(),
        pcm.size(),
        record.c_str(),
        sample_rate,
        channels,
        packet_ms,
        bitrate,
        &out,
        &metadata
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_encode_ogg_opus failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jlong create_realtime_opus_decoder(JNIEnv *, jclass, jint sample_rate, jint packet_ms)
{
    if ((sample_rate != 8000 && sample_rate != 12000 && sample_rate != 16000 &&
         sample_rate != 24000 && sample_rate != 48000) || packet_ms != 20) {
        return 0;
    }
    int error = OPUS_OK;
    auto *state = new RealtimeOpusDecoder();
    state->decoder = opus_decoder_create(sample_rate, 1, &error);
    if (state->decoder == nullptr || error != OPUS_OK) {
        delete state;
        return 0;
    }
    state->sample_rate = sample_rate;
    state->samples_per_packet = sample_rate / 50;
    return reinterpret_cast<jlong>(state);
}

void free_realtime_opus_decoder(JNIEnv *, jclass, jlong handle)
{
    delete reinterpret_cast<RealtimeOpusDecoder *>(handle);
}

jbyteArray decode_realtime_opus(
    JNIEnv *env,
    jclass,
    jlong handle,
    jbyteArray payload,
    jboolean packet_lost
)
{
    auto *state = reinterpret_cast<RealtimeOpusDecoder *>(handle);
    if (state == nullptr || state->decoder == nullptr) {
        throw_illegal_state(env, "invalid realtime Opus decoder");
        return nullptr;
    }
    const auto encoded = to_vector(env, payload);
    if ((packet_lost == JNI_FALSE && encoded.empty()) ||
        (packet_lost == JNI_FALSE && encoded.size() > 1275U)) {
        throw_illegal_state(env, "invalid realtime Opus packet");
        return nullptr;
    }
    std::array<opus_int16, 960> pcm{};
    const int decoded = opus_decode(
        state->decoder,
        packet_lost == JNI_TRUE ? nullptr : encoded.data(),
        packet_lost == JNI_TRUE ? 0 : static_cast<opus_int32>(encoded.size()),
        pcm.data(), state->samples_per_packet, 0
    );
    if (decoded != state->samples_per_packet) {
        throw_illegal_state(env, "realtime Opus decode failed");
        return nullptr;
    }
    auto *result = env->NewByteArray(static_cast<jsize>(decoded * 2));
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(decoded * 2),
                                reinterpret_cast<const jbyte *>(pcm.data()));
    }
    return result;
}

jbyteArray build_standard_command_json(
    JNIEnv *env,
    jclass,
    jstring device_id,
    jstring msg_id,
    jint command_code,
    jstring command_name,
    jlong timestamp_ms,
    jstring params_json,
    jstring extra_json
)
{
    const auto device = to_string(env, device_id);
    const auto msg = to_string(env, msg_id);
    const auto command = to_string(env, command_name);
    const auto params = to_string(env, params_json);
    const auto extra = to_string(env, extra_json);
    TjiScBuffer out{};
    const int status = tji_sc_build_standard_command_json(
        device.c_str(),
        msg.c_str(),
        command_code,
        command.c_str(),
        timestamp_ms,
        params.c_str(),
        extra.c_str(),
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_build_standard_command_json failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray resample_pcm16(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jint source_sample_rate,
    jint target_sample_rate
)
{
    const auto pcm = to_vector(env, pcm16le);
    TjiScBuffer out{};
    const int status = tji_sc_resample_pcm16(
        pcm.data(),
        pcm.size(),
        source_sample_rate,
        target_sample_rate,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_resample_pcm16 failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray generate_tone_pcm16(
    JNIEnv *env,
    jclass,
    jint frequency_hz,
    jint duration_ms,
    jint sample_rate,
    jint min_duration_ms,
    jint fade_ms,
    jfloat amplitude
)
{
    TjiScBuffer out{};
    const int status = tji_sc_generate_tone_pcm16(
        frequency_hz,
        duration_ms,
        sample_rate,
        min_duration_ms,
        fade_ms,
        amplitude,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_generate_tone_pcm16 failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray prepend_silence_pcm16(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jint duration_ms,
    jint sample_rate
)
{
    const auto pcm = to_vector(env, pcm16le);
    TjiScBuffer out{};
    const int status = tji_sc_prepend_silence_pcm16(pcm.data(), pcm.size(), duration_ms, sample_rate, &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_prepend_silence_pcm16 failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray decode_wav_pcm16_mono(
    JNIEnv *env,
    jclass,
    jbyteArray wav,
    jint target_sample_rate
)
{
    const auto data = to_vector(env, wav);
    TjiScBuffer out{};
    const int status = tji_sc_decode_wav_pcm16_mono(data.data(), data.size(), target_sample_rate, &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_decode_wav_pcm16_mono failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray parse_mqtt_state_json(
    JNIEnv *env,
    jclass,
    jstring serial_number,
    jstring payload_json,
    jboolean allow_online
)
{
    const auto serial = to_string(env, serial_number);
    const auto payload = to_string(env, payload_json);
    TjiScBuffer out{};
    const int status = tji_sc_parse_mqtt_state_json(serial.c_str(), payload.c_str(), allow_online == JNI_TRUE ? 1 : 0, &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_parse_mqtt_state_json failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray parse_mqtt_ack_json(JNIEnv *env, jclass, jstring payload_json)
{
    const auto payload = to_string(env, payload_json);
    TjiScBuffer out{};
    const int status = tji_sc_parse_mqtt_ack_json(payload.c_str(), &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_parse_mqtt_ack_json failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray parse_mqtt_record_list_json(JNIEnv *env, jclass, jstring payload_json)
{
    const auto payload = to_string(env, payload_json);
    TjiScBuffer out{};
    const int status = tji_sc_parse_mqtt_record_list_json(payload.c_str(), &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_parse_mqtt_record_list_json failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray parse_mqtt_storage_status_json(JNIEnv *env, jclass, jstring payload_json)
{
    const auto payload = to_string(env, payload_json);
    TjiScBuffer out{};
    const int status = tji_sc_parse_mqtt_storage_status_json(payload.c_str(), &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_parse_mqtt_storage_status_json failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray parse_mqtt_record_event_json(
    JNIEnv *env,
    jclass,
    jstring event_type,
    jstring payload_json
)
{
    const auto event = to_string(env, event_type);
    const auto payload = to_string(env, payload_json);
    TjiScBuffer out{};
    const int status = tji_sc_parse_mqtt_record_event_json(event.c_str(), payload.c_str(), &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_parse_mqtt_record_event_json failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

const JNINativeMethod kMethods[] = {
    {
        "nativeEncodeOggOpus",
        "([BLjava/lang/String;IIII)[B",
        reinterpret_cast<void *>(encode_ogg_opus)
    },
    {
        "nativeCreateRealtimeOpusDecoder",
        "(II)J",
        reinterpret_cast<void *>(create_realtime_opus_decoder)
    },
    {
        "nativeFreeRealtimeOpusDecoder",
        "(J)V",
        reinterpret_cast<void *>(free_realtime_opus_decoder)
    },
    {
        "nativeDecodeRealtimeOpus",
        "(J[BZ)[B",
        reinterpret_cast<void *>(decode_realtime_opus)
    },
    {
        "nativeBuildStandardCommandJson",
        "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;JLjava/lang/String;Ljava/lang/String;)[B",
        reinterpret_cast<void *>(build_standard_command_json)
    },
    {
        "nativeResamplePcm16",
        "([BII)[B",
        reinterpret_cast<void *>(resample_pcm16)
    },
    {
        "nativeGenerateTonePcm16",
        "(IIIIIF)[B",
        reinterpret_cast<void *>(generate_tone_pcm16)
    },
    {
        "nativePrependSilencePcm16",
        "([BII)[B",
        reinterpret_cast<void *>(prepend_silence_pcm16)
    },
    {
        "nativeDecodeWavPcm16Mono",
        "([BI)[B",
        reinterpret_cast<void *>(decode_wav_pcm16_mono)
    },
    {
        "nativeParseMqttStateJson",
        "(Ljava/lang/String;Ljava/lang/String;Z)[B",
        reinterpret_cast<void *>(parse_mqtt_state_json)
    },
    {
        "nativeParseMqttAckJson",
        "(Ljava/lang/String;)[B",
        reinterpret_cast<void *>(parse_mqtt_ack_json)
    },
    {
        "nativeParseMqttRecordListJson",
        "(Ljava/lang/String;)[B",
        reinterpret_cast<void *>(parse_mqtt_record_list_json)
    },
    {
        "nativeParseMqttStorageStatusJson",
        "(Ljava/lang/String;)[B",
        reinterpret_cast<void *>(parse_mqtt_storage_status_json)
    },
    {
        "nativeParseMqttRecordEventJson",
        "(Ljava/lang/String;Ljava/lang/String;)[B",
        reinterpret_cast<void *>(parse_mqtt_record_event_json)
    },
};

} // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *)
{
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }
    jclass cls = env->FindClass("com/tji/device/product/speaker/core/SpeakerCoreNative");
    if (cls == nullptr) {
        return JNI_ERR;
    }
    if (env->RegisterNatives(cls, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

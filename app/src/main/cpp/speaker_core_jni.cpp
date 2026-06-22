#include "tji_speaker_core.h"

#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

namespace {

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

std::vector<float> to_float_vector(JNIEnv *env, jfloatArray array)
{
    if (array == nullptr) {
        return {};
    }
    const jsize size = env->GetArrayLength(array);
    std::vector<float> data(static_cast<size_t>(size));
    if (size > 0) {
        env->GetFloatArrayRegion(array, 0, size, reinterpret_cast<jfloat *>(data.data()));
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

jbyteArray encode_hadp(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jstring record_id,
    jint codec_id,
    jint sample_rate,
    jint channels,
    jint packet_ms
)
{
    const auto pcm = to_vector(env, pcm16le);
    const auto record = to_string(env, record_id);
    TjiScBuffer out{};
    TjiScHadpMetadata metadata{};
    const int status = tji_sc_encode_hadp(
        pcm.data(),
        pcm.size(),
        record.c_str(),
        codec_id,
        sample_rate,
        channels,
        packet_ms,
        &out,
        &metadata
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_encode_hadp failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray packetize_legacy(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jint sequence,
    jint timestamp_samples
)
{
    const auto pcm = to_vector(env, pcm16le);
    TjiScBuffer out{};
    const int status = tji_sc_packetize_adpcm_legacy(
        pcm.data(),
        pcm.size(),
        static_cast<uint32_t>(sequence),
        static_cast<uint32_t>(timestamp_samples),
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_packetize_adpcm_legacy failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray decode_hadp_pcm16(
    JNIEnv *env,
    jclass,
    jbyteArray hadp
)
{
    const auto data = to_vector(env, hadp);
    TjiScBuffer out{};
    const int status = tji_sc_decode_hadp_pcm16(data.data(), data.size(), &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_decode_hadp_pcm16 failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray packetize_v2(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jint sequence,
    jint timestamp_ms,
    jstring device_id,
    jstring task_id,
    jstring talk_id,
    jint stream_type,
    jboolean is_last_packet
)
{
    const auto pcm = to_vector(env, pcm16le);
    const auto device = to_string(env, device_id);
    const auto task = to_string(env, task_id);
    const auto talk = to_string(env, talk_id);
    TjiScBuffer out{};
    const int status = tji_sc_packetize_adpcm_v2(
        pcm.data(),
        pcm.size(),
        static_cast<uint32_t>(sequence),
        static_cast<uint32_t>(timestamp_ms),
        device.c_str(),
        task.c_str(),
        talk.c_str(),
        stream_type,
        is_last_packet == JNI_TRUE ? 1 : 0,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_packetize_adpcm_v2 failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jlong create_packetizer(JNIEnv *, jclass)
{
    TjiScAdpcmPacketizer *packetizer = nullptr;
    const int status = tji_sc_adpcm_packetizer_create(&packetizer);
    if (status != TJI_SC_OK) {
        return 0;
    }
    return reinterpret_cast<jlong>(packetizer);
}

void free_packetizer(JNIEnv *, jclass, jlong handle)
{
    auto *packetizer = reinterpret_cast<TjiScAdpcmPacketizer *>(handle);
    tji_sc_adpcm_packetizer_free(packetizer);
}

void reset_packetizer(JNIEnv *, jclass, jlong handle)
{
    auto *packetizer = reinterpret_cast<TjiScAdpcmPacketizer *>(handle);
    tji_sc_adpcm_packetizer_reset(packetizer);
}

jbyteArray packetizer_packetize_legacy(
    JNIEnv *env,
    jclass,
    jlong handle,
    jbyteArray pcm16le,
    jint sequence,
    jint timestamp_samples
)
{
    auto *packetizer = reinterpret_cast<TjiScAdpcmPacketizer *>(handle);
    const auto pcm = to_vector(env, pcm16le);
    TjiScBuffer out{};
    const int status = tji_sc_adpcm_packetizer_packetize_legacy(
        packetizer,
        pcm.data(),
        pcm.size(),
        static_cast<uint32_t>(sequence),
        static_cast<uint32_t>(timestamp_samples),
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_adpcm_packetizer_packetize_legacy failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray packetizer_packetize_v2(
    JNIEnv *env,
    jclass,
    jlong handle,
    jbyteArray pcm16le,
    jint sequence,
    jint timestamp_ms,
    jstring device_id,
    jstring task_id,
    jstring talk_id,
    jint stream_type,
    jboolean is_last_packet
)
{
    auto *packetizer = reinterpret_cast<TjiScAdpcmPacketizer *>(handle);
    const auto pcm = to_vector(env, pcm16le);
    const auto device = to_string(env, device_id);
    const auto task = to_string(env, task_id);
    const auto talk = to_string(env, talk_id);
    TjiScBuffer out{};
    const int status = tji_sc_adpcm_packetizer_packetize_v2(
        packetizer,
        pcm.data(),
        pcm.size(),
        static_cast<uint32_t>(sequence),
        static_cast<uint32_t>(timestamp_ms),
        device.c_str(),
        task.c_str(),
        talk.c_str(),
        stream_type,
        is_last_packet == JNI_TRUE ? 1 : 0,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_adpcm_packetizer_packetize_v2 failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jbyteArray process_voice(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jint profile,
    jint sample_rate,
    jfloat bass_db,
    jfloat treble_db
)
{
    const auto pcm = to_vector(env, pcm16le);
    TjiScBuffer out{};
    const int status = tji_sc_process_voice(
        pcm.data(),
        pcm.size(),
        profile,
        sample_rate,
        bass_db,
        treble_db,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_process_voice failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
    return result;
}

jlong create_voice_processor(JNIEnv *, jclass)
{
    TjiScVoiceProcessor *processor = nullptr;
    const int status = tji_sc_voice_processor_create(&processor);
    if (status != TJI_SC_OK) {
        return 0;
    }
    return reinterpret_cast<jlong>(processor);
}

void free_voice_processor(JNIEnv *, jclass, jlong handle)
{
    auto *processor = reinterpret_cast<TjiScVoiceProcessor *>(handle);
    tji_sc_voice_processor_free(processor);
}

void reset_voice_processor(JNIEnv *, jclass, jlong handle)
{
    auto *processor = reinterpret_cast<TjiScVoiceProcessor *>(handle);
    tji_sc_voice_processor_reset(processor);
}

jbyteArray voice_processor_process_frame(
    JNIEnv *env,
    jclass,
    jlong handle,
    jbyteArray pcm16le,
    jfloat bass_db,
    jfloat treble_db
)
{
    auto *processor = reinterpret_cast<TjiScVoiceProcessor *>(handle);
    const auto pcm = to_vector(env, pcm16le);
    TjiScBuffer out{};
    const int status = tji_sc_voice_processor_process_frame(
        processor,
        pcm.data(),
        pcm.size(),
        bass_db,
        treble_db,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_voice_processor_process_frame failed");
        return nullptr;
    }
    jbyteArray result = to_jbyte_array(env, out);
    tji_sc_free(&out);
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

jbyteArray build_record_download_command_json(
    JNIEnv *env,
    jclass,
    jstring device_id,
    jstring msg_id,
    jstring record_id,
    jstring store_task_id,
    jstring created_at,
    jstring name,
    jstring download_url,
    jlong file_size,
    jstring crc32,
    jint duration_ms,
    jstring codec,
    jint sample_rate,
    jint channels,
    jint packet_ms,
    jint frame_bytes,
    jint samples_per_frame,
    jboolean verify_only,
    jstring verify_kind,
    jstring expected_audio_crc32,
    jstring expected_first_samples_json,
    jboolean temporary,
    jboolean visible,
    jboolean auto_play,
    jint playback_volume,
    jboolean has_playback_volume
)
{
    const auto device = to_string(env, device_id);
    const auto msg = to_string(env, msg_id);
    const auto record = to_string(env, record_id);
    const auto task = to_string(env, store_task_id);
    const auto created = to_string(env, created_at);
    const auto display_name = to_string(env, name);
    const auto url = to_string(env, download_url);
    const auto file_crc32 = to_string(env, crc32);
    const auto codec_name = to_string(env, codec);
    const auto verify_kind_value = to_string(env, verify_kind);
    const auto expected_crc = to_string(env, expected_audio_crc32);
    const auto first_samples = to_string(env, expected_first_samples_json);
    TjiScBuffer out{};
    const int status = tji_sc_build_record_download_command_json(
        device.c_str(),
        msg.c_str(),
        record.c_str(),
        task.c_str(),
        created.c_str(),
        display_name.c_str(),
        url.c_str(),
        file_size,
        file_crc32.c_str(),
        duration_ms,
        codec_name.c_str(),
        sample_rate,
        channels,
        packet_ms,
        frame_bytes,
        samples_per_frame,
        verify_only == JNI_TRUE ? 1 : 0,
        verify_kind_value.c_str(),
        expected_crc.c_str(),
        first_samples.c_str(),
        temporary == JNI_TRUE ? 1 : 0,
        visible == JNI_TRUE ? 1 : 0,
        auto_play == JNI_TRUE ? 1 : 0,
        playback_volume,
        has_playback_volume == JNI_TRUE ? 1 : 0,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_build_record_download_command_json failed");
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

jbyteArray pad_pcm16_to_frame(
    JNIEnv *env,
    jclass,
    jbyteArray pcm16le,
    jint frame_bytes
)
{
    const auto pcm = to_vector(env, pcm16le);
    TjiScBuffer out{};
    const int status = tji_sc_pad_pcm16_to_frame(pcm.data(), pcm.size(), static_cast<size_t>(frame_bytes), &out);
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_pad_pcm16_to_frame failed");
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

jbyteArray float32_to_pcm16(
    JNIEnv *env,
    jclass,
    jfloatArray samples,
    jint source_sample_rate,
    jint target_sample_rate
)
{
    const auto data = to_float_vector(env, samples);
    TjiScBuffer out{};
    const int status = tji_sc_float32_to_pcm16(
        data.data(),
        data.size(),
        source_sample_rate,
        target_sample_rate,
        &out
    );
    if (status != TJI_SC_OK) {
        tji_sc_free(&out);
        throw_illegal_state(env, "tji_sc_float32_to_pcm16 failed");
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
        "nativeEncodeHadp",
        "([BLjava/lang/String;IIII)[B",
        reinterpret_cast<void *>(encode_hadp)
    },
    {
        "nativePacketizeLegacy",
        "([BII)[B",
        reinterpret_cast<void *>(packetize_legacy)
    },
    {
        "nativeDecodeHadpPcm16",
        "([B)[B",
        reinterpret_cast<void *>(decode_hadp_pcm16)
    },
    {
        "nativePacketizeV2",
        "([BIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ)[B",
        reinterpret_cast<void *>(packetize_v2)
    },
    {
        "nativeCreatePacketizer",
        "()J",
        reinterpret_cast<void *>(create_packetizer)
    },
    {
        "nativeFreePacketizer",
        "(J)V",
        reinterpret_cast<void *>(free_packetizer)
    },
    {
        "nativeResetPacketizer",
        "(J)V",
        reinterpret_cast<void *>(reset_packetizer)
    },
    {
        "nativePacketizerPacketizeLegacy",
        "(J[BII)[B",
        reinterpret_cast<void *>(packetizer_packetize_legacy)
    },
    {
        "nativePacketizerPacketizeV2",
        "(J[BIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ)[B",
        reinterpret_cast<void *>(packetizer_packetize_v2)
    },
    {
        "nativeProcessVoice",
        "([BIIFF)[B",
        reinterpret_cast<void *>(process_voice)
    },
    {
        "nativeCreateVoiceProcessor",
        "()J",
        reinterpret_cast<void *>(create_voice_processor)
    },
    {
        "nativeFreeVoiceProcessor",
        "(J)V",
        reinterpret_cast<void *>(free_voice_processor)
    },
    {
        "nativeResetVoiceProcessor",
        "(J)V",
        reinterpret_cast<void *>(reset_voice_processor)
    },
    {
        "nativeVoiceProcessorProcessFrame",
        "(J[BFF)[B",
        reinterpret_cast<void *>(voice_processor_process_frame)
    },
    {
        "nativeBuildStandardCommandJson",
        "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;JLjava/lang/String;Ljava/lang/String;)[B",
        reinterpret_cast<void *>(build_standard_command_json)
    },
    {
        "nativeBuildRecordDownloadCommandJson",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;ILjava/lang/String;IIIIIZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZIZ)[B",
        reinterpret_cast<void *>(build_record_download_command_json)
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
        "nativePadPcm16ToFrame",
        "([BI)[B",
        reinterpret_cast<void *>(pad_pcm16_to_frame)
    },
    {
        "nativeDecodeWavPcm16Mono",
        "([BI)[B",
        reinterpret_cast<void *>(decode_wav_pcm16_mono)
    },
    {
        "nativeFloat32ToPcm16",
        "([FII)[B",
        reinterpret_cast<void *>(float32_to_pcm16)
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

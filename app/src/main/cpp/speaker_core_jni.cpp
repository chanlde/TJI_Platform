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
    const int status = tji_sc_encode_hadp(
        pcm.data(),
        pcm.size(),
        record.c_str(),
        codec_id,
        sample_rate,
        channels,
        packet_ms,
        &out,
        nullptr
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
        "nativePacketizeV2",
        "([BIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;IZ)[B",
        reinterpret_cast<void *>(packetize_v2)
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

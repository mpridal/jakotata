#include <jni.h>

#include <memory>
#include <mutex>

#include "audio_engine.h"
#include "jakotata/version.h"
#include "jakotata/voice.h"

using namespace jakotata;

namespace {
std::mutex gLock;
std::unique_ptr<AudioEngine> gEngine;

AudioEngine& engine() {
    std::lock_guard<std::mutex> g(gLock);
    if (!gEngine) gEngine = std::make_unique<AudioEngine>();
    return *gEngine;
}
}  // namespace

#define JNI_FN(name) Java_io_github_mpridal_jakotata_core_voice_NativeVoice_##name

extern "C" {

JNIEXPORT jstring JNICALL JNI_FN(nativeVersion)(JNIEnv* env, jobject) {
    return env->NewStringUTF(voiceCoreVersion());
}

JNIEXPORT jboolean JNICALL JNI_FN(nativeStart)(JNIEnv*, jobject) { return engine().startEngine(); }
JNIEXPORT void JNICALL JNI_FN(nativeStop)(JNIEnv*, jobject) { engine().stopEngine(); }
JNIEXPORT void JNICALL JNI_FN(nativeSetMode)(JNIEnv*, jobject, jint mode) {
    engine().setMode(static_cast<SoundMode>(mode));
}
JNIEXPORT void JNICALL JNI_FN(nativeSetVoiceEnabled)(JNIEnv*, jobject, jboolean on) { engine().setVoiceEnabled(on); }
JNIEXPORT void JNICALL JNI_FN(nativeSetAmount)(JNIEnv*, jobject, jfloat a) { engine().setAmount(a); }

/** [medianF0, F1, F2, F3, hasBands, band0..band9] */
JNIEXPORT void JNICALL JNI_FN(nativeSetDad)(JNIEnv* env, jobject, jfloatArray data) {
    VoiceParams p;
    if (data != nullptr && env->GetArrayLength(data) >= 5 + kBands) {
        jfloat* d = env->GetFloatArrayElements(data, nullptr);
        p.medianF0 = d[0];
        p.formants = {d[1], d[2], d[3]};
        p.hasBands = d[4] > 0.5f;
        for (int i = 0; i < kBands; ++i) p.bandsDb[i] = d[5 + i];
        env->ReleaseFloatArrayElements(data, d, JNI_ABORT);
    }
    engine().setDad(p);
}

JNIEXPORT jfloat JNICALL JNI_FN(nativeInputLevel)(JNIEnv*, jobject) { return engine().inputLevel(); }
JNIEXPORT jint JNICALL JNI_FN(nativeParrotState)(JNIEnv*, jobject) { return engine().parrotState(); }
JNIEXPORT jfloat JNICALL JNI_FN(nativeKidF0)(JNIEnv*, jobject) { return engine().kidF0(); }
JNIEXPORT jfloat JNICALL JNI_FN(nativeLiveLatencyMs)(JNIEnv*, jobject) { return engine().liveLatencyMs(); }
JNIEXPORT jint JNICALL JNI_FN(nativeMeasureRoundTrip)(JNIEnv*, jobject) { return engine().measureRoundTripMs(); }

JNIEXPORT void JNICALL JNI_FN(nativeSetRecording)(JNIEnv*, jobject, jboolean on) { engine().setRecording(on); }
JNIEXPORT jlong JNICALL JNI_FN(nativeRecordingStartNs)(JNIEnv*, jobject) { return engine().recordingStartNs(); }
JNIEXPORT jlong JNICALL JNI_FN(nativeRecordingLatencyNs)(JNIEnv*, jobject) { return engine().recordingLatencyNs(); }
JNIEXPORT jint JNICALL JNI_FN(nativeReadRecording)(JNIEnv* env, jobject, jfloatArray dst) {
    jsize max = env->GetArrayLength(dst);
    jfloat* d = env->GetFloatArrayElements(dst, nullptr);
    int n = engine().readRecording(d, max);
    env->ReleaseFloatArrayElements(dst, d, 0);
    return n;
}

JNIEXPORT jfloatArray JNICALL JNI_FN(nativeAnalyze)(JNIEnv* env, jobject, jfloatArray pcm, jint sampleRate) {
    jsize n = env->GetArrayLength(pcm);
    jfloat* d = env->GetFloatArrayElements(pcm, nullptr);
    VoiceAnalysis a = analyzeVoice(d, n, static_cast<float>(sampleRate));
    env->ReleaseFloatArrayElements(pcm, d, JNI_ABORT);
    float out[VoiceAnalysis::kSize];
    a.toArray(out);
    jfloatArray result = env->NewFloatArray(VoiceAnalysis::kSize);
    env->SetFloatArrayRegion(result, 0, VoiceAnalysis::kSize, out);
    return result;
}

}  // extern "C"

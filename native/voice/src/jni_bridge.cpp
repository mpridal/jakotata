#include <jni.h>

#include "jakotata/version.h"

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_mpridal_jakotata_core_voice_NativeVoice_nativeVersion(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF(jakotata::voiceCoreVersion());
}

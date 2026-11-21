// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// From chiaki-ng v1.10.1, with VI Translator's package.

#ifndef CHIAKI_JNI_H
#define CHIAKI_JNI_H

#define JNI_VERSION JNI_VERSION_1_6

#define BASE_PACKAGE "app/vitranslator/phone/ps/chiaki"
#define JNI_FCN(name) Java_app_vitranslator_phone_ps_chiaki_ChiakiNative_##name

#define E (*env)

JNIEnv *attach_thread_jni();
jobject jnistr_from_ascii(JNIEnv *env, const char *str);

extern JavaVM *global_vm;

#endif
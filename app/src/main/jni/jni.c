/*
 * CatSaver
 * Copyright (C) 2015 HiHex Ltd.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

#include <stdlib.h>
#include <jni.h>
#include <zlib.h>

JNIEXPORT jlong JNICALL Java_hihex_cs_FlushableGzipOutputStream_nativeCreate(JNIEnv* env, jclass cls, jstring javaFilename) {
    const char* filename = (*env)->GetStringUTFChars(env, javaFilename, NULL);
    gzFile f = gzopen(filename, "w");
    (*env)->ReleaseStringUTFChars(env, javaFilename, filename);
    return (jlong) f;
}

JNIEXPORT void JNICALL Java_hihex_cs_FlushableGzipOutputStream_nativeClose(JNIEnv* env, jclass cls, jlong ptr) {
    if (ptr != 0) {
        gzFile f = (gzFile) ptr;
        gzclose(f);
    }
}

JNIEXPORT void JNICALL Java_hihex_cs_FlushableGzipOutputStream_nativeWrite(JNIEnv* env, jclass cls, jlong ptr,
                                                                           jbyteArray buf, jint off, jint len) {
    if (ptr != 0) {
        gzFile f = (gzFile) ptr;
        jbyte* content = (*env)->GetByteArrayElements(env, buf, NULL);
        gzwrite(f, content + off, len);
        (*env)->ReleaseByteArrayElements(env, buf, content, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL Java_hihex_cs_FlushableGzipOutputStream_nativePutc(JNIEnv* env, jclass cls, jlong ptr, jint c) {
    if (ptr != 0) {
        gzFile f = (gzFile) ptr;
        gzputc(f, c);
    }
}

JNIEXPORT void JNICALL Java_hihex_cs_FlushableGzipOutputStream_nativeFlush(JNIEnv* env, jclass cls, jlong ptr) {
    if (ptr != 0) {
        gzFile f = (gzFile) ptr;
        gzflush(f, Z_FULL_FLUSH);
    }
}

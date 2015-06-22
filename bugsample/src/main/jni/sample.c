#include <jni.h>
#include <pthread.h>

struct JniData {
    JavaVM* vm;
    jobject self;
};

void* faulty_thread(void* input) {
    struct JniData* arg = input;
    JNIEnv* env = NULL;

    if ((*(arg->vm))->AttachCurrentThreadAsDaemon(arg->vm, &env, NULL) == JNI_OK) {
        jclass cls = (*env)->GetObjectClass(env, arg->self);
        jmethodID method = (*env)->GetMethodID(env, cls, "throwNPE", "()V");

        int i;
        for (i = 0; i < 10; ++ i) {
            (*env)->CallVoidMethod(env, arg->self, method);
        }

        (*(arg->vm))->DetachCurrentThread(arg->vm);
    }

    return 0;
}

JNIEXPORT void JNICALL Java_hihex_samplebuggyapp_MainActivity_exitByJniSegFault(JNIEnv* env, jobject self, jobject view) {
    *(int*)0 = 0;
}

JNIEXPORT void JNICALL Java_hihex_samplebuggyapp_MainActivity_exitByExceptionFromJniThread(JNIEnv* env, jobject self, jobject view) {
    struct JniData* data = malloc(sizeof(*data));
    (*env)->GetJavaVM(env, &(data->vm));
    data->self = (*env)->NewGlobalRef(env, self);

    pthread_t thread;
    pthread_create(&thread, NULL, faulty_thread, data);
}




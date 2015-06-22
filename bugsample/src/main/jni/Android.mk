LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := bugsample
LOCAL_SRC_FILES := sample.c

include $(BUILD_SHARED_LIBRARY)


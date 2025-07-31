#include <jni.h>
#include "BlePrintAndScanOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return margelo::nitro::bleprintandscan::initialize(vm);
}

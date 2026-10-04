# The hfmwic JNI library (HEIC photos on Windows through Windows Imaging Component). Built by
# build-wic.ps1; see ../README.md. It links only Windows libraries and contains no codec.
cmake_minimum_required(VERSION 3.16)
project(hfmwic CXX)

set(JDK "" CACHE PATH "A JDK, for jni.h")

add_library(hfmwic SHARED hfm_wic.cpp)
target_include_directories(hfmwic PRIVATE "${JDK}/include" "${JDK}/include/win32")
target_link_libraries(hfmwic PRIVATE windowscodecs ole32 propsys)
set_property(TARGET hfmwic PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded")

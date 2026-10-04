# The hfmheif JNI library: hfm_heif.c linked with static builds of libheif and libde265.
# Built by build-heif.ps1 (Windows) and build-heif.sh (Linux); see ../README.md.
cmake_minimum_required(VERSION 3.16)
project(hfmheif C CXX)

set(DEPS "" CACHE PATH "Prefix where libheif and libde265 were installed")
set(JDK "" CACHE PATH "A JDK, for jni.h")

add_library(hfmheif SHARED hfm_heif.c)
set_target_properties(hfmheif PROPERTIES LINKER_LANGUAGE CXX)
target_compile_definitions(hfmheif PRIVATE LIBHEIF_STATIC_BUILD LIBDE265_STATIC_BUILD)
target_include_directories(hfmheif PRIVATE "${DEPS}/include" "${JDK}/include")

if(WIN32)
  target_include_directories(hfmheif PRIVATE "${JDK}/include/win32")
  target_link_libraries(hfmheif PRIVATE "${DEPS}/lib/heif.lib" "${DEPS}/lib/libde265.lib")
else()
  target_include_directories(hfmheif PRIVATE "${JDK}/include/linux")
  target_link_libraries(hfmheif PRIVATE "${DEPS}/lib/libheif.a" "${DEPS}/lib/libde265.a" pthread)
  # Only the JNI entry points are exported, and the C++ runtime goes inside, so the library needs
  # nothing beyond the C library.
  target_link_options(hfmheif PRIVATE -static-libstdc++ -static-libgcc
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map" -Wl,--no-undefined)
endif()

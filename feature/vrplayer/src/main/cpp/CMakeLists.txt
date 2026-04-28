cmake_minimum_required(VERSION 3.22.1)

project(vrplayer LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# OpenXR loader is delivered as an Android prefab AAR by Khronos. Gradle
# extracts it and exposes the imported target `OpenXR::openxr_loader` to CMake
# via the standard prefab mechanism.
find_package(OpenXR REQUIRED CONFIG)
find_package(game-activity QUIET CONFIG)  # not used; placeholder for future

# NDK ships android_native_app_glue under sources/android/native_app_glue.
set(NATIVE_APP_GLUE_DIR ${ANDROID_NDK}/sources/android/native_app_glue)

add_library(native_app_glue STATIC
    ${NATIVE_APP_GLUE_DIR}/android_native_app_glue.c
)
target_include_directories(native_app_glue PUBLIC ${NATIVE_APP_GLUE_DIR})

add_library(vrplayer SHARED
    main.cpp
    xr_session.cpp
    gl_renderer.cpp
    quad.cpp
    screen.cpp
    skybox.cpp
    sphere.cpp
    stereo.cpp
    hand_tracking.cpp
    native_calls.cpp
    pointer.cpp
    picker_quad.cpp
    subtitle_quad.cpp
    input.cpp
    video_bridge.cpp
)

target_include_directories(vrplayer PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# NativeActivity dlsym's ANativeActivity_onCreate at runtime; the linker
# would otherwise garbage-collect the unreferenced glue symbol from the
# static archive. -Wl,--whole-archive forces every object from the glue
# archive into libvrplayer.so. This is the pattern used by Khronos'
# hello_xr sample. -u as a single space-separated string in
# INTERFACE_LINK_OPTIONS doesn't survive CMake's option flattening, which
# is what caused the previous "undefined symbol: ANativeActivity_onCreate"
# UnsatisfiedLinkError on app launch.
target_link_options(vrplayer PRIVATE
    "LINKER:--whole-archive,$<TARGET_FILE:native_app_glue>,--no-whole-archive"
    "-u" "ANativeActivity_onCreate"
)

target_link_libraries(vrplayer
    native_app_glue
    OpenXR::openxr_loader
    android
    log
    EGL
    GLESv3
)

target_compile_options(vrplayer PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
)

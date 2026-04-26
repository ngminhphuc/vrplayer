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
# Force the entry-point symbol to be kept (NativeActivity dlsym's it).
set_target_properties(native_app_glue PROPERTIES
    INTERFACE_LINK_OPTIONS "-u ANativeActivity_onCreate")

add_library(vrplayer SHARED
    main.cpp
    xr_session.cpp
    gl_renderer.cpp
    quad.cpp
    screen.cpp
    skybox.cpp
    pointer.cpp
    input.cpp
    video_bridge.cpp
)

target_include_directories(vrplayer PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

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

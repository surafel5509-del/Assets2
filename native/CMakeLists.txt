# S Engine 2.0 -- Android NDK build.
#
# Wired into Gradle from app/build.gradle.kts:
#
#   android {
#       externalNativeBuild { cmake { path = file("../native/CMakeLists.txt") } }
#       defaultConfig { externalNativeBuild { cmake { arguments += ... } } }
#   }
#
# The host build (Makefile) covers the platform-independent core; this file adds
# the platform layer, the JNI bridge and the third-party integrations that only
# exist on device.

cmake_minimum_required(VERSION 3.22)
project(se_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(SE_BUILD_TESTS      "Build the host test binary"              OFF)
option(SE_USE_VULKAN       "Build the Vulkan backend"                ON)
option(SE_USE_GLES3        "Build the OpenGL ES 3.0 backend"         ON)
option(SE_USE_BULLET       "Link Bullet Physics"                     OFF)
option(SE_SCRIPT_QUICKJS   "Build the QuickJS scripting backend"     ON)
option(SE_PROFILER         "Enable the runtime profiler instrumentation" ON)

# ---------------------------------------------------------------------------
# Core: no Android, no GPU, no third-party dependencies.  This is exactly the
# set the host Makefile compiles and tests.
# ---------------------------------------------------------------------------
set(SE_CORE_SOURCES
    engine/core/Memory.cpp
    engine/ecs/World.cpp
    engine/scene/SceneGraph.cpp
)

# ---------------------------------------------------------------------------
# Platform-independent subsystems that still need to be compiled for device.
# Files listed here that do not exist yet are staged in progressively; keep the
# list in sync with what is actually on disk (see scripts/check_sources.py).
# ---------------------------------------------------------------------------
set(SE_SUBSYSTEM_SOURCES "")

foreach(_candidate
        engine/render/NullRHI.cpp
        engine/assets/AssetStore.cpp
        engine/audio/AudioMixer.cpp
        engine/input/InputSystem.cpp
        engine/profile/Profiler.cpp
        engine/script/ScriptVM.cpp
        engine/physics/PhysicsWorld.cpp)
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/${_candidate}")
        list(APPEND SE_SUBSYSTEM_SOURCES "${_candidate}")
    endif()
endforeach()

add_library(se_engine SHARED
    ${SE_CORE_SOURCES}
    ${SE_SUBSYSTEM_SOURCES}
    engine/platform/android/JniBridge.cpp
    engine/platform/android/AndroidFileSystem.cpp
)

target_include_directories(se_engine PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_options(se_engine PRIVATE
    -Wall -Wextra -Wno-unused-parameter
    -fno-exceptions -fno-rtti            # the engine does not throw; -fno-rtti
    -fvisibility=hidden                  # keeps the .so small and loads faster
    $<$<CONFIG:Release>:-O3 -flto>
    $<$<CONFIG:Debug>:-O0 -g -DSE_DEBUG>)

target_link_options(se_engine PRIVATE
    $<$<CONFIG:Release>:-flto -Wl,--gc-sections>)

target_compile_definitions(se_engine PRIVATE
    SE_PLATFORM_ANDROID=1
    $<$<BOOL:${SE_PROFILER}>:SE_PROFILER=1>)

# Android system libraries.
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(se_engine PRIVATE ${log-lib} ${android-lib})

# ---------------------------------------------------------------------------
# Graphics backends.
# ---------------------------------------------------------------------------
if(SE_USE_GLES3)
    find_library(gles3-lib GLESv3)
    find_library(egl-lib EGL)
    if(gles3-lib AND egl-lib)
        target_link_libraries(se_engine PRIVATE ${gles3-lib} ${egl-lib})
        target_compile_definitions(se_engine PRIVATE SE_HAS_GLES3=1)
    endif()
endif()

if(SE_USE_VULKAN)
    find_library(vulkan-lib vulkan)
    if(vulkan-lib)
        target_link_libraries(se_engine PRIVATE ${vulkan-lib})
        target_compile_definitions(se_engine PRIVATE SE_HAS_VULKAN=1)
    else()
        message(STATUS "S Engine: libvulkan not found; GLES 3 backend only")
    endif()
endif()

# ---------------------------------------------------------------------------
# Third-party integrations live in third_party/ and are added by their own
# CMakeLists.  They are optional so a clean checkout still builds.
# ---------------------------------------------------------------------------
if(SE_USE_BULLET AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/bullet3/CMakeLists.txt")
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(USE_MSVC_RUNTIME_LIBRARY_DLL OFF CACHE BOOL "" FORCE)
    set(BUILD_BULLET2_DEMOS OFF CACHE BOOL "" FORCE)
    set(BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
    set(BUILD_UNIT_TESTS OFF CACHE BOOL "" FORCE)
    add_subdirectory(third_party/bullet3)
    target_link_libraries(se_engine PRIVATE BulletDynamics BulletCollision LinearMath)
    target_compile_definitions(se_engine PRIVATE SE_HAS_BULLET=1)
endif()

if(SE_SCRIPT_QUICKJS AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/quickjs/CMakeLists.txt")
    add_subdirectory(third_party/quickjs)
    target_link_libraries(se_engine PRIVATE quickjs)
    target_compile_definitions(se_engine PRIVATE SE_HAS_QUICKJS=1)
endif()

# ---------------------------------------------------------------------------
# Optional host test target (the normal path is `make` in this directory).
# ---------------------------------------------------------------------------
if(SE_BUILD_TESTS)
    add_executable(se_engine_tests tests/test_engine.cpp ${SE_CORE_SOURCES} engine/render/NullRHI.cpp)
    target_include_directories(se_engine_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
endif()

cmake_minimum_required(VERSION 3.22.1)

project(tji_speaker_core VERSION 0.2.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(TJI_SPEAKER_CORE_BUILD_TESTS "Build speaker-core CTest executable" ${PROJECT_IS_TOP_LEVEL})
option(TJI_SPEAKER_CORE_BUILD_TOOLS "Build speaker-core command line tools" ${PROJECT_IS_TOP_LEVEL})

set(OPUS_BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_CUSTOM_MODES OFF CACHE BOOL "" FORCE)
set(OPUS_DISABLE_INTRINSICS ON CACHE BOOL "" FORCE)
set(OPUS_DRED OFF CACHE BOOL "" FORCE)
set(OPUS_OSCE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/opus
    ${CMAKE_CURRENT_BINARY_DIR}/third_party/opus
    EXCLUDE_FROM_ALL
)
if(ANDROID)
    # libopus explicitly warns that unoptimized decoder/encoder builds are
    # unusably slow; keep Debug APKs representative of the device audio path.
    target_compile_options(opus PRIVATE -O2)
endif()

add_library(tji_speaker_core
    src/audio_tools.cpp
    src/command_json.cpp
    src/ogg_opus_codec.cpp
    src/mqtt_json.cpp
    src/pcm_utils.cpp
    src/speaker_core_c_api.cpp
)

set_target_properties(tji_speaker_core PROPERTIES
    POSITION_INDEPENDENT_CODE ON
)

target_include_directories(tji_speaker_core
    PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/include
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
)

target_compile_features(tji_speaker_core PUBLIC cxx_std_20)
target_link_libraries(tji_speaker_core PRIVATE Opus::opus)

if(CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(tji_speaker_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

if(MSVC)
    target_compile_options(tji_speaker_core PRIVATE /utf-8)
endif()

if(TJI_SPEAKER_CORE_BUILD_TESTS)
    enable_testing()

    add_executable(tji_speaker_core_tests
        tests/speaker_core_tests.cpp
    )

    target_link_libraries(tji_speaker_core_tests PRIVATE tji_speaker_core)

    if(MSVC)
        target_compile_options(tji_speaker_core_tests PRIVATE /utf-8)
    endif()

    add_test(NAME tji_speaker_core_tests COMMAND tji_speaker_core_tests)
    set_tests_properties(tji_speaker_core_tests PROPERTIES
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    )
endif()

if(TJI_SPEAKER_CORE_BUILD_TOOLS)
    add_executable(tji_speaker_core_sample
        tools/generate_opus_sample.cpp
    )

    target_link_libraries(tji_speaker_core_sample PRIVATE tji_speaker_core)

    if(MSVC)
        target_compile_options(tji_speaker_core_sample PRIVATE /utf-8)
    endif()
endif()

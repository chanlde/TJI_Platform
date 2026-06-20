cmake_minimum_required(VERSION 3.22.1)

project(tji_speaker_android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(
    ${CMAKE_CURRENT_LIST_DIR}/../../../../native/speaker-core
    ${CMAKE_CURRENT_BINARY_DIR}/speaker-core
)

add_library(tji_speaker_core_jni SHARED
    speaker_core_jni.cpp
)

target_link_libraries(tji_speaker_core_jni
    PRIVATE
        tji_speaker_core
        log
)

target_include_directories(tji_speaker_core_jni
    PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}/../../../../native/speaker-core/include
)

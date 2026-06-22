cmake_minimum_required(VERSION 3.22.1)

project(tji_speaker_core VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(TJI_SPEAKER_CORE_BUILD_TESTS "Build speaker-core CTest executable" ${PROJECT_IS_TOP_LEVEL})
option(TJI_SPEAKER_CORE_BUILD_TOOLS "Build speaker-core command line tools" ${PROJECT_IS_TOP_LEVEL})

add_library(tji_speaker_core
    src/adpcm_codec.cpp
    src/audio_tools.cpp
    src/command_json.cpp
    src/hadp_codec.cpp
    src/mqtt_json.cpp
    src/pcm_utils.cpp
    src/udp_packetizer.cpp
    src/voice_processor.cpp
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

if(CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(tji_speaker_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

if(TJI_SPEAKER_CORE_BUILD_TESTS)
    enable_testing()

    add_executable(tji_speaker_core_tests
        tests/speaker_core_tests.cpp
    )

    target_link_libraries(tji_speaker_core_tests PRIVATE tji_speaker_core)

    add_test(NAME tji_speaker_core_tests COMMAND tji_speaker_core_tests)
    set_tests_properties(tji_speaker_core_tests PROPERTIES
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    )
endif()

if(TJI_SPEAKER_CORE_BUILD_TOOLS)
    add_executable(tji_speaker_core_sample
        tools/generate_hadp_sample.cpp
    )

    target_link_libraries(tji_speaker_core_sample PRIVATE tji_speaker_core)
endif()

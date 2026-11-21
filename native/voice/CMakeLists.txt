cmake_minimum_required(VERSION 3.22.1)
project(jakotata_voice CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Signalsmith Stretch + Linear (MIT), jen hlavičky – third_party/ (zadání kap. 4).
add_library(signalsmith INTERFACE)
target_include_directories(signalsmith INTERFACE
    third_party/signalsmith-stretch/include
    third_party/signalsmith-linear/include)

# Čisté C++ jádro bez závislosti na Androidu – testuje se na hostu v CI (zadání kap. 10).
add_library(voice_core STATIC src/version.cpp src/voice.cpp)
target_include_directories(voice_core PUBLIC include)
target_link_libraries(voice_core PUBLIC signalsmith)
set_target_properties(voice_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(MSVC)
    target_compile_options(voice_core PRIVATE /W4 /bigobj)
else()
    target_compile_options(voice_core PRIVATE -Wall -Wextra -O2)
endif()

if(ANDROID AND NOT JAKOTATA_DEVICE_TESTS)
    find_package(oboe REQUIRED CONFIG)
    add_library(jakotata_voice SHARED src/jni_bridge.cpp src/audio_engine.cpp)
    target_link_libraries(jakotata_voice PRIVATE voice_core oboe::oboe log android)
    target_compile_options(jakotata_voice PRIVATE -Wall -Wextra -O2)
endif()

# Testy: na hostu (CI, GoogleTest přes FetchContent), nebo na zařízení přes NDK
# (-DJAKOTATA_DEVICE_TESTS=ON -DJAKOTATA_GTEST_DIR=<NDK>/sources/third_party/googletest).
option(JAKOTATA_HOST_TESTS "Build GoogleTest unit tests" OFF)
option(JAKOTATA_DEVICE_TESTS "Build GoogleTest unit tests for an Android device" OFF)
if((JAKOTATA_HOST_TESTS AND NOT ANDROID) OR JAKOTATA_DEVICE_TESTS)
    if(JAKOTATA_GTEST_DIR)
        add_library(gtest_local STATIC ${JAKOTATA_GTEST_DIR}/src/gtest-all.cc ${JAKOTATA_GTEST_DIR}/src/gtest_main.cc)
        target_include_directories(gtest_local PUBLIC ${JAKOTATA_GTEST_DIR}/include PRIVATE ${JAKOTATA_GTEST_DIR})
        set(GTEST_TARGET gtest_local)
    else()
        include(FetchContent)
        FetchContent_Declare(
            googletest
            URL https://github.com/google/googletest/archive/refs/tags/v1.15.2.tar.gz
            DOWNLOAD_EXTRACT_TIMESTAMP TRUE
        )
        set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
        FetchContent_MakeAvailable(googletest)
        set(GTEST_TARGET GTest::gtest_main)
    endif()
    enable_testing()
    add_executable(voice_tests tests/version_test.cpp tests/dsp_test.cpp)
    target_link_libraries(voice_tests PRIVATE voice_core ${GTEST_TARGET})
    if(NOT JAKOTATA_DEVICE_TESTS)
        include(GoogleTest)
        gtest_discover_tests(voice_tests)
    endif()
endif()

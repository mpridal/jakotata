cmake_minimum_required(VERSION 3.22.1)
project(jakotata_voice CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Čisté C++ jádro bez závislosti na Androidu – testuje se na hostu v CI (zadání kap. 10).
add_library(voice_core STATIC src/version.cpp)
target_include_directories(voice_core PUBLIC include)
set_target_properties(voice_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(MSVC)
    target_compile_options(voice_core PRIVATE /W4)
else()
    target_compile_options(voice_core PRIVATE -Wall -Wextra -O2)
endif()

if(ANDROID)
    add_library(jakotata_voice SHARED src/jni_bridge.cpp)
    target_link_libraries(jakotata_voice PRIVATE voice_core log)
endif()

option(JAKOTATA_HOST_TESTS "Build GoogleTest unit tests for the host" OFF)
if(JAKOTATA_HOST_TESTS AND NOT ANDROID)
    include(FetchContent)
    FetchContent_Declare(
        googletest
        URL https://github.com/google/googletest/archive/refs/tags/v1.15.2.tar.gz
        DOWNLOAD_EXTRACT_TIMESTAMP TRUE
    )
    set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(googletest)
    enable_testing()
    add_executable(voice_tests tests/version_test.cpp)
    target_link_libraries(voice_tests PRIVATE voice_core GTest::gtest_main)
    include(GoogleTest)
    gtest_discover_tests(voice_tests)
endif()

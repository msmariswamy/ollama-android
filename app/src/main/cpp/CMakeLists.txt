cmake_minimum_required(VERSION 3.22.1)
project("ollamamobile")

set(WHISPER_SRC ${CMAKE_SOURCE_DIR}/whisper.cpp)

# Disable unnecessary backends (CPU-only on Android)
set(GGML_METAL       OFF CACHE BOOL "" FORCE)
set(GGML_CUDA        OFF CACHE BOOL "" FORCE)
set(GGML_BLAS        OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP      OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_SRC} whisper_build)

add_library(whisper-android SHARED whisper_jni.cpp)

target_include_directories(whisper-android PRIVATE
    ${WHISPER_SRC}/include
    ${WHISPER_SRC}/ggml/include)

target_link_libraries(whisper-android whisper android log)
target_compile_options(whisper-android PRIVATE -O3 -std=c++17)
target_link_options(whisper-android PRIVATE "-Wl,-z,max-page-size=16384")
